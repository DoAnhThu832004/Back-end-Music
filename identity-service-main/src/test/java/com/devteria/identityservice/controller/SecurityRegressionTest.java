package com.devteria.identityservice.controller;

import com.devteria.identityservice.dto.request.UserRolesUpdateRequest;
import com.devteria.identityservice.dto.response.RecommendationResponse;
import com.devteria.identityservice.entity.Comment;
import com.devteria.identityservice.entity.User;
import com.devteria.identityservice.exception.AppException;
import com.devteria.identityservice.exception.ErrorCode;
import com.devteria.identityservice.repository.CommentRepository;
import com.devteria.identityservice.repository.UserRepository;
import com.devteria.identityservice.service.CommentService;
import com.devteria.identityservice.service.RecommendationEngineService;
import com.devteria.identityservice.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("/test.properties")
public class SecurityRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private CommentRepository commentRepository;

    @MockBean
    private RecommendationEngineService recommendationEngineService;

    @Autowired
    private CommentService commentService;

    // =========================================================================
    // KỊCH BẢN 1: CHỐNG LEO THANG ĐẶC QUYỀN (PRIVILEGE ESCALATION)
    // =========================================================================

    @Test
    @DisplayName("User thường cố gọi API cập nhật Role -> Bị chặn 403 Forbidden")
    @WithMockUser(username = "regular_user", roles = {"USER"})
    void regularUser_callingUpdateRoles_shouldBeForbidden() throws Exception {
        UserRolesUpdateRequest request = UserRolesUpdateRequest.builder()
                .roles(List.of("ADMIN"))
                .build();

        mockMvc.perform(put("/users/target-user-id/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(userService, never()).updateUserRoles(any(), any());
    }

    @Test
    @DisplayName("Admin gọi API cập nhật Role -> Được phép truy cập")
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    void adminUser_callingUpdateRoles_shouldBeAllowed() throws Exception {
        UserRolesUpdateRequest request = UserRolesUpdateRequest.builder()
                .roles(List.of("ADMIN"))
                .build();

        mockMvc.perform(put("/users/target-user-id/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(userService, times(1)).updateUserRoles(eq("target-user-id"), any());
    }

    // =========================================================================
    // KỊCH BẢN 2: CHỐNG IDOR TRÊN GỢI Ý ÂM NHẠC (RECOMMENDATIONS)
    // =========================================================================

    @Test
    @DisplayName("User A truyền userId của User B để xem trộm gợi ý -> Bị chặn 403 / 1007")
    @WithMockUser(username = "user_a", roles = {"USER"})
    void userA_accessingRecommendationsOfUserB_shouldBeForbidden() throws Exception {
        User userA = User.builder().id("user-a-id").username("user_a").build();
        when(userRepository.findByUsername("user_a")).thenReturn(Optional.of(userA));

        mockMvc.perform(get("/api/recommendations")
                        .param("userId", "user-b-id"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1007));
    }

    @Test
    @DisplayName("User A truy cập gợi ý của chính mình -> Thành công 200 OK")
    @WithMockUser(username = "user_a", roles = {"USER"})
    void userA_accessingOwnRecommendations_shouldBeAllowed() throws Exception {
        User userA = User.builder().id("user-a-id").username("user_a").build();
        when(userRepository.findByUsername("user_a")).thenReturn(Optional.of(userA));
        when(recommendationEngineService.getRecommendations(eq("user-a-id"), anyInt()))
                .thenReturn(new RecommendationResponse("PERSONALIZED", Collections.emptyList()));

        mockMvc.perform(get("/api/recommendations")
                        .param("userId", "user-a-id"))
                .andExpect(status().isOk());

        verify(recommendationEngineService, times(1)).getRecommendations(eq("user-a-id"), anyInt());
    }

    @Test
    @DisplayName("User A gọi gợi ý không truyền userId -> Tự động nhận diện user A và thành công 200 OK")
    @WithMockUser(username = "user_a", roles = {"USER"})
    void userA_accessingRecommendationsWithoutUserId_shouldResolveSelf() throws Exception {
        User userA = User.builder().id("user-a-id").username("user_a").build();
        when(userRepository.findByUsername("user_a")).thenReturn(Optional.of(userA));
        when(recommendationEngineService.getRecommendations(eq("user-a-id"), anyInt()))
                .thenReturn(new RecommendationResponse("PERSONALIZED", Collections.emptyList()));

        mockMvc.perform(get("/api/recommendations"))
                .andExpect(status().isOk());

        verify(recommendationEngineService, times(1)).getRecommendations(eq("user-a-id"), anyInt());
    }

    // =========================================================================
    // KỊCH BẢN 3: CHỐNG IDOR TRÊN XÓA BÌNH LUẬN (COMMENT DELETION)
    // =========================================================================

    @Test
    @DisplayName("User A cố tình xóa bình luận của User B -> Bị ném ngoại lệ UNAUTHORIZED")
    @WithMockUser(username = "user_a", roles = {"USER"})
    void userA_deletingCommentOfUserB_shouldThrowUnauthorized() {
        User userB = User.builder().id("user-b-id").username("user_b").build();
        Comment commentOfB = new Comment();
        commentOfB.setId("comment-1");
        commentOfB.setUser(userB);

        when(commentRepository.findById("comment-1")).thenReturn(Optional.of(commentOfB));

        AppException ex = assertThrows(AppException.class, () -> {
            commentService.deleteComment("comment-1");
        });

        org.assertj.core.api.Assertions.assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Admin xóa bình luận vi phạm của bất kỳ người dùng nào -> Được phép thành công")
    @WithMockUser(username = "admin_user", roles = {"ADMIN"})
    void admin_deletingCommentOfAnyUser_shouldBeAllowed() {
        User userB = User.builder().id("user-b-id").username("user_b").build();
        Comment commentOfB = new Comment();
        commentOfB.setId("comment-1");
        commentOfB.setUser(userB);

        when(commentRepository.findById("comment-1")).thenReturn(Optional.of(commentOfB));

        commentService.deleteComment("comment-1");

        verify(commentRepository, times(1)).deleteById("comment-1");
    }
}