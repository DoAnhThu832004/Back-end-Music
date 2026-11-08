package com.devteria.identityservice.controller;

import com.devteria.identityservice.dto.request.ApiResponse;
import com.devteria.identityservice.service.InteractionService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/interactions")
public class InteractionController {

    private final InteractionService interactionService;

    public InteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @PostMapping("/listen/{songId}")
    public ApiResponse<Void> listenSong(@PathVariable("songId") String songId) {
        interactionService.recordListenInteraction(songId);
        return ApiResponse.<Void>builder()
                .code(200)
                .message("Recorded listen interaction")
                .build();
    }
}
