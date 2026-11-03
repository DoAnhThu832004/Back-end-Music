package com.devteria.identityservice.controller;

import java.io.IOException;
import java.util.List;

import com.devteria.identityservice.dto.response.PageResponse;
import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import com.devteria.identityservice.dto.request.UserRolesUpdateRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.devteria.identityservice.dto.request.ApiResponse;
import com.devteria.identityservice.dto.request.ChangePasswordRequest;
import com.devteria.identityservice.dto.request.UserCreationRequest;
import com.devteria.identityservice.dto.request.UserUpdateRequest;
import com.devteria.identityservice.dto.response.UserResponse;
import com.devteria.identityservice.service.UserService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class UserController {
    UserService userService;
    @Transactional
    @PostMapping
    ApiResponse<UserResponse> createUser(@RequestBody @Valid UserCreationRequest request) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.createUser(request))
                .build();
    }

    @GetMapping
    ApiResponse<PageResponse<UserResponse>> getUsers(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size
    ) {
        return ApiResponse.<PageResponse<UserResponse>>builder()
                .result(userService.getUsers(page, size))
                .build();
    }

    @GetMapping("/{userId}")
    ApiResponse<UserResponse> getUser(@PathVariable("userId") String userId) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.getUser(userId))
                .build();
    }

    @GetMapping("/my-info")
    ApiResponse<UserResponse> getMyInfo() {
        return ApiResponse.<UserResponse>builder()
                .result(userService.getMyInfo())
                .build();
    }

    @DeleteMapping("/{userId}")
    ApiResponse<String> deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
        return ApiResponse.<String>builder().result("User has been deleted").build();
    }
    @Transactional
    @PutMapping("/{userId}")
    ApiResponse<UserResponse> updateUser(@PathVariable String userId, @RequestBody UserUpdateRequest request) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.updateUser(userId, request))
                .message("this is update user")
                .build();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{userId}/roles")
    ApiResponse<UserResponse> updateUserRoles(
            @PathVariable String userId,
            @RequestBody @Valid UserRolesUpdateRequest request
    ) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.updateUserRoles(userId, request))
                .message("User roles updated successfully")
                .build();
    }
    @Transactional
    @PostMapping(value = "/{userId}/upload")
    ApiResponse<UserResponse> uploadProfileImage(
            @PathVariable("userId") String userId,
            @RequestParam("image") MultipartFile image
    ) throws IOException {
        return ApiResponse.<UserResponse>builder()
                .result(userService.uploadProfileImage(userId, image))
                .message("Profile image uploaded successfully")
                .build();
    }

    @Transactional
    @PutMapping("/change-password")
    ApiResponse<String> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        userService.changePassword(request);
        return ApiResponse.<String>builder()
                .result("Password changed successfully")
                .build();
    }

    /**
     * [ADMIN] ChĂ¡ÂºÂ·n tÄ‚Â i khoĂ¡ÂºÂ£n ngĂ†Â°Ă¡Â»Âi dÄ‚Â¹ng.
     * PATCH /users/{userId}/block
     */
    @PatchMapping("/{userId}/block")
    ApiResponse<UserResponse> blockUser(@PathVariable String userId) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.blockUser(userId))
                .message("User has been blocked successfully")
                .build();
    }

    /**
     * [ADMIN] HuĂ¡Â»Â· chĂ¡ÂºÂ·n tÄ‚Â i khoĂ¡ÂºÂ£n ngĂ†Â°Ă¡Â»Âi dÄ‚Â¹ng.
     * PATCH /users/{userId}/unblock
     */
    @PatchMapping("/{userId}/unblock")
    ApiResponse<UserResponse> unblockUser(@PathVariable String userId) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.unblockUser(userId))
                .message("User has been unblocked successfully")
                .build();
    }
}
