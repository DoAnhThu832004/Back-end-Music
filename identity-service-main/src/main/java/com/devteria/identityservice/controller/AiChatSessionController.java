package com.devteria.identityservice.controller;

import com.devteria.identityservice.dto.request.ChatSessionCreationRequest;
import com.devteria.identityservice.dto.request.ApiResponse;
import com.devteria.identityservice.dto.response.ChatMessageResponse;
import com.devteria.identityservice.dto.response.ChatSessionResponse;
import com.devteria.identityservice.enums.AiPersona;
import com.devteria.identityservice.service.AiChatSessionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai/chat")
public class AiChatSessionController {

    private final AiChatSessionService aiChatSessionService;

    public AiChatSessionController(AiChatSessionService aiChatSessionService) {
        this.aiChatSessionService = aiChatSessionService;
    }

    @PostMapping("/sessions")
    public ApiResponse<ChatSessionResponse> createSession(@RequestBody(required = false) @Valid ChatSessionCreationRequest request) {
        return ApiResponse.<ChatSessionResponse>builder()
                .result(aiChatSessionService.createSession(request))
                .build();
    }

    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionResponse>> getSessions() {
        return ApiResponse.<List<ChatSessionResponse>>builder()
                .result(aiChatSessionService.getSessions())
                .build();
    }

    @GetMapping("/sessions/active")
    public ApiResponse<ChatSessionResponse> getOrCreateActiveSession(@RequestParam(required = false) AiPersona persona) {
        return ApiResponse.<ChatSessionResponse>builder()
                .result(aiChatSessionService.getOrCreateActiveSession(persona))
                .build();
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessageResponse>> getMessages(@PathVariable String sessionId) {
        return ApiResponse.<List<ChatMessageResponse>>builder()
                .result(aiChatSessionService.getMessages(sessionId))
                .build();
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<String> deleteSession(@PathVariable String sessionId) {
        aiChatSessionService.deleteSession(sessionId);
        return ApiResponse.<String>builder()
                .result("Phiên trò chuyện đã được xóa thành công")
                .build();
    }
}
