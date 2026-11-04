package com.devteria.identityservice.service;

import com.devteria.identityservice.exception.AppException;
import com.devteria.identityservice.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class AiChatGuardService {

    public static final int MAX_REQUESTS_PER_MINUTE = 5;
    public static final int MAX_REQUESTS_PER_DAY = 50;
    private static final long ONE_MINUTE_MS = 60_000L;

    // Concurrency Lock: 1 stream per sessionId tại một thời điểm
    private final Map<String, Boolean> activeSessionStreams = new ConcurrentHashMap<>();

    // Rate Limiter per User: Sliding Window (1 phút)
    private final Map<String, Queue<Long>> userMinuteTimestamps = new ConcurrentHashMap<>();

    // Quota per User: Hạn mức ngày
    private final Map<String, UserDailyQuota> userDailyQuotas = new ConcurrentHashMap<>();

    public static class UserDailyQuota {
        private final LocalDate date;
        private int count;

        public UserDailyQuota(LocalDate date, int count) {
            this.date = date;
            this.count = count;
        }

        public LocalDate getDate() { return date; }
        public int getCount() { return count; }
        public void increment() { this.count++; }
    }

    /**
     * Cố gắng lấy khóa cho phiên chat (Concurrency Session Lock).
     * @param sessionId ID phiên trò chuyện
     * @return true nếu lấy khóa thành công, false nếu phiên đang có stream chạy dở
     */
    public boolean tryAcquireSessionLock(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return false;
        return activeSessionStreams.putIfAbsent(sessionId, Boolean.TRUE) == null;
    }

    /**
     * Kiểm tra và lấy khóa cho phiên chat. Ném ngoại lệ AI_SESSION_BUSY (409 Conflict) nếu đang bận.
     */
    public void checkAndLockSession(String sessionId) {
        if (!tryAcquireSessionLock(sessionId)) {
            log.warn("[AI Guard] Phiên chat {} đang có luồng stream AI chạy dở (Conflict).", sessionId);
            throw new AppException(ErrorCode.AI_SESSION_BUSY);
        }
    }

    /**
     * Giải phóng khóa cho phiên chat khi stream kết thúc, lỗi hoặc ngắt kết nối.
     */
    public void releaseSessionLock(String sessionId) {
        if (sessionId != null) {
            activeSessionStreams.remove(sessionId);
            log.debug("[AI Guard] Đã giải phóng khóa cho session {}", sessionId);
        }
    }

    public boolean isSessionLocked(String sessionId) {
        return sessionId != null && activeSessionStreams.containsKey(sessionId);
    }

    /**
     * Kiểm tra và ghi nhận lượt gửi tin nhắn của User:
     * - Tối đa 5 tin nhắn / phút (Sliding window)
     * - Tối đa 50 tin nhắn / ngày
     * Ném AppException(ErrorCode.AI_RATE_LIMIT_EXCEEDED) nếu vượt ngưỡng.
     */
    public synchronized void validateAndRecordUserRequest(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        long now = System.currentTimeMillis();
        LocalDate today = LocalDate.now();

        // 1. Kiểm tra giới hạn 5 tin nhắn / phút (Sliding Window)
        Queue<Long> timestamps = userMinuteTimestamps.computeIfAbsent(userId, k -> new ArrayDeque<>());
        long windowStart = now - ONE_MINUTE_MS;
        while (!timestamps.isEmpty() && timestamps.peek() < windowStart) {
            timestamps.poll();
        }

        if (timestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
            log.warn("[AI Guard] User {} vượt quá giới hạn {} tin nhắn/phút.", userId, MAX_REQUESTS_PER_MINUTE);
            throw new AppException(ErrorCode.AI_RATE_LIMIT_EXCEEDED);
        }

        // 2. Kiểm tra hạn mức 50 tin nhắn / ngày
        UserDailyQuota quota = userDailyQuotas.get(userId);
        if (quota != null && quota.getDate().equals(today)) {
            if (quota.getCount() >= MAX_REQUESTS_PER_DAY) {
                log.warn("[AI Guard] User {} đã dùng hết hạn mức {} tin nhắn/ngày.", userId, MAX_REQUESTS_PER_DAY);
                throw new AppException(ErrorCode.AI_RATE_LIMIT_EXCEEDED);
            }
        }

        // 3. Ghi nhận lượt yêu cầu hợp lệ
        timestamps.offer(now);

        if (quota == null || !quota.getDate().equals(today)) {
            userDailyQuotas.put(userId, new UserDailyQuota(today, 1));
        } else {
            quota.increment();
        }
    }

    public synchronized int getRemainingDailyQuota(String userId) {
        if (userId == null) return 0;
        UserDailyQuota quota = userDailyQuotas.get(userId);
        if (quota == null || !quota.getDate().equals(LocalDate.now())) {
            return MAX_REQUESTS_PER_DAY;
        }
        return Math.max(0, MAX_REQUESTS_PER_DAY - quota.getCount());
    }

    public synchronized void clearUserMinuteLimit(String userId) {
        if (userId != null) {
            userMinuteTimestamps.remove(userId);
        }
    }

    public synchronized void resetLimitsForUser(String userId) {
        if (userId != null) {
            userMinuteTimestamps.remove(userId);
            userDailyQuotas.remove(userId);
        }
    }
}
