package com.devteria.identityservice.service;

import com.devteria.identityservice.exception.AppException;
import com.devteria.identityservice.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiChatGuardServiceTest {

    private AiChatGuardService guardService;

    @BeforeEach
    void setUp() {
        guardService = new AiChatGuardService();
    }

    @Test
    @DisplayName("Lấy khóa session thành công khi chưa bị khóa")
    void tryAcquireSessionLock_Success_WhenNotLocked() {
        boolean acquired = guardService.tryAcquireSessionLock("session-1");
        assertThat(acquired).isTrue();
        assertThat(guardService.isSessionLocked("session-1")).isTrue();
    }

    @Test
    @DisplayName("Lấy khóa thất bại khi session đang có luồng stream khác")
    void tryAcquireSessionLock_Fails_WhenAlreadyLocked() {
        guardService.tryAcquireSessionLock("session-1");
        boolean secondAttempt = guardService.tryAcquireSessionLock("session-1");
        assertThat(secondAttempt).isFalse();
    }

    @Test
    @DisplayName("Giải phóng khóa cho phép luồng tiếp theo lấy khóa thành công")
    void releaseSessionLock_AllowsSubsequentAcquire() {
        guardService.tryAcquireSessionLock("session-1");
        guardService.releaseSessionLock("session-1");

        assertThat(guardService.isSessionLocked("session-1")).isFalse();
        boolean reacquired = guardService.tryAcquireSessionLock("session-1");
        assertThat(reacquired).isTrue();
    }

    @Test
    @DisplayName("checkAndLockSession thành công khi chưa bị khóa")
    void checkAndLockSession_Success() {
        guardService.checkAndLockSession("session-1");
        assertThat(guardService.isSessionLocked("session-1")).isTrue();
    }

    @Test
    @DisplayName("checkAndLockSession ném AI_SESSION_BUSY (409 Conflict) khi session đang bận")
    void checkAndLockSession_ThrowsAiSessionBusy() {
        guardService.checkAndLockSession("session-1");

        assertThatThrownBy(() -> guardService.checkAndLockSession("session-1"))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_SESSION_BUSY);
    }

    @Test
    @DisplayName("User gửi dưới 5 tin nhắn/phút thành công")
    void validateAndRecordUserRequest_Success_UnderMinuteLimit() {
        String userId = "user-123";
        for (int i = 0; i < 5; i++) {
            guardService.validateAndRecordUserRequest(userId);
        }
        assertThat(guardService.getRemainingDailyQuota(userId)).isEqualTo(45);
    }

    @Test
    @DisplayName("Ném AI_RATE_LIMIT_EXCEEDED khi gửi tin nhắn thứ 6 trong 1 phút")
    void validateAndRecordUserRequest_ThrowsException_WhenExceedsMinuteLimit() {
        String userId = "user-123";
        for (int i = 0; i < 5; i++) {
            guardService.validateAndRecordUserRequest(userId);
        }

        assertThatThrownBy(() -> guardService.validateAndRecordUserRequest(userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("Ném AI_RATE_LIMIT_EXCEEDED khi vượt quá 50 tin nhắn trong ngày")
    void validateAndRecordUserRequest_ThrowsException_WhenExceedsDailyQuota() {
        String userId = "user-power";

        // Giả lập dùng hết 50 lượt (mỗi block 5 tin nhắn, reset window phút)
        for (int batch = 0; batch < 10; batch++) {
            for (int i = 0; i < 5; i++) {
                guardService.validateAndRecordUserRequest(userId);
            }
            // Xóa bộ nhớ phút để mô phỏng thời gian trôi qua, nhưng giữ hạn mức ngày
            guardService.clearUserMinuteLimit(userId);
        }

        assertThat(guardService.getRemainingDailyQuota(userId)).isEqualTo(0);

        // Tin nhắn thứ 51 bị từ chối
        assertThatThrownBy(() -> guardService.validateAndRecordUserRequest(userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("resetLimitsForUser xóa sạch giới hạn của user")
    void resetLimitsForUser_ClearsAllLimits() {
        String userId = "user-reset";
        for (int i = 0; i < 5; i++) {
            guardService.validateAndRecordUserRequest(userId);
        }

        guardService.resetLimitsForUser(userId);
        assertThat(guardService.getRemainingDailyQuota(userId)).isEqualTo(50);

        // Có thể gửi request mới mà không bị chặn
        guardService.validateAndRecordUserRequest(userId);
        assertThat(guardService.getRemainingDailyQuota(userId)).isEqualTo(49);
    }

    @Test
    @DisplayName("Ném UNAUTHENTICATED khi userId rỗng hoặc null")
    void validateAndRecordUserRequest_ThrowsUnauthenticated_WhenUserIdNull() {
        assertThatThrownBy(() -> guardService.validateAndRecordUserRequest(null))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);

        assertThatThrownBy(() -> guardService.validateAndRecordUserRequest(""))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);
    }
}
