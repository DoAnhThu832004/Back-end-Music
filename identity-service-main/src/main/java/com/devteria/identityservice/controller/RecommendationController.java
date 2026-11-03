package com.devteria.identityservice.controller;

import com.devteria.identityservice.dto.request.ApiResponse;
import com.devteria.identityservice.dto.response.HomeRecommendationResponse;
import com.devteria.identityservice.dto.response.RecommendationResponse;
import com.devteria.identityservice.entity.User;
import com.devteria.identityservice.exception.AppException;
import com.devteria.identityservice.exception.ErrorCode;
import com.devteria.identityservice.repository.UserRepository;
import com.devteria.identityservice.service.RecommendationEngineService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationEngineService recommendationEngineService;
    private final UserRepository userRepository;

    public RecommendationController(
            RecommendationEngineService recommendationEngineService,
            UserRepository userRepository) {
        this.recommendationEngineService = recommendationEngineService;
        this.userRepository = userRepository;
    }

    private String resolveAuthorizedUserId(String requestedUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String currentUsername = authentication.getName();
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        // Nếu client không truyền hoặc truyền rỗng, lấy userId của chính người đăng nhập
        if (requestedUserId == null || requestedUserId.isBlank()) {
            return currentUser.getId();
        }

        // Nếu truyền userId khác mà không phải là ADMIN -> Chặn IDOR!
        if (!isAdmin && !requestedUserId.equals(currentUser.getId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        return requestedUserId;
    }

    @GetMapping
    public ApiResponse<RecommendationResponse> getRecommendations(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {

        String authorizedUserId = resolveAuthorizedUserId(userId);
        RecommendationResponse result = recommendationEngineService.getRecommendations(authorizedUserId, limit);

        return ApiResponse.<RecommendationResponse>builder()
                .code(1000)
                .message("Lấy danh sách gợi ý thành công. Nguồn: " + result.getSource())
                .result(result)
                .build();
    }

    @GetMapping("/home")
    public ApiResponse<HomeRecommendationResponse> getHomeRecommendations(
            @RequestParam(value = "userId", required = false) String userId) {

        String authorizedUserId = resolveAuthorizedUserId(userId);
        HomeRecommendationResponse result = recommendationEngineService.getHomeRecommendations(authorizedUserId);

        return ApiResponse.<HomeRecommendationResponse>builder()
                .code(1000)
                .message("Lấy dữ liệu trang chủ thành công. Nguồn: " + result.getSource())
                .result(result)
                .build();
    }
}