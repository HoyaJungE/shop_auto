package com.harness.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 사용자 엔티티
 *
 * id / createdAt / updatedAt 은 BaseEntity에서 상속.
 * 비밀번호는 BCryptPasswordEncoder로 해시 저장한다.
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true)
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 200)
    private String email;

    @Column(nullable = false, length = 200)
    private String password;                   // BCrypt 해시

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // ── 생성 ────────────────────────────────────────────────────

    public static User create(String email, String encodedPassword) {
        User user = new User();
        user.email    = email;
        user.password = encodedPassword;
        user.role     = Role.USER;
        return user;
    }

    // ── 역할 ────────────────────────────────────────────────────

    public enum Role {
        USER, ADMIN
    }
}
