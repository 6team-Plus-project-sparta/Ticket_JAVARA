package com.example.ticket_javara.domain.user.service;

import com.example.ticket_javara.domain.user.dto.request.UserUpdateRequest;
import com.example.ticket_javara.domain.user.dto.response.UserResponse;
import com.example.ticket_javara.domain.user.entity.User;
import com.example.ticket_javara.domain.user.repository.UserRepository;
import com.example.ticket_javara.global.exception.BusinessException;
import com.example.ticket_javara.global.exception.ConflictException;
import com.example.ticket_javara.global.exception.ErrorCode;
import com.example.ticket_javara.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /** 내 정보 조회 (FN-AUTH-03) */
    public UserResponse getMyInfo(Long userId) {
        User user = getUser(userId);
        return UserResponse.from(user);
    }

    /** 내 정보 수정 (FN-AUTH-04) */
    @Transactional
    public void updateMyInfo(Long userId, UserUpdateRequest request) {
        User user = getUser(userId);

        // 닉네임 변경
        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            if (!user.getNickname().equals(request.getNickname()) && 
                userRepository.existsByNickname(request.getNickname())) {
                throw new ConflictException(ErrorCode.NICKNAME_DUPLICATED);
            }
            user.updateNickname(request.getNickname());
        }

        // 비밀번호 변경
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            // 현재 비밀번호 검증
            if (request.getCurrentPassword() == null || !passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
                throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
            }
            String encodedNewPassword = passwordEncoder.encode(request.getPassword());
            user.updatePassword(encodedNewPassword);
        }
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND));
    }
}
