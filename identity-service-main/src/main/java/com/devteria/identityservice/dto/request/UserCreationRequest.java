package com.devteria.identityservice.dto.request;

import java.time.LocalDate;
import java.util.Set;

import jakarta.validation.constraints.Size;

import com.devteria.identityservice.validator.DobConstraint;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserCreationRequest {
    @Size(min = 4, message = "USERNAME_INVALID")
    String username;

    @Size(min = 6, message = "INVALID_PASSWORD")
    String password;

    String firstName;
    String lastName;

    @DobConstraint(min = 10, message = "INVALID_DOB")
    LocalDate dob;

    /**
     * Danh sách ID các thể loại nhạc yêu thích, được chọn bửi user lần đầu khi đăng ký.
     * Dùng cho luồng Cold Start: nếu user chưa có lịch sử nghe, hệ thống sẽ gợi ý
     * các bài hát Trending thuộc những thể loại này.
     */
    Set<String> preferredGenreIds;
}
