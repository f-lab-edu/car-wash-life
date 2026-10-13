package me.ngyu.carwashlife.application.carwash.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CarWashInformationTest {

  @DisplayName("시설 가격이 음수이면 상세정보 생성을 거절한다.")
  @ParameterizedTest(name = "[{index}] {displayName}")
  @ValueSource(strings = {"water", "foam", "air", "vacuum"})
  void negativePriceIsRejected(String facility) {
    assertThatThrownBy(() -> information()
            .highPressureWaterPrice(facility.equals("water") ? -1 : null)
            .foamGunPrice(facility.equals("foam") ? -1 : null)
            .airGunPrice(facility.equals("air") ? -1 : null)
            .vacuumPrice(facility.equals("vacuum") ? -1 : null).build())
            .isInstanceOf(IllegalArgumentException.class);
  }

  @DisplayName("세차 베이 수가 음수이면 상세정보 생성을 거절한다.")
  @Test
  void negativeWashBayCountIsRejected() {
    assertThatThrownBy(() -> information()
            .washBayCount(-1).build())
            .isInstanceOf(IllegalArgumentException.class);
  }

  @DisplayName("드라잉 존 베이 수가 음수이면 상세정보 생성을 거절한다.")
  @Test
  void negativeDryingBayCountIsRejected() {
    assertThatThrownBy(() -> information()
            .dryingBayCount(-1).build())
            .isInstanceOf(IllegalArgumentException.class);
  }

  @DisplayName("베이 수를 0으로 제보하면 미확인 값과 구분하여 0으로 저장한다.")
  @Test
  void zeroBayCountsAreDifferentFromUnknownCounts() {
    CarWashInformation information = information()
            .washBayCount(0)
            .dryingBayCount(0).build();

    assertThat(information.washBayCount()).isZero();
    assertThat(information.dryingBayCount()).isZero();
  }

  @DisplayName("사용 불가 에어건에 가격이 있으면 상세정보 생성을 거절한다.")
  @Test
  void unavailableAirGunCannotHaveAPrice() {
    assertThatThrownBy(
            () -> information()
                    .airGunAvailability(FacilityAvailability.UNAVAILABLE)
                    .airGunPrice(0).build())
            .isInstanceOf(IllegalArgumentException.class);
  }

  @DisplayName("사용 불가 청소기에 가격이 있으면 상세정보 생성을 거절한다.")
  @Test
  void unavailableVacuumCannotHaveAPrice() {
    assertThatThrownBy(
            () -> information()
                    .vacuumAvailability(FacilityAvailability.UNAVAILABLE)
                    .vacuumPrice(1000).build())
            .isInstanceOf(IllegalArgumentException.class);
  }

  @DisplayName("사용 불가 시설은 가격 없이 상세정보에 저장한다.")
  @Test
  void unavailableFacilityCanBeRecordedWithoutAPrice() {
    CarWashInformation information = information()
            .airGunAvailability(FacilityAvailability.UNAVAILABLE)
            .vacuumAvailability(FacilityAvailability.UNAVAILABLE).build();

    assertThat(information.airGunAvailability()).isEqualTo(FacilityAvailability.UNAVAILABLE);
    assertThat(information.airGunPrice()).isNull();
    assertThat(information.vacuumAvailability()).isEqualTo(FacilityAvailability.UNAVAILABLE);
    assertThat(information.vacuumPrice()).isNull();
  }

  @DisplayName("빌더로 생성한 상세정보에서 입력하지 않은 시설은 미확인으로 정규화하고 가격과 베이 수는 null로 보존한다.")
  @Test
  void builderNormalizesUnspecifiedFacilitiesWithoutInventingPricesOrBayCounts() {
    CarWashInformation information = information().build();

    assertThat(information.foamLanceAvailability()).isEqualTo(FacilityAvailability.UNKNOWN);
    assertThat(information.airGunAvailability()).isEqualTo(FacilityAvailability.UNKNOWN);
    assertThat(information.vacuumAvailability()).isEqualTo(FacilityAvailability.UNKNOWN);
    assertThat(information.highPressureWaterPrice()).isNull();
    assertThat(information.foamGunPrice()).isNull();
    assertThat(information.airGunPrice()).isNull();
    assertThat(information.vacuumPrice()).isNull();
    assertThat(information.washBayCount()).isNull();
    assertThat(information.dryingBayCount()).isNull();
  }

  @DisplayName("생성자로 직접 상세정보를 생성해도 생략한 시설은 미확인으로 정규화하고 입력한 무료 가격은 보존한다.")
  @Test
  void directConstructionNormalizesNullFacilitiesAndPreservesAReportedFreePrice() {
    CarWashInformation information = new CarWashInformation("세차생활", null, new Coordinates(37.5, 127.0),
                                                            null, null, null, null, 0, null, 0, null, null);

    assertThat(information.foamLanceAvailability()).isEqualTo(FacilityAvailability.UNKNOWN);
    assertThat(information.airGunAvailability()).isEqualTo(FacilityAvailability.UNKNOWN);
    assertThat(information.vacuumAvailability()).isEqualTo(FacilityAvailability.UNKNOWN);
    assertThat(information.address()).isNull();
    assertThat(information.airGunPrice()).isZero();
    assertThat(information.vacuumPrice()).isZero();
  }

  @DisplayName("생성자로 직접 음수 가격을 입력해도 상세정보 생성을 거절한다.")
  @Test
  void directConstructionCannotBypassPriceValidation() {
    assertThatThrownBy(() -> new CarWashInformation("세차생활", null, new Coordinates(37.5, 127.0),
                                                    -1, null, null, null, null, null, null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @DisplayName("세차장 이름이 null이거나 비어 있거나 공백뿐이면 상세정보 생성을 거절한다.")
  @ParameterizedTest(name = "[{index}] {displayName}")
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void missingOrBlankNameIsRejected(String name) {
    assertThatThrownBy(() -> information().name(name).build()).isInstanceOf(IllegalArgumentException.class);
  }

  @DisplayName("좌표가 없으면 상세정보 생성을 거절한다.")
  @Test
  void missingCoordinatesAreRejected() {
    assertThatThrownBy(() -> information().coordinates(null).build()).isInstanceOf(IllegalArgumentException.class);
  }

  private CarWashInformation.CarWashInformationBuilder information() {
    return CarWashInformation.builder().name("세차생활").address("서울시").coordinates(new Coordinates(37.5, 127.0));
  }
}
