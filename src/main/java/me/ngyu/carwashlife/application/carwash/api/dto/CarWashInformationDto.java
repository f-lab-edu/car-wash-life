package me.ngyu.carwashlife.application.carwash.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import me.ngyu.carwashlife.application.carwash.domain.CarWashInformation;
import me.ngyu.carwashlife.application.carwash.domain.Coordinates;
import me.ngyu.carwashlife.application.carwash.domain.FacilityAvailability;
import me.ngyu.carwashlife.common.exception.ApplicationException;
import me.ngyu.carwashlife.common.exception.ErrorCode;

public class CarWashInformationDto {

  private CarWashInformationDto() {
  }

  public record Request(@NotBlank @Size(max = 255) String name,
                        @Size(max = 255) String address,
                        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
                        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
                        @PositiveOrZero Integer highPressureWaterPrice,
                        FacilityAvailability foamLanceAvailability,
                        @PositiveOrZero Integer foamGunPrice,
                        FacilityAvailability airGunAvailability,
                        @PositiveOrZero Integer airGunPrice,
                        FacilityAvailability vacuumAvailability,
                        @PositiveOrZero Integer vacuumPrice,
                        @PositiveOrZero Integer washBayCount,
                        @PositiveOrZero Integer dryingBayCount) {

    /**
     * 요청 정보를 도메인 값으로 변환하고 잘못된 입력을 검증 오류로 분류한다.
     */
    public CarWashInformation toDomain() {
      try {
        return new CarWashInformation(name, address, new Coordinates(latitude, longitude), highPressureWaterPrice,
                                      foamLanceAvailability, foamGunPrice, airGunAvailability, airGunPrice,
                                      vacuumAvailability, vacuumPrice, washBayCount, dryingBayCount);
      } catch (IllegalArgumentException exception) {
        throw new ApplicationException(ErrorCode.VALIDATION_ERROR, exception);
      }
    }
  }
}
