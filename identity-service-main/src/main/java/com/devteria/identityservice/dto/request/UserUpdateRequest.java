package com.devteria.identityservice.dto.request;

import java.time.LocalDate;
import java.util.Set;

import com.devteria.identityservice.validator.DobConstraint;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserUpdateRequest {
    String password;
    String firstName;
    String lastName;

    @DobConstraint(min = 18, message = "INVALID_DOB")
    LocalDate dob;

    /**
     * Danh sách ID thể loại nhạc yêu thích — có thể cập nhật sau khi đăng ký.
     */
    Set<String> preferredGenreIds;
}
