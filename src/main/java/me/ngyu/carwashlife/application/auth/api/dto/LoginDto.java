package me.ngyu.carwashlife.application.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import me.ngyu.carwashlife.application.auth.domain.LoginCredentials;

public class LoginDto {

  private LoginDto() {
  }

  public record Request(@NotBlank @Email String email,
                        @NotBlank String password) {

    /**
     * 로그인 요청을 인증에 필요한 도메인 값으로 변환한다.
     */
    public LoginCredentials toDomain() {
      return new LoginCredentials(email, password);
    }
  }

  public record Response(String accessToken,
                         String tokenType,
                         long expiresIn,
                         Long memberId) {

  }
}
