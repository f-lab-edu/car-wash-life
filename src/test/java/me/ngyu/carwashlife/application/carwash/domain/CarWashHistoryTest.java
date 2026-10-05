package me.ngyu.carwashlife.application.carwash.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CarWashHistoryTest {

  private static final OffsetDateTime SUBMITTED_AT = OffsetDateTime.parse("2026-10-05T10:00:00+09:00");

  @Test
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
  void observationAndSubmissionTimesAreKeptSeparate() {
    OffsetDateTime observedAt = SUBMITTED_AT.minusDays(2);
    CarWashHistory history = registration().observedAt(observedAt).build();

    assertThat(history.getObservedAt()).isEqualTo(observedAt);
    assertThat(history.getSubmittedAt()).isEqualTo(SUBMITTED_AT);
    assertThat(history.getMemberId()).isEqualTo(1L);
    assertThat(history.getVisitExperience()).isEqualTo(VisitExperience.NOT_USED);
  }

  @Test
  void visitExperienceIsRequired() {
    assertThatThrownBy(() -> registration().visitExperience(null).build())
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void observationAfterSubmissionIsRejected() {
    assertThatThrownBy(() -> registration()
        .observedAt(SUBMITTED_AT.plusSeconds(1))
        .build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1})
  void invalidAuthorIsRejected(long memberId) {
    assertThatThrownBy(() -> registration().memberId(memberId).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
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
  void negativeWashBayCountIsRejected() {
    assertThatThrownBy(() -> registration().washBayCount(-1).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void negativeDryingBayCountIsRejected() {
    assertThatThrownBy(() -> registration().dryingBayCount(-1).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void zeroBayCountsAreDifferentFromUnknownCounts() {
    CarWashHistory history = registration().washBayCount(0).dryingBayCount(0).build();

    assertThat(history.getWashBayCount()).isZero();
    assertThat(history.getDryingBayCount()).isZero();
  }

  @Test
  void unavailableAirGunCannotHaveAPrice() {
    assertThatThrownBy(() -> registration()
        .airGunAvailability(FacilityAvailability.UNAVAILABLE)
        .airGunPrice(0)
        .build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void unavailableVacuumCannotHaveAPrice() {
    assertThatThrownBy(() -> registration()
        .vacuumAvailability(FacilityAvailability.UNAVAILABLE)
        .vacuumPrice(1000)
        .build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
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
  void missingLatitudeIsRejected() {
    assertThatThrownBy(() -> registration().latitude(null).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(doubles = {-90.1, 90.1, Double.NaN, Double.POSITIVE_INFINITY})
  void invalidLatitudeIsRejected(double latitude) {
    assertThatThrownBy(() -> registration().latitude(latitude).build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
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
