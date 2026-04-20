package com.example.ticket_javara.domain.chat.controller;

import com.example.ticket_javara.domain.chat.dto.AdminChatRoomResponse;
import com.example.ticket_javara.domain.chat.dto.ChatHistoryResponse;
import com.example.ticket_javara.domain.chat.dto.ChatRoomResponse;
import com.example.ticket_javara.domain.chat.dto.UpdateChatRoomStatusRequest;
import com.example.ticket_javara.domain.chat.service.ChatRoomService;
import com.example.ticket_javara.global.common.ApiResponse;
import com.example.ticket_javara.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    /** 고객: CS 문의 채팅방 생성 또는 기존 WAITING 방 반환 */
    @PostMapping("/chat/rooms")
    public ResponseEntity<ApiResponse<ChatRoomResponse>> createOrGetRoom(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        ChatRoomResponse response = chatRoomService.createOrGetRoom(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 채팅 이력 조회
     * - cursor: 이전 메시지 페이징 (일반 조회)
     * - afterId: 해당 ID 이후 메시지 반환 (재연결 시 누락 메시지 복구)
     */
    @GetMapping("/chat/rooms/{chatRoomId}/messages")
    public ResponseEntity<ApiResponse<ChatHistoryResponse>> getChatHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long chatRoomId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Long afterId,
            @RequestParam(defaultValue = "50") int size) {

        ChatHistoryResponse response = chatRoomService.getChatHistory(
                chatRoomId, cursor, afterId, size,
                userDetails.getUserId(), userDetails.getRole());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 관리자: 채팅방 상태 전이
     * WAITING → IN_PROGRESS → COMPLETED (역방향 불가)
     *
     * ★ 항목 1 개선: Service가 DTO를 반환하므로 Controller는 HTTP 통신만 처리
     * ★ 항목 3 개선: Map<String,String> 제거 → @Valid DTO로 입력값 강제 및 NPE 방지
     */
    @PatchMapping("/admin/chat/rooms/{chatRoomId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ChatRoomResponse>> updateRoomStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long chatRoomId,
            @Valid @RequestBody UpdateChatRoomStatusRequest request) {

        ChatRoomResponse response = chatRoomService.updateRoomStatus(
                chatRoomId, request.getStatus(), userDetails.getUserId(), userDetails.getRole());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /** 관리자: 전체 CS 문의 채팅방 목록 조회 (상태별 필터링) */
    @GetMapping("/admin/chat/rooms")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<AdminChatRoomResponse>>> getAdminChatRooms(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String status,
            Pageable pageable) {

        Page<AdminChatRoomResponse> response = chatRoomService.getAdminChatRooms(
                status, pageable, userDetails.getRole());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
