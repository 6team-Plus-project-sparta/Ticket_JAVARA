package com.example.ticket_javara.domain.user.controller;

import com.example.ticket_javara.domain.user.dto.request.UserUpdateRequest;
import com.example.ticket_javara.domain.user.dto.response.UserResponse;
import com.example.ticket_javara.domain.user.service.UserService;
import com.example.ticket_javara.global.common.ApiResponse;
import com.example.ticket_javara.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 내 정보 조회 (FN-AUTH-03) */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserResponse response = userService.getMyInfo(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 내 정보 수정 (FN-AUTH-04) */
    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<Void>> updateMyInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody UserUpdateRequest request) {
        userService.updateMyInfo(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
