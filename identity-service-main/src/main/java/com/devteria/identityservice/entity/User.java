package com.devteria.identityservice.entity;

import java.time.LocalDate;
import java.util.Set;

import jakarta.persistence.*;
import com.devteria.identityservice.entity.Genre;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class User extends AbstractAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @Column(name = "username", unique = true, columnDefinition = "VARCHAR(255) COLLATE utf8mb4_unicode_ci") // unique : không cho người dùng cùng tên,
            // utf8mb4 -> hỗ trợ ngôn ngữ
            // unicode_ci -> không phân biệt chữ hoa, chữ thường
    String username;

    String password;
    String firstName;
    LocalDate dob;
    String lastName;
    String imageUrl;

    @ManyToMany
    Set<Role> roles;

    /**
     * Trạng thái tài khoản: true = bị chặn, false = bình thường (mặc định).
     * Admin có thể thay đổi trạng thái này để chặn hoặc mở khoá người dùng.
     */
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    boolean blocked;

    /**
     * Thể loại nhạc yêu thích của user — được chọn lần đầu khi đăng ký.
     * Dùng cho luồng Cold Start trong hệ thống gợi ý nhạc cá nhân hóa.
     */
    @ManyToMany
    @JoinTable(
            name = "user_preferred_genres",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "genre_id")
    )
    Set<Genre> preferredGenres;
}
