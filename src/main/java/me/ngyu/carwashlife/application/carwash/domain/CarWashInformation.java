package me.ngyu.carwashlife.application.carwash.domain;

import lombok.Builder;

@Builder
public record CarWashInformation(String name,
                                 String address,
                                 Double latitude,
                                 Double longitude,
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
    validateLocation(name, latitude, longitude);
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

  private static void validateLocation(String name, Double latitude, Double longitude) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("세차장 이름은 필수입니다.");
    }
    if (latitude == null || !Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
      throw new IllegalArgumentException("위도는 -90부터 90 사이여야 합니다.");
    }
    if (longitude == null || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
      throw new IllegalArgumentException("경도는 -180부터 180 사이여야 합니다.");
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
