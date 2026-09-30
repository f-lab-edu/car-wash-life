package me.ngyu.carwashlife.application.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class LoginDto {

  private LoginDto() {
  }

  public record Request(
      @NotBlank @Email String email,
      @NotBlank String password
  ) {
  }

  public record Response(
      String accessToken,
      String tokenType,
      long expiresIn,
      Long memberId
  ) {
  }
}
