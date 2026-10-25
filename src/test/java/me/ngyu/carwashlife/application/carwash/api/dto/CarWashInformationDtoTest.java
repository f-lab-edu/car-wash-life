package me.ngyu.carwashlife.application.carwash.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import me.ngyu.carwashlife.application.carwash.domain.FacilityAvailability;
import me.ngyu.carwashlife.common.exception.ApplicationException;
import me.ngyu.carwashlife.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CarWashInformationDtoTest {

  @Test
  @DisplayName("도메인 변환 중 시설과 가격이 모순되면 원인을 보존한 검증 오류로 변환한다.")
  void contradictoryFacilityPricePreservesTheValidationCause() {
    CarWashInformationDto.Request request = new CarWashInformationDto.Request("세차생활", null, 37.5, 127.0, null, null, null,
                                                                              FacilityAvailability.UNAVAILABLE, 0, null, null, null, null);

    ApplicationException exception = catchThrowableOfType(ApplicationException.class, request::toDomain);

    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    assertThat(exception).hasCauseInstanceOf(IllegalArgumentException.class);
  }
}
