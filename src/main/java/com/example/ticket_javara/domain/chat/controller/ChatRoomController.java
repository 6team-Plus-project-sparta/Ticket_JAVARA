package com.example.ticket_javara.domain.chat.controller;

import com.example.ticket_javara.domain.chat.dto.AdminChatRoomResponse;
import com.example.ticket_javara.domain.chat.dto.ChatHistoryResponse;
import com.example.ticket_javara.domain.chat.dto.ChatRoomResponse;
import com.example.ticket_javara.domain.chat.service.ChatRoomService;
import com.example.ticket_javara.global.common.ApiResponse;
import com.example.ticket_javara.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.example.ticket_javara.domain.chat.dto.ChatRoomCloseResponse;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    @PostMapping("/chat/rooms")
    public ResponseEntity<ApiResponse<ChatRoomResponse>> createOrGetRoom(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        ChatRoomResponse response = chatRoomService.createOrGetRoom(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/chat/rooms/{chatRoomId}/messages")
    public ResponseEntity<ApiResponse<ChatHistoryResponse>> getChatHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long chatRoomId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "50") int size) {
        
        ChatHistoryResponse response = chatRoomService.getChatHistory(
                chatRoomId, cursor, size, userDetails.getUserId(), userDetails.getRole());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PatchMapping("/chat/rooms/{chatRoomId}/close")
    public ResponseEntity<ApiResponse<ChatRoomCloseResponse>> closeRoom(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long chatRoomId) {
        
        // Service에서 이미 DTO로 변환된 결과를 받아옴
        ChatRoomResponse chatRoomResponse = chatRoomService.closeRoom(chatRoomId, userDetails.getUserId(), userDetails.getRole());
        
        ChatRoomCloseResponse response = ChatRoomCloseResponse.builder()
                .message("채팅방이 종료되었습니다.")
                .chatRoomId(chatRoomId)
                .chatRoom(chatRoomResponse)
                .build();
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/admin/chat/rooms")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<AdminChatRoomResponse>>> getAdminChatRooms(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false, defaultValue = "OPEN") String status,
            Pageable pageable) {
        
        Page<AdminChatRoomResponse> response = chatRoomService.getAdminChatRooms(
                status, pageable, userDetails.getRole());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
