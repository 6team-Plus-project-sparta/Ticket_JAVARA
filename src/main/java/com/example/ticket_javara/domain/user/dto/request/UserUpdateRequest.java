package com.example.ticket_javara.domain.user.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserUpdateRequest {

    @Size(min = 2, max = 50, message = "닉네임은 2~50자 사이여야 합니다.")
    private String nickname;

    @Size(min = 8, max = 100, message = "비밀번호는 최소 8자 이상이어야 합니다.")
    private String password;

    private String currentPassword;
}
