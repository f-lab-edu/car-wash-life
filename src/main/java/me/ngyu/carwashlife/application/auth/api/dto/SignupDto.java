package me.ngyu.carwashlife.application.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import me.ngyu.carwashlife.application.member.domain.VehicleType;
import me.ngyu.carwashlife.application.member.domain.WashExperience;

public class SignupDto {

  private SignupDto() {
  }

  public record Request(
      @NotBlank @Email String email,
      @NotBlank String password,
      String residenceRegionCode,
      VehicleType vehicleType,
      WashExperience washExperience
  ) {
  }

  public record Response(Long memberId, String email, OffsetDateTime createdAt) {
  }
}
