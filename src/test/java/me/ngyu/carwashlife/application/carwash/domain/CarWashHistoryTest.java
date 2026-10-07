package me.ngyu.carwashlife.application.carwash.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CarWashHistoryTest {

  private static final OffsetDateTime SUBMITTED_AT = OffsetDateTime.parse("2026-10-05T10:00:00+09:00");

  @Test
  @DisplayName("이력에서 입력하지 않은 시설과 가격 및 베이 수는 미확인 상태로 유지한다.")
  void unspecifiedFacilitiesPricesAndBayCountsRemainUnknown() {
    CarWashHistory history = registration().build();

    assertThat(history.getFoamLanceAvailability()).isEqualTo(FacilityAvailability.UNKNOWN);
    assertThat(history.getAirGunAvailability()).isEqualTo(FacilityAvailability.UNKNOWN);
    assertThat(history.getVacuumAvailability()).isEqualTo(FacilityAvailability.UNKNOWN);
    assertThat(history.getHighPressureWaterPrice()).isNull();
    assertThat(history.getFoamGunPrice()).isNull();
    assertThat(history.getAirGunPrice()).isNull();
    assertThat(history.getVacuumPrice()).isNull();
    assertThat(history.getWashBayCount()).isNull();
    assertThat(history.getDryingBayCount()).isNull();
  }

  @Test
  @DisplayName("이력의 시설과 가격 및 베이 수는 제보한 값으로 저장한다.")
  void facilityPricesAndBayCountsAreStoredAsReported() {
    CarWashHistory history = registration()
            .highPressureWaterPrice(3000)
            .foamLanceAvailability(FacilityAvailability.AVAILABLE)
            .foamGunPrice(2000)
            .airGunAvailability(FacilityAvailability.AVAILABLE)
            .airGunPrice(0)
            .vacuumAvailability(FacilityAvailability.AVAILABLE)
            .vacuumPrice(1000)
            .washBayCount(6)
            .dryingBayCount(10)
            .build();

    assertThat(history.getHighPressureWaterPrice()).isEqualTo(3000);
    assertThat(history.getFoamLanceAvailability()).isEqualTo(FacilityAvailability.AVAILABLE);
    assertThat(history.getFoamGunPrice()).isEqualTo(2000);
    assertThat(history.getAirGunAvailability()).isEqualTo(FacilityAvailability.AVAILABLE);
    assertThat(history.getAirGunPrice()).isZero();
    assertThat(history.getVacuumAvailability()).isEqualTo(FacilityAvailability.AVAILABLE);
    assertThat(history.getVacuumPrice()).isEqualTo(1000);
    assertThat(history.getWashBayCount()).isEqualTo(6);
    assertThat(history.getDryingBayCount()).isEqualTo(10);
  }

  @Test
  @DisplayName("이력의 관찰 시점과 제출 시점을 작성자 및 경험 유형과 함께 구분하여 저장한다.")
  void observationAndSubmissionTimesAreKeptSeparate() {
    OffsetDateTime observedAt = SUBMITTED_AT.minusDays(2);
    CarWashHistory history = registration().observedAt(observedAt).build();

    assertThat(history.getObservedAt()).isEqualTo(observedAt);
    assertThat(history.getSubmittedAt()).isEqualTo(SUBMITTED_AT);
    assertThat(history.getMemberId()).isEqualTo(1L);
    assertThat(history.getVisitExperience()).isEqualTo(VisitExperience.NOT_USED);
  }

  @Test
  @DisplayName("경험 유형이 없으면 이력 생성을 거절한다.")
  void visitExperienceIsRequired() {
    assertThatThrownBy(() -> registration().visitExperience(null).build())
            .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("관찰 시점이 제출 시점보다 늦으면 이력 생성을 거절한다.")
  void observationAfterSubmissionIsRejected() {
    assertThatThrownBy(() -> registration()
            .observedAt(SUBMITTED_AT.plusSeconds(1))
            .build())
            .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @DisplayName("작성자 식별자가 양수가 아니면 이력 생성을 거절한다.")
  @ValueSource(longs = {0, -1})
  void invalidAuthorIsRejected(long memberId) {
    assertThatThrownBy(() -> registration().memberId(memberId).build())
            .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @DisplayName("시설 가격이 음수이면 이력 생성을 거절한다.")
  @ValueSource(strings = {"water", "foam", "air", "vacuum"})
  void negativePriceIsRejected(String facility) {
    var builder = registration();
    switch (facility) {
      case "water" -> builder.highPressureWaterPrice(-1);
      case "foam" -> builder.foamGunPrice(-1);
      case "air" -> builder.airGunPrice(-1);
      case "vacuum" -> builder.vacuumPrice(-1);
    }

    assertThatThrownBy(builder::build).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("세차 베이 수가 음수이면 이력 생성을 거절한다.")
  void negativeWashBayCountIsRejected() {
    assertThatThrownBy(() -> registration().washBayCount(-1).build())
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("드라잉 존 베이 수가 음수이면 이력 생성을 거절한다.")
  void negativeDryingBayCountIsRejected() {
    assertThatThrownBy(() -> registration().dryingBayCount(-1).build())
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("베이 수를 0으로 제보하면 미확인 값과 구분하여 0으로 저장한다.")
  void zeroBayCountsAreDifferentFromUnknownCounts() {
    CarWashHistory history = registration().washBayCount(0).dryingBayCount(0).build();

    assertThat(history.getWashBayCount()).isZero();
    assertThat(history.getDryingBayCount()).isZero();
  }

  @Test
  @DisplayName("사용 불가 에어건에 가격이 있으면 이력 생성을 거절한다.")
  void unavailableAirGunCannotHaveAPrice() {
    assertThatThrownBy(() -> registration()
            .airGunAvailability(FacilityAvailability.UNAVAILABLE)
            .airGunPrice(0)
            .build())
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("사용 불가 청소기에 가격이 있으면 이력 생성을 거절한다.")
  void unavailableVacuumCannotHaveAPrice() {
    assertThatThrownBy(() -> registration()
            .vacuumAvailability(FacilityAvailability.UNAVAILABLE)
            .vacuumPrice(1000)
            .build())
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("사용 불가 시설은 가격 없이 이력에 저장한다.")
  void unavailableFacilityCanBeRecordedWithoutAPrice() {
    CarWashHistory history = registration()
            .airGunAvailability(FacilityAvailability.UNAVAILABLE)
            .vacuumAvailability(FacilityAvailability.UNAVAILABLE)
            .build();

    assertThat(history.getAirGunAvailability()).isEqualTo(FacilityAvailability.UNAVAILABLE);
    assertThat(history.getAirGunPrice()).isNull();
    assertThat(history.getVacuumAvailability()).isEqualTo(FacilityAvailability.UNAVAILABLE);
    assertThat(history.getVacuumPrice()).isNull();
  }

  @Test
  @DisplayName("위도가 없으면 세차장 이력 생성을 거절한다.")
  void missingLatitudeIsRejected() {
    assertThatThrownBy(() -> registration().latitude(null).build())
            .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @DisplayName("위도가 허용 범위를 벗어나거나 유한하지 않으면 생성을 거절한다.")
  @ValueSource(doubles = {-90.1, 90.1, Double.NaN, Double.POSITIVE_INFINITY})
  void invalidLatitudeIsRejected(double latitude) {
    assertThatThrownBy(() -> registration().latitude(latitude).build())
            .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @DisplayName("경도가 허용 범위를 벗어나거나 유한하지 않으면 생성을 거절한다.")
  @ValueSource(doubles = {-180.1, 180.1, Double.NaN, Double.NEGATIVE_INFINITY})
  void invalidLongitudeIsRejected(double longitude) {
    assertThatThrownBy(() -> registration().longitude(longitude).build())
            .isInstanceOf(IllegalArgumentException.class);
  }

  private CarWashHistory.CarWashHistoryBuilder registration() {
    CarWash carWash = CarWash.create("세차생활", 37.5, 127.0, SUBMITTED_AT);
    return CarWashHistory.builder()
                         .carWash(carWash)
                         .memberId(1L)
                         .type(CarWashHistoryType.REGISTRATION)
                         .visitExperience(VisitExperience.NOT_USED)
                         .observedAt(SUBMITTED_AT)
                         .submittedAt(SUBMITTED_AT)
                         .name("세차생활")
                         .address("서울시")
                         .latitude(37.5)
                         .longitude(127.0);
  }
}
