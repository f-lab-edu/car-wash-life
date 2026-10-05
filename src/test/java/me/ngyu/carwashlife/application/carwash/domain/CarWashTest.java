package me.ngyu.carwashlife.application.carwash.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CarWashTest {

  private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-10-05T10:00:00+09:00");

  @Test
  void newCarWashIsNotOperatorManagedAndHasNoRepresentativeHistory() {
    CarWash carWash = CarWash.create("세차생활", 37.5, 127.0, CREATED_AT);

    assertThat(carWash.isOperatorManaged()).isFalse();
    assertThat(carWash.getTargetHistory()).isNull();
    assertThat(carWash.getCreatedAt()).isEqualTo(CREATED_AT);
    assertThat(carWash.getUpdatedAt()).isEqualTo(CREATED_AT);
  }

  @Test
  void selectingHistoryUpdatesRepresentativeNameAndCoordinatesTogether() {
    CarWash carWash = CarWash.create("기존 이름", 37.5, 127.0, CREATED_AT);
    CarWashHistory history = CarWashHistory.builder()
        .carWash(carWash)
        .memberId(1L)
        .type(CarWashHistoryType.REGISTRATION)
        .visitExperience(VisitExperience.NOT_USED)
        .observedAt(CREATED_AT)
        .submittedAt(CREATED_AT)
        .name("변경된 이름")
        .latitude(37.6)
        .longitude(127.1)
        .build();
    OffsetDateTime selectedAt = CREATED_AT.plusMinutes(1);

    carWash.selectRepresentativeHistory(history, selectedAt);

    assertThat(carWash.getTargetHistory()).isSameAs(history);
    assertThat(carWash.getName()).isEqualTo("변경된 이름");
    assertThat(carWash.getLatitude()).isEqualTo(37.6);
    assertThat(carWash.getLongitude()).isEqualTo(127.1);
    assertThat(carWash.getUpdatedAt()).isEqualTo(selectedAt);
    assertThat(carWash.getCreatedAt()).isEqualTo(CREATED_AT);
  }

  @Test
  void selectingAnotherCarWashHistoryLeavesRepresentativeInformationUnchanged() {
    CarWash carWash = CarWash.create("세차생활", 37.5, 127.0, CREATED_AT);
    CarWash otherCarWash = CarWash.create("다른 세차장", 37.6, 127.1, CREATED_AT);
    CarWashHistory otherHistory = historyOf(otherCarWash);

    assertThatThrownBy(() -> carWash.selectRepresentativeHistory(otherHistory, CREATED_AT))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(carWash.getTargetHistory()).isNull();
    assertThat(carWash.getName()).isEqualTo("세차생활");
    assertThat(carWash.getLatitude()).isEqualTo(37.5);
    assertThat(carWash.getLongitude()).isEqualTo(127.0);
    assertThat(carWash.getUpdatedAt()).isEqualTo(CREATED_AT);
  }

  @Test
  void selectingTheSameHistoryDoesNotChangeUpdatedAt() {
    CarWash carWash = CarWash.create("세차생활", 37.5, 127.0, CREATED_AT);
    CarWashHistory history = historyOf(carWash);
    carWash.selectRepresentativeHistory(history, CREATED_AT);

    carWash.selectRepresentativeHistory(history, CREATED_AT.plusHours(1));

    assertThat(carWash.getUpdatedAt()).isEqualTo(CREATED_AT);
  }

  @Test
  void selectingAHistoryDoesNotMutateThePreviousHistory() {
    CarWash carWash = CarWash.create("세차생활", 37.5, 127.0, CREATED_AT);
    CarWashHistory previousHistory = historyOf(carWash);
    carWash.selectRepresentativeHistory(previousHistory, CREATED_AT);
    CarWashHistory newHistory = CarWashHistory.builder()
        .carWash(carWash)
        .memberId(2L)
        .type(CarWashHistoryType.MODIFICATION)
        .visitExperience(VisitExperience.USED)
        .observedAt(CREATED_AT.plusHours(1))
        .submittedAt(CREATED_AT.plusHours(1))
        .name("수정한 이름")
        .latitude(37.6)
        .longitude(127.1)
        .build();

    carWash.selectRepresentativeHistory(newHistory, CREATED_AT.plusHours(1));

    assertThat(carWash.getTargetHistory()).isSameAs(newHistory);
    assertThat(previousHistory.getName()).isEqualTo("세차생활");
    assertThat(previousHistory.getLatitude()).isEqualTo(37.5);
    assertThat(previousHistory.getLongitude()).isEqualTo(127.0);
    assertThat(previousHistory.getVisitExperience()).isEqualTo(VisitExperience.NOT_USED);
  }

  @Test
  void selectingHistoryBeforeRegistrationIsRejected() {
    CarWash carWash = CarWash.create("세차생활", 37.5, 127.0, CREATED_AT);

    assertThatThrownBy(() -> carWash.selectRepresentativeHistory(
        historyOf(carWash), CREATED_AT.minusSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t"})
  void blankNameIsRejected(String name) {
    assertThatThrownBy(() -> CarWash.create(name, 37.5, 127.0, CREATED_AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(doubles = {-90.1, 90.1, Double.NaN, Double.POSITIVE_INFINITY})
  void invalidLatitudeIsRejected(double latitude) {
    assertThatThrownBy(() -> CarWash.create("세차생활", latitude, 127.0, CREATED_AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(doubles = {-180.1, 180.1, Double.NaN, Double.NEGATIVE_INFINITY})
  void invalidLongitudeIsRejected(double longitude) {
    assertThatThrownBy(() -> CarWash.create("세차생활", 37.5, longitude, CREATED_AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private CarWashHistory historyOf(CarWash carWash) {
    return CarWashHistory.builder()
        .carWash(carWash)
        .memberId(1L)
        .type(CarWashHistoryType.REGISTRATION)
        .visitExperience(VisitExperience.NOT_USED)
        .observedAt(CREATED_AT)
        .submittedAt(CREATED_AT)
        .name("세차생활")
        .latitude(37.5)
        .longitude(127.0)
        .build();
  }
}
