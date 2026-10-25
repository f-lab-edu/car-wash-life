package me.ngyu.carwashlife.application.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import me.ngyu.carwashlife.application.auth.domain.SignupInformation;
import me.ngyu.carwashlife.application.member.domain.VehicleType;
import me.ngyu.carwashlife.application.member.domain.WashExperience;

public class SignupDto {

  private SignupDto() {
  }

  public record Request(@NotBlank @Email String email,
                        @NotBlank String password,
                        String residenceRegionCode,
                        VehicleType vehicleType,
                        WashExperience washExperience) {

    /**
     * 회원가입 요청을 회원 생성에 필요한 도메인 값으로 변환한다.
     */
    public SignupInformation toDomain() {
      return new SignupInformation(email, password, residenceRegionCode, vehicleType, washExperience);
    }
  }

  public record Response(Long memberId, String email, OffsetDateTime createdAt) {

  }
}
