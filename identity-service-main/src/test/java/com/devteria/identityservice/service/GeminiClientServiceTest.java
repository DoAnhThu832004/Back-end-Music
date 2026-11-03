package com.devteria.identityservice.service;

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

import java.util.List;
import java.util.Optional;

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
                .andRespond(withSuccess(malformedGeminiResponse, MediaType.APPLICATION_JSON));

        Optional<TestResponseDto> result = geminiClientService.generateStructuredOutput(
                "Phân tích", "Chỉ dẫn", TestResponseDto.class);

        mockServer.verify();
        assertThat(result).isEmpty();
    }
}