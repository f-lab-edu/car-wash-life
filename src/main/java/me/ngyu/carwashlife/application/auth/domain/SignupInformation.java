package me.ngyu.carwashlife.application.auth.domain;

import me.ngyu.carwashlife.application.member.domain.VehicleType;
import me.ngyu.carwashlife.application.member.domain.WashExperience;

public record SignupInformation(String email,
                                String password,
                                String residenceRegionCode,
                                VehicleType vehicleType,
                                WashExperience washExperience) {

  /**
   * 문자열 표현에 비밀번호를 노출하지 않는다.
   */
  @Override
  public String toString() {
    return "SignupInformation[password=***]";
  }
}
