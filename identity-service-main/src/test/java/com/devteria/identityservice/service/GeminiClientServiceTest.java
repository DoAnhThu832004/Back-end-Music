package com.devteria.identityservice.service;

import com.devteria.identityservice.enums.MessageStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiClientServiceTest {

    private GeminiClientService geminiClientService;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;

    // Sample DTO for testing structured outputs
    public record TestResponseDto(String message, List<String> tags) {}

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        RestClient.Builder restClientBuilder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com/v1beta");
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();

        geminiClientService = new GeminiClientService(restClient, objectMapper);
        ReflectionTestUtils.setField(geminiClientService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(geminiClientService, "model", "gemini-3.5-flash");
    }

    @Test
    @DisplayName("Gemini trả về JSON hợp lệ -> Parse thành công DTO và log token")
    void generateStructuredOutput_validResponse_success() {
        String mockGeminiResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{\\"message\\": \\"Hello Music\\", \\"tags\\": [\\"Pop\\", \\"Chill\\"]}"
                      }
                    ]
                  }
                }
              ],
              "usageMetadata": {
                "totalTokenCount": 85
              }
            }
            """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-api-key"))
                .andRespond(withSuccess(mockGeminiResponse, MediaType.APPLICATION_JSON));

        Optional<TestResponseDto> result = geminiClientService.generateStructuredOutput(
                "Phân tích", "Chỉ dẫn hệ thống", TestResponseDto.class);

        mockServer.verify();
        assertThat(result).isPresent();
        assertThat(result.get().message()).isEqualTo("Hello Music");
        assertThat(result.get().tags()).containsExactly("Pop", "Chill");
    }

    @Test
    @DisplayName("Gemini bị lỗi 429 Too Many Requests -> Fail-Open an toàn, trả về Optional.empty()")
    void generateStructuredOutput_rateLimit429_returnsEmpty() {
        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("{\"error\": \"Quota exceeded\"}"));

        Optional<TestResponseDto> result = geminiClientService.generateStructuredOutput(
                "Phân tích", "Chỉ dẫn hệ thống", TestResponseDto.class);

        mockServer.verify();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Gemini trả về JSON bị cắt cụt (Malformed) -> Fail-Open an toàn, không ném exception")
    void generateStructuredOutput_malformedJson_returnsEmpty() {
        String malformedGeminiResponse = """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      {
                        "text": "{\\"message\\": \\"Unclosed JSON"
                      }
                    ]
                  }
                }
              ]
            }
            """;

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-api-key"))
                .andRespond(withSuccess(malformedGeminiResponse, MediaType.APPLICATION_JSON));

        Optional<TestResponseDto> result = geminiClientService.generateStructuredOutput(
                "Phân tích", "Chỉ dẫn", TestResponseDto.class);

        mockServer.verify();
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("streamGenerateContent thành công -> stream chunks và gọi callback với COMPLETE")
    void streamGenerateContent_success() throws Exception {
        String ssePayload = """
data: {"candidates": [{"content": {"parts": [{"text": "Giai điệu "}]}}]}

data: {"candidates": [{"content": {"parts": [{"text": "rất chill."}]}}], "usageMetadata": {"totalTokenCount": 45}}

""";

        mockServer.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:streamGenerateContent?alt=sse"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-api-key"))
                .andRespond(withSuccess(ssePayload, MediaType.TEXT_EVENT_STREAM));

        SseEmitter emitter = new SseEmitter(90_000L);
        CompletableFuture<GeminiClientService.StreamResult> future = new CompletableFuture<>();

        geminiClientService.streamGenerateContent(
                List.of(Map.of("role", "user", "parts", List.of(Map.of("text", "Xin chào")))),
                "Bạn là DJ",
                emitter,
                future::complete
        );

        GeminiClientService.StreamResult result = future.get(5, TimeUnit.SECONDS);

        mockServer.verify();
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(MessageStatus.COMPLETE);
        assertThat(result.fullContent()).isEqualTo("Giai điệu rất chill.");
        assertThat(result.totalTokens()).isEqualTo(45);
    }

    @Test
    @DisplayName("streamGenerateContent bị từ chối khi Semaphore hết permit -> trả về status FAILED")
    void streamGenerateContent_semaphoreBusy_fails() throws Exception {
        Semaphore emptySemaphore = new Semaphore(0);
        ReflectionTestUtils.setField(geminiClientService, "aiStreamingSemaphore", emptySemaphore);

        SseEmitter emitter = new SseEmitter(90_000L);
        CompletableFuture<GeminiClientService.StreamResult> future = new CompletableFuture<>();

        geminiClientService.streamGenerateContent(
                List.of(Map.of("role", "user", "parts", List.of(Map.of("text", "Xin chào")))),
                "Bạn là DJ",
                emitter,
                future::complete
        );

        GeminiClientService.StreamResult result = future.get(6, TimeUnit.SECONDS);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(MessageStatus.FAILED);
        assertThat(result.errorMessage()).contains("quá tải");
    }
}
