package me.ngyu.carwashlife.application.carwash.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory.Fields;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class CarWashHistoryModificationTest {

  private static final OffsetDateTime SUBMITTED_AT = OffsetDateTime.parse("2026-10-07T12:00:00+09:00");
  private final CarWash carWash = CarWash.create("세차생활", new Coordinates(37.5, 127.0), SUBMITTED_AT);

  @DisplayName("변경 항목은 좌표를 하나로 묶은 12개 상세정보 항목만 포함한다.")
  @Test
  void changedFieldsTreatCoordinatesAsOneInformationItemAndExcludeMetadata() {
    assertThat(Fields.values()).extracting(Enum::name).containsExactly(
            "NAME", "ADDRESS", "COORDINATES", "HIGH_PRESSURE_WATER_PRICE",
            "FOAM_LANCE_AVAILABILITY", "FOAM_GUN_PRICE", "AIR_GUN_AVAILABILITY", "AIR_GUN_PRICE",
            "VACUUM_AVAILABILITY", "VACUUM_PRICE", "WASH_BAY_COUNT", "DRYING_BAY_COUNT");
  }

  @DisplayName("최초 등록 이력은 전체 정보 항목의 원래 출처가 된다.")
  @Test
  void registrationIsTheOriginalSourceOfAllSnapshotFields() {
    CarWashHistory registration = registration();

    assertThat(registration.getBaseHistory()).isNull();
    assertThat(registration.getChangedFields()).isEqualTo(EnumSet.allOf(Fields.class));
    assertThat(registration.getSourceHistory(Fields.NAME)).isSameAs(registration);
  }

  @DisplayName("정보 항목을 수정하면 실제 변경 항목만 기록하고 나머지 정보의 기존 출처를 유지한다.")
  @ParameterizedTest(name = "[{index}] {displayName}")
  @EnumSource(Fields.class)
  void eachSnapshotFieldIsComparedToTheBaseAndKeepsItsOwnSource(Fields field) {
    CarWashHistory base = registration();
    var information = information();
    switch (field) {
      case NAME -> information.name("수정한 세차생활");
      case ADDRESS -> information.address("서울시 강남구");
      case COORDINATES -> information.coordinates(new Coordinates(37.6, 127.1));
      case HIGH_PRESSURE_WATER_PRICE -> information.highPressureWaterPrice(3500);
      case FOAM_LANCE_AVAILABILITY -> information.foamLanceAvailability(FacilityAvailability.AVAILABLE);
      case FOAM_GUN_PRICE -> information.foamGunPrice(4000);
      case AIR_GUN_AVAILABILITY -> information.airGunAvailability(FacilityAvailability.AVAILABLE);
      case AIR_GUN_PRICE -> information.airGunPrice(1000);
      case VACUUM_AVAILABILITY -> information.vacuumAvailability(FacilityAvailability.AVAILABLE);
      case VACUUM_PRICE -> information.vacuumPrice(1000);
      case WASH_BAY_COUNT -> information.washBayCount(6);
      case DRYING_BAY_COUNT -> information.dryingBayCount(10);
    }
    CarWashHistory next = CarWashHistory.modify(carWash, base, 2L, VisitExperience.USED,
                                                SUBMITTED_AT, SUBMITTED_AT, information.build());

    assertThat(next.getType()).isEqualTo(CarWashHistoryType.MODIFICATION);
    assertThat(next.getBaseHistory()).isSameAs(base);
    assertThat(next.getChangedFields()).containsExactly(field);
    assertThat(next.getSourceHistory(field)).isSameAs(next);
    for (Fields unchanged : Fields.values()) {
      if (unchanged != field) {
        assertThat(next.getSourceHistory(unchanged)).isSameAs(base);
      }
    }
    assertThat(base.getName()).isEqualTo("세차생활");
    assertThat(base.getHighPressureWaterPrice()).isEqualTo(3000);
    assertThat(base.getMemberId()).isEqualTo(1L);
    assertThat(base.getVisitExperience()).isEqualTo(VisitExperience.NOT_USED);
  }

  @DisplayName("위도나 경도 중 하나만 바꾸어도 좌표 전체를 변경 항목으로 기록하고 새 이력을 출처로 제공한다.")
  @ParameterizedTest(name = "[{index}] {displayName}")
  @CsvSource({"37.6, 127.0", "37.5, 127.1"})
  void changingOneAxisReplacesTheSourceOfTheCoordinatePair(double latitude, double longitude) {
    CarWashHistory base = registration();
    CarWashHistory next = CarWashHistory.modify(carWash, base, 2L, VisitExperience.USED, SUBMITTED_AT, SUBMITTED_AT,
                                                information().coordinates(new Coordinates(latitude, longitude)).build());

    assertThat(next.getChangedFields()).containsExactly(Fields.COORDINATES);
    assertThat(next.getSourceHistory(Fields.COORDINATES)).isSameAs(next);
    assertThat(next.getCoordinates()).isEqualTo(new Coordinates(latitude, longitude));
    assertThat(base.getCoordinates()).isEqualTo(new Coordinates(37.5, 127.0));
  }

  @DisplayName("수정을 반복해도 변경하지 않은 정보는 원래 이력의 관찰 시점을 유지한다.")
  @Test
  void unchangedFieldsKeepTheOriginalObservationAcrossMultipleModifications() {
    CarWashHistory base = registration();
    CarWashHistory first = modification(base, "세차생활", "서울시", 3500);
    CarWashHistory second = modification(first, "새 이름", "서울시", 3500);

    assertThat(second.getChangedFields()).containsExactly(Fields.NAME);
    assertThat(second.getSourceHistory(Fields.NAME)).isSameAs(second);
    assertThat(second.getSourceHistory(Fields.HIGH_PRESSURE_WATER_PRICE)).isSameAs(first);
    assertThat(second.getSourceHistory(Fields.COORDINATES)).isSameAs(base);
    assertThat(second.getSourceHistory(Fields.COORDINATES).getObservedAt()).isEqualTo(SUBMITTED_AT.minusDays(100));
  }

  @DisplayName("주소와 가격을 null로 바꾸면 정보 삭제를 실제 변경으로 기록한다.")
  @Test
  void explicitlyClearingNullableInformationIsAChange() {
    CarWashHistory base = registration();
    CarWashHistory next = modification(base, "세차생활", null, null);

    assertThat(next.getChangedFields()).containsExactly(Fields.ADDRESS, Fields.HIGH_PRESSURE_WATER_PRICE);
    assertThat(next.getAddress()).isNull();
    assertThat(next.getHighPressureWaterPrice()).isNull();
    assertThat(next.getSourceHistory(Fields.ADDRESS)).isSameAs(next);
  }

  @DisplayName("세차장 정보 변경 없이 제보 메타데이터만 바꾸면 수정 이력 생성을 거절한다.")
  @Test
  void metadataAloneCannotCreateAModification() {
    assertThatThrownBy(() -> modification(registration(), "세차생활", "서울시", 3000)).isInstanceOf(IllegalArgumentException.class);
  }

  @DisplayName("외부에서 수정 이력의 변경 항목 목록을 변경하지 못하게 한다.")
  @Test
  void changedFieldsCannotBeModifiedByTheCaller() {
    CarWashHistory next = modification(registration(), "새 이름", "서울시", 3000);

    assertThatThrownBy(() -> next.getChangedFields().clear()).isInstanceOf(UnsupportedOperationException.class);
  }

  @DisplayName("등록 팩터리는 기준 이력 없이 최초 등록 유형의 이력을 생성한다.")
  @Test
  void registrationFactoryCreatesRegistrationWithoutABaseHistory() {
    CarWashHistory history = registration();

    assertThat(history.getType()).isEqualTo(CarWashHistoryType.REGISTRATION);
    assertThat(history.getBaseHistory()).isNull();
  }

  @DisplayName("수정 요청에 기준 이력이 없으면 이력 생성을 거절한다.")
  @Test
  void modificationRequiresABaseHistory() {
    assertThatThrownBy(() -> modification(null, "새 이름", "서울시", 3000))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @DisplayName("수정 요청의 기준 이력이 다른 세차장에 속하면 이력 생성을 거절한다.")
  @Test
  void modificationCannotReferenceAnotherCarWash() {
    CarWash other = CarWash.create("다른 세차장", new Coordinates(37.5, 127.0), SUBMITTED_AT);

    assertThatThrownBy(() -> CarWashHistory.modify(other, registration(), 2L, VisitExperience.USED, SUBMITTED_AT, SUBMITTED_AT,
                                                   information()
                                                           .name("새 이름").build()))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @DisplayName("수정 요청의 상세정보가 없으면 이력 생성을 거절한다.")
  @Test
  void modificationRequiresInformation() {
    assertThatThrownBy(() -> CarWashHistory.modify(carWash, registration(), 2L, VisitExperience.USED,
                                                   SUBMITTED_AT, SUBMITTED_AT, null))
            .isInstanceOf(NullPointerException.class);
  }

  private CarWashHistory registration() {
    return CarWashHistory.register(carWash, 1L, VisitExperience.NOT_USED, SUBMITTED_AT.minusDays(100), SUBMITTED_AT,
                                   information().build());
  }

  private CarWashHistory modification(CarWashHistory base,
                                      String name,
                                      String address,
                                      Integer highPressureWaterPrice) {
    return CarWashHistory.modify(carWash, base, 2L, VisitExperience.USED, SUBMITTED_AT, SUBMITTED_AT,
                                 information()
                                         .name(name)
                                         .address(address)
                                         .highPressureWaterPrice(highPressureWaterPrice).build());
  }

  private CarWashInformation.CarWashInformationBuilder information() {
    return CarWashInformation.builder()
                             .name("세차생활")
                             .address("서울시")
                             .coordinates(new Coordinates(37.5, 127.0))
                             .highPressureWaterPrice(3000);
  }

}
