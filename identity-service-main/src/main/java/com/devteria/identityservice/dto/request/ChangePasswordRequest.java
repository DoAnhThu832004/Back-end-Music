package com.devteria.identityservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangePasswordRequest {

    @NotBlank(message = "Mật khẩu cũ không được để trống")
    String oldPassword;

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 8, message = "INVALID_PASSWORD")
    String newPassword;

    @NotBlank(message = "Xác nhận mật khẩu không được để trống")
    String confirmPassword;
}
