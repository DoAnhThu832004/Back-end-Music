package com.devteria.identityservice.controller;

import com.devteria.identityservice.dto.request.ApiResponse;
import com.devteria.identityservice.dto.request.CommentCreationRequest;
import com.devteria.identityservice.dto.request.CommentUpdateRequest;
import com.devteria.identityservice.dto.response.CommentResponse;
import com.devteria.identityservice.dto.response.PageResponse;
import com.devteria.identityservice.service.CommentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/comments")
public class CommentController {

    private final CommentService commentService;

    // Sử dụng Constructor Injection để đảm bảo tính bất biến của các phụ thuộc
    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }
    @PostMapping("/song/{songId}/comments")
    public ApiResponse<CommentResponse> createComment(
            @PathVariable String songId,
            @RequestBody @Valid CommentCreationRequest request
    ) {
        return ApiResponse.<CommentResponse>builder()
                .result(commentService.createComment(songId, request))
                .build();
    }
    @GetMapping("/song/{songId}")
    public ApiResponse<PageResponse<CommentResponse>> getCommentsBySong(
            @PathVariable("songId") String songId,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size
    ) {
        return ApiResponse.<PageResponse<CommentResponse>>builder()
                .result(commentService.getCommentsBySong(songId, page, size))
                .build();
    }
    @PutMapping("/{commentId}")
    public ApiResponse<CommentResponse> updateComment(
            @PathVariable("commentId") String commentId,
            @RequestBody @Valid CommentUpdateRequest request) {
        return ApiResponse.<CommentResponse>builder()
                .result(commentService.updateComment(commentId, request))
                .build();
    }
    @DeleteMapping("/{commentId}")
    public ApiResponse<String> deleteComment(@PathVariable("commentId") String commentId) {
        commentService.deleteComment(commentId);
        return ApiResponse.<String>builder()
                .result("Comment has been deleted")
                .build();
    }
}
