package com.devteria.identityservice.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserRolesUpdateRequest {
    @NotEmpty(message = "ROLES_CANNOT_BE_EMPTY")
    List<String> roles;
}
