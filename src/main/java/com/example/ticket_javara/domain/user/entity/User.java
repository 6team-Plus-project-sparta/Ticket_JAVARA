package com.example.ticket_javara.domain.user.entity;

import com.example.ticket_javara.global.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * USER 테이블 엔티티
 * ERD v7.0: user_id, email(UK), password, nickname, role(ENUM), created_at, updated_at
 * ⚠️ @Setter 사용 금지 — 비즈니스 메서드로 상태 변경
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UserRole role;

    @Builder
    public User(String email, String password, String nickname, UserRole role) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.role = role;
    }

    // ── 비즈니스 메서드 ──

    /** 닉네임 변경 */
    public void updateNickname(String newNickname) {
        this.nickname = newNickname;
    }

    /** 비밀번호 변경 (암호화된 값을 전달받아야 함) */
    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    /** 관리자 여부 */
    public boolean isAdmin() {
        return UserRole.ADMIN.equals(this.role);
    }
}
