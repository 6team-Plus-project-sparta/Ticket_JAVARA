package com.example.ticket_javara.domain.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원가입 요청 DTO (FN-AUTH-01)
 */
@Getter
@NoArgsConstructor
public class SignupRequest {

    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @NotBlank(message = "이메일은 필수입니다.")
    @Size(max = 100, message = "이메일은 최대 100자입니다.")
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Pattern(
            regexp = "^(?=.*[a-zA-Z])(?=.*\\d).{8,}$",
            message = "비밀번호는 8자 이상, 영문+숫자를 포함해야 합니다."
    )
    private String password;

    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(min = 2, max = 20, message = "닉네임은 2~20자입니다.")
    @Pattern(regexp = "^[^!@#$%^&*(),.?\":{}|<>]*$", message = "닉네임에 특수문자는 사용할 수 없습니다.")
    private String nickname;
}
