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
  private final CarWash carWash = CarWash.create("세차생활", new Coordinates(37.5, 127.0), SUBMITTED_AT);

  @DisplayName("저장 전 식별자가 없는 원장으로 최초 이력을 생성하면 원장 관계와 상세정보를 유지한다.")
  @Test
  void registrationCanReferenceAnUnsavedCarWash() {
    CarWashHistory history = registration();

    assertThat(carWash.getId()).isNull();
    assertThat(history.getId()).isNull();
    assertThat(history.getCarWash()).isSameAs(carWash);
    assertThat(history.getName()).isEqualTo("세차생활");
    assertThat(history.getCoordinates()).isEqualTo(new Coordinates(37.5, 127.0));
  }

  @DisplayName("이력에서 입력하지 않은 시설과 가격 및 베이 수는 미확인 상태로 유지한다.")
  @Test
  void unspecifiedFacilitiesPricesAndBayCountsRemainUnknown() {
    CarWashHistory history = registration();

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

  @DisplayName("이력의 시설과 가격 및 베이 수는 제보한 값으로 저장한다.")
  @Test
  void facilityPricesAndBayCountsAreStoredAsReported() {
    CarWashHistory history = CarWashHistory.register(carWash, 1L, VisitExperience.NOT_USED, SUBMITTED_AT, SUBMITTED_AT,
                                                     information()
                                                             .highPressureWaterPrice(3000)
                                                             .foamLanceAvailability(FacilityAvailability.AVAILABLE)
                                                             .foamGunPrice(2000)
                                                             .airGunAvailability(FacilityAvailability.AVAILABLE)
                                                             .airGunPrice(0)
                                                             .vacuumAvailability(FacilityAvailability.AVAILABLE)
                                                             .vacuumPrice(1000)
                                                             .washBayCount(6)
                                                             .dryingBayCount(10).build());

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

  @DisplayName("이력의 관찰 시점과 제출 시점을 작성자 및 경험 유형과 함께 구분하여 저장한다.")
  @Test
  void observationAndSubmissionTimesAreKeptSeparate() {
    OffsetDateTime observedAt = SUBMITTED_AT.minusDays(2);
    CarWashHistory history = CarWashHistory.register(carWash, 1L, VisitExperience.NOT_USED, observedAt, SUBMITTED_AT,
                                                     information().build());

    assertThat(history.getObservedAt()).isEqualTo(observedAt);
    assertThat(history.getSubmittedAt()).isEqualTo(SUBMITTED_AT);
    assertThat(history.getMemberId()).isEqualTo(1L);
    assertThat(history.getVisitExperience()).isEqualTo(VisitExperience.NOT_USED);
  }

  @DisplayName("경험 유형이 없으면 이력 생성을 거절한다.")
  @Test
  void visitExperienceIsRequired() {
    assertThatThrownBy(() -> CarWashHistory.register(carWash, 1L, null, SUBMITTED_AT, SUBMITTED_AT,
                                                     information().build()))
            .isInstanceOf(NullPointerException.class);
  }

  @DisplayName("관찰 시점이 제출 시점보다 늦으면 이력 생성을 거절한다.")
  @Test
  void observationAfterSubmissionIsRejected() {
    assertThatThrownBy(
            () -> CarWashHistory.register(carWash, 1L, VisitExperience.NOT_USED, SUBMITTED_AT.plusSeconds(1), SUBMITTED_AT,
                                          information().build()))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @DisplayName("작성자 식별자가 양수가 아니면 이력 생성을 거절한다.")
  @ParameterizedTest(name = "[{index}] {displayName}")
  @ValueSource(longs = {0, -1})
  void invalidAuthorIsRejected(long memberId) {
    assertThatThrownBy(
            () -> CarWashHistory.register(carWash, memberId, VisitExperience.NOT_USED, SUBMITTED_AT, SUBMITTED_AT,
                                          information().build()))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @DisplayName("최초 등록의 상세정보가 없으면 이력 생성을 거절한다.")
  @Test
  void registrationRequiresInformation() {
    assertThatThrownBy(() -> CarWashHistory.register(carWash, 1L, VisitExperience.NOT_USED,
                                                     SUBMITTED_AT, SUBMITTED_AT, null))
            .isInstanceOf(NullPointerException.class);
  }

  private CarWashHistory registration() {
    return CarWashHistory.register(carWash, 1L, VisitExperience.NOT_USED, SUBMITTED_AT, SUBMITTED_AT,
                                   information().build());
  }

  private CarWashInformation.CarWashInformationBuilder information() {
    return CarWashInformation.builder()
                             .name("세차생활")
                             .address("서울시")
                             .coordinates(new Coordinates(37.5, 127.0));
  }

}
