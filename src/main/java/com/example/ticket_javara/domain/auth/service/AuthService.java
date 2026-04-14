package com.example.ticket_javara.domain.auth.service;

import com.example.ticket_javara.domain.auth.dto.request.LoginRequest;
import com.example.ticket_javara.domain.auth.dto.request.SignupRequest;
import com.example.ticket_javara.domain.auth.dto.response.LoginResponse;
import com.example.ticket_javara.domain.auth.dto.response.SignupResponse;
import com.example.ticket_javara.domain.user.entity.User;
import com.example.ticket_javara.domain.user.entity.UserRole;
import com.example.ticket_javara.domain.user.repository.UserRepository;
import com.example.ticket_javara.global.exception.ConflictException;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.NotFoundException;
import com.example.ticket_javara.global.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 서비스 (FN-AUTH-01, FN-AUTH-02)
 * - 회원가입: 이메일 중복 검사 → BCrypt 암호화 → User 저장
 * - 로그인: 이메일 조회 → 비밀번호 검증 → JWT 발급
 * 참고: 03_유스케이스_명세서.md UC-001, UC-002
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * 회원가입 (FN-AUTH-01)
     * - 이메일 중복: 409 EMAIL_DUPLICATED
     */
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        // 이메일 중복 검사
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException(ErrorCode.DUPLICATE_EMAIL);
        }

        // 비밀번호 BCrypt 암호화
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // User 저장 (기본 role = USER)
        User user = User.builder()
                .email(request.getEmail())
                .password(encodedPassword)
                .nickname(request.getNickname())
                .role(UserRole.USER)
                .build();
        userRepository.save(user);

        return SignupResponse.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * 로그인 (FN-AUTH-02)
     * - 이메일 없음 / 비밀번호 불일치: 동일 메시지로 401 반환 (보안 원칙)
     * - 성공: JWT Access Token 발급
     */
    public LoginResponse login(LoginRequest request) {
        // 이메일로 사용자 조회 (없으면 인증 실패 동일 메시지)
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new NotFoundException(ErrorCode.INVALID_CREDENTIALS));

        // 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new NotFoundException(ErrorCode.INVALID_CREDENTIALS);
        }

        // JWT Access Token 발급
        String accessToken = jwtUtil.createAccessToken(user.getUserId(), user.getEmail(), user.getRole().name());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .expiresIn(3600)
                .tokenType("Bearer")
                .build();
    }
}
