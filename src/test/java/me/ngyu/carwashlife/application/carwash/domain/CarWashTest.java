package me.ngyu.carwashlife.application.carwash.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CarWashTest {

  private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-10-05T10:00:00+09:00");

  @DisplayName("세차장을 생성하면 운영자 관리 여부를 거짓으로 두고 대표 이력 없이 등록 시점을 저장한다.")
  @Test
  void newCarWashIsNotOperatorManagedAndHasNoRepresentativeHistory() {
    CarWash carWash = CarWash.create("세차생활", new Coordinates(37.5, 127.0), CREATED_AT);

    assertThat(carWash.isOperatorManaged()).isFalse();
    assertThat(carWash.getTargetHistory()).isNull();
    assertThat(carWash.getCreatedAt()).isEqualTo(CREATED_AT);
    assertThat(carWash.getUpdatedAt()).isEqualTo(CREATED_AT);
  }

  @DisplayName("대표 이력을 선택하면 이름과 좌표 및 갱신 시점을 함께 반영한다.")
  @Test
  void selectingHistoryUpdatesRepresentativeNameAndCoordinatesTogether() {
    CarWash carWash = CarWash.create("기존 이름", new Coordinates(37.5, 127.0), CREATED_AT);
    CarWashHistory history = CarWashHistory.register(carWash, 1L, VisitExperience.NOT_USED, CREATED_AT, CREATED_AT,
                                                     CarWashInformation.builder()
                                                                       .name("변경된 이름")
                                                                       .coordinates(new Coordinates(37.6, 127.1))
                                                                       .build());
    OffsetDateTime selectedAt = CREATED_AT.plusMinutes(1);

    carWash.selectRepresentativeHistory(history, selectedAt);

    assertThat(carWash.getTargetHistory()).isSameAs(history);
    assertThat(carWash.getName()).isEqualTo("변경된 이름");
    assertThat(carWash.getLatitude()).isEqualTo(37.6);
    assertThat(carWash.getLongitude()).isEqualTo(127.1);
    assertThat(carWash.getUpdatedAt()).isEqualTo(selectedAt);
    assertThat(carWash.getCreatedAt()).isEqualTo(CREATED_AT);
  }

  @DisplayName("다른 세차장의 이력을 대표로 선택하려 하면 기존 원장 정보를 유지하고 거절한다.")
  @Test
  void selectingAnotherCarWashHistoryLeavesRepresentativeInformationUnchanged() {
    CarWash carWash = CarWash.create("세차생활", new Coordinates(37.5, 127.0), CREATED_AT);
    CarWash otherCarWash = CarWash.create("다른 세차장", new Coordinates(37.6, 127.1), CREATED_AT);
    CarWashHistory otherHistory = historyOf(otherCarWash);

    assertThatThrownBy(() -> carWash.selectRepresentativeHistory(otherHistory, CREATED_AT))
            .isInstanceOf(IllegalArgumentException.class);

    assertThat(carWash.getTargetHistory()).isNull();
    assertThat(carWash.getName()).isEqualTo("세차생활");
    assertThat(carWash.getLatitude()).isEqualTo(37.5);
    assertThat(carWash.getLongitude()).isEqualTo(127.0);
    assertThat(carWash.getUpdatedAt()).isEqualTo(CREATED_AT);
  }

  @DisplayName("같은 이력을 다시 대표로 선택하면 원장의 갱신 시점을 바꾸지 않는다.")
  @Test
  void selectingTheSameHistoryDoesNotChangeUpdatedAt() {
    CarWash carWash = CarWash.create("세차생활", new Coordinates(37.5, 127.0), CREATED_AT);
    CarWashHistory history = historyOf(carWash);
    carWash.selectRepresentativeHistory(history, CREATED_AT);

    carWash.selectRepresentativeHistory(history, CREATED_AT.plusHours(1));

    assertThat(carWash.getUpdatedAt()).isEqualTo(CREATED_AT);
  }

  @DisplayName("새 이력을 대표로 선택해도 이전 이력의 이름과 좌표 및 경험 유형을 보존한다.")
  @Test
  void selectingAHistoryDoesNotMutateThePreviousHistory() {
    CarWash carWash = CarWash.create("세차생활", new Coordinates(37.5, 127.0), CREATED_AT);
    CarWashHistory previousHistory = historyOf(carWash);
    carWash.selectRepresentativeHistory(previousHistory, CREATED_AT);
    CarWashHistory newHistory = CarWashHistory.modify(carWash, previousHistory, 2L, VisitExperience.USED, CREATED_AT.plusHours(1), CREATED_AT.plusHours(1),
                                                      CarWashInformation.builder()
                                                                        .name("수정한 이름")
                                                                        .coordinates(new Coordinates(37.6, 127.1))
                                                                        .build());

    carWash.selectRepresentativeHistory(newHistory, CREATED_AT.plusHours(1));

    assertThat(carWash.getTargetHistory()).isSameAs(newHistory);
    assertThat(previousHistory.getName()).isEqualTo("세차생활");
    assertThat(previousHistory.getLatitude()).isEqualTo(37.5);
    assertThat(previousHistory.getLongitude()).isEqualTo(127.0);
    assertThat(previousHistory.getVisitExperience()).isEqualTo(VisitExperience.NOT_USED);
  }

  @DisplayName("대표 정보 반영 시점이 최초 등록보다 빠르면 이력 선택을 거절한다.")
  @Test
  void selectingHistoryBeforeRegistrationIsRejected() {
    CarWash carWash = CarWash.create("세차생활", new Coordinates(37.5, 127.0), CREATED_AT);

    assertThatThrownBy(() -> carWash.selectRepresentativeHistory(
            historyOf(carWash), CREATED_AT.minusSeconds(1)))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @DisplayName("세차장 이름이 비어 있거나 공백뿐이면 생성을 거절한다.")
  @ParameterizedTest(name = "[{index}] {displayName}")
  @ValueSource(strings = {"", " ", "\t"})
  void blankNameIsRejected(String name) {
    assertThatThrownBy(() -> CarWash.create(name, new Coordinates(37.5, 127.0), CREATED_AT))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @DisplayName("좌표가 없으면 세차장 원장 생성을 거절한다.")
  @Test
  void missingCoordinatesAreRejected() {
    assertThatThrownBy(() -> CarWash.create("세차생활", null, CREATED_AT)).isInstanceOf(NullPointerException.class);
  }

  private CarWashHistory historyOf(CarWash carWash) {
    return CarWashHistory.register(carWash, 1L, VisitExperience.NOT_USED, CREATED_AT, CREATED_AT,
                                   CarWashInformation.builder()
                                                     .name("세차생활")
                                                     .coordinates(new Coordinates(37.5, 127.0))
                                                     .build());
  }
}
