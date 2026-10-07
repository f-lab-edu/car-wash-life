package me.ngyu.carwashlife.application.carwash.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CarWashHistoryModificationTest {

  private static final OffsetDateTime SUBMITTED_AT = OffsetDateTime.parse("2026-10-07T12:00:00+09:00");
  private final CarWash carWash = CarWash.create("세차생활", 37.5, 127.0, SUBMITTED_AT);

  @Test
  void registrationIsTheOriginalSourceOfAllSnapshotFields() {
    CarWashHistory registration = registration();

    assertThat(registration.getBaseHistory()).isNull();
    assertThat(registration.getChangedFields()).isEqualTo(EnumSet.allOf(CarWashHistoryField.class));
    assertThat(registration.getSourceHistory(CarWashHistoryField.NAME)).isSameAs(registration);
  }

  @ParameterizedTest
  @EnumSource(CarWashHistoryField.class)
  void eachSnapshotFieldIsComparedToTheBaseAndKeepsItsOwnSource(CarWashHistoryField field) {
    CarWashHistory base = registration();
    var builder = modification(base);
    switch (field) {
      case NAME -> builder.name("수정한 세차생활");
      case ADDRESS -> builder.address("서울시 강남구");
      case LATITUDE -> builder.latitude(37.6);
      case LONGITUDE -> builder.longitude(127.1);
      case HIGH_PRESSURE_WATER_PRICE -> builder.highPressureWaterPrice(3500);
      case FOAM_LANCE_AVAILABILITY -> builder.foamLanceAvailability(FacilityAvailability.AVAILABLE);
      case FOAM_GUN_PRICE -> builder.foamGunPrice(4000);
      case AIR_GUN_AVAILABILITY -> builder.airGunAvailability(FacilityAvailability.AVAILABLE);
      case AIR_GUN_PRICE -> builder.airGunPrice(1000);
      case VACUUM_AVAILABILITY -> builder.vacuumAvailability(FacilityAvailability.AVAILABLE);
      case VACUUM_PRICE -> builder.vacuumPrice(1000);
      case WASH_BAY_COUNT -> builder.washBayCount(6);
      case DRYING_BAY_COUNT -> builder.dryingBayCount(10);
      case PHOTOS -> builder.newPhotoIds(java.util.List.of(1L));
    }
    CarWashHistory next = builder.build();

    assertThat(next.getBaseHistory()).isSameAs(base);
    assertThat(next.getChangedFields()).containsExactly(field);
    assertThat(next.getSourceHistory(field)).isSameAs(next);
    for (CarWashHistoryField unchanged : CarWashHistoryField.values()) {
      if (unchanged != field) {
        assertThat(next.getSourceHistory(unchanged)).isSameAs(base);
      }
    }
    assertThat(base.getName()).isEqualTo("세차생활");
    assertThat(base.getHighPressureWaterPrice()).isEqualTo(3000);
    assertThat(base.getMemberId()).isEqualTo(1L);
    assertThat(base.getVisitExperience()).isEqualTo(VisitExperience.NOT_USED);
  }

  @Test
  void unchangedFieldsKeepTheOriginalObservationAcrossMultipleModifications() {
    CarWashHistory base = registration();
    CarWashHistory first = modification(base).highPressureWaterPrice(3500).build();
    CarWashHistory second = modification(first).highPressureWaterPrice(3500).name("새 이름").build();

    assertThat(second.getChangedFields()).containsExactly(CarWashHistoryField.NAME);
    assertThat(second.getSourceHistory(CarWashHistoryField.NAME)).isSameAs(second);
    assertThat(second.getSourceHistory(CarWashHistoryField.HIGH_PRESSURE_WATER_PRICE)).isSameAs(first);
    assertThat(second.getSourceHistory(CarWashHistoryField.LATITUDE)).isSameAs(base);
    assertThat(second.getSourceHistory(CarWashHistoryField.LATITUDE).getObservedAt()).isEqualTo(SUBMITTED_AT.minusDays(100));
  }

  @Test
  void explicitlyClearingNullableInformationIsAChange() {
    CarWashHistory base = registration();
    CarWashHistory next = modification(base).address(null).highPressureWaterPrice(null).build();

    assertThat(next.getChangedFields()).containsExactly(CarWashHistoryField.ADDRESS, CarWashHistoryField.HIGH_PRESSURE_WATER_PRICE);
    assertThat(next.getAddress()).isNull();
    assertThat(next.getHighPressureWaterPrice()).isNull();
    assertThat(next.getSourceHistory(CarWashHistoryField.ADDRESS)).isSameAs(next);
  }

  @Test
  void metadataAloneCannotCreateAModification() {
    assertThatThrownBy(() -> modification(registration()).build()).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void changedFieldsCannotBeModifiedByTheCaller() {
    CarWashHistory next = modification(registration()).name("새 이름").build();

    assertThatThrownBy(() -> next.getChangedFields().clear()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void registrationCannotReferenceABaseHistory() {
    CarWashHistory base = registration();

    assertThatThrownBy(() -> modification(base).type(CarWashHistoryType.REGISTRATION).name("새 이름").build())
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void modificationRequiresABaseHistory() {
    assertThatThrownBy(() -> modification(registration()).baseHistory(null).name("새 이름").build())
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void modificationCannotReferenceAnotherCarWash() {
    CarWash other = CarWash.create("다른 세차장", 37.5, 127.0, SUBMITTED_AT);

    assertThatThrownBy(() -> modification(registration()).carWash(other).name("새 이름").build())
            .isInstanceOf(IllegalArgumentException.class);
  }

  private CarWashHistory registration() {
    return CarWashHistory.builder()
                         .carWash(carWash)
                         .memberId(1L)
                         .type(CarWashHistoryType.REGISTRATION)
                         .visitExperience(VisitExperience.NOT_USED)
                         .observedAt(SUBMITTED_AT.minusDays(100))
                         .submittedAt(SUBMITTED_AT)
                         .name("세차생활")
                         .address("서울시")
                         .latitude(37.5)
                         .longitude(127.0)
                         .highPressureWaterPrice(3000)
                         .build();
  }

  private CarWashHistory.CarWashHistoryBuilder modification(CarWashHistory base) {
    return CarWashHistory.builder()
                         .carWash(carWash)
                         .baseHistory(base)
                         .memberId(2L)
                         .type(CarWashHistoryType.MODIFICATION)
                         .visitExperience(VisitExperience.USED)
                         .observedAt(SUBMITTED_AT)
                         .submittedAt(SUBMITTED_AT)
                         .name("세차생활")
                         .address("서울시")
                         .latitude(37.5)
                         .longitude(127.0)
                         .highPressureWaterPrice(3000);
  }
}
