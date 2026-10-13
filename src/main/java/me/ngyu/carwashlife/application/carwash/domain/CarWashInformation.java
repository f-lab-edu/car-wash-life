package me.ngyu.carwashlife.application.carwash.domain;

import lombok.Builder;

@Builder
public record CarWashInformation(String name,
                                 String address,
                                 Coordinates coordinates,
                                 Integer highPressureWaterPrice,
                                 FacilityAvailability foamLanceAvailability,
                                 Integer foamGunPrice,
                                 FacilityAvailability airGunAvailability,
                                 Integer airGunPrice,
                                 FacilityAvailability vacuumAvailability,
                                 Integer vacuumPrice,
                                 Integer washBayCount,
                                 Integer dryingBayCount) {

  public CarWashInformation {
    validateName(name);
    if (coordinates == null) {
      throw new IllegalArgumentException("좌표는 필수입니다.");
    }
    validateNonNegative(highPressureWaterPrice, "고압수 가격");
    validateNonNegative(foamGunPrice, "폼건 가격");
    validateNonNegative(airGunPrice, "에어건 가격");
    validateNonNegative(vacuumPrice, "청소기 가격");
    validateNonNegative(washBayCount, "세차 베이 수");
    validateNonNegative(dryingBayCount, "드라잉 존 베이 수");
    foamLanceAvailability = defaultAvailability(foamLanceAvailability);
    airGunAvailability = defaultAvailability(airGunAvailability);
    vacuumAvailability = defaultAvailability(vacuumAvailability);
    validateFacilityPrice(airGunAvailability, airGunPrice, "에어건");
    validateFacilityPrice(vacuumAvailability, vacuumPrice, "청소기");
  }

  private static FacilityAvailability defaultAvailability(FacilityAvailability availability) {
    return availability == null ? FacilityAvailability.UNKNOWN : availability;
  }

  private static void validateName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("세차장 이름은 필수입니다.");
    }
  }

  private static void validateNonNegative(Integer value, String label) {
    if (value != null && value < 0) {
      throw new IllegalArgumentException(label + "은 음수일 수 없습니다.");
    }
  }

  private static void validateFacilityPrice(FacilityAvailability availability,
                                            Integer price,
                                            String label) {
    if (!availability.supportsPrice() && price != null) {
      throw new IllegalArgumentException(label + "을 사용할 수 없으면 가격을 입력할 수 없습니다.");
    }
  }
}
