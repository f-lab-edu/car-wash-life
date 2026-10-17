package me.ngyu.carwashlife.application.carwash.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CarWashHistoryConfidenceTest {

  private final OffsetDateTime evaluatedAt = OffsetDateTime.parse("2026-10-07T12:00:00+09:00");

  @DisplayName("실제 이용 여부와 90일 이내 관찰 여부 및 근거 사진 유무에 따라 신뢰도 점수를 계산한다.")
  @ParameterizedTest(name = "[{index}] {displayName}")
  @CsvSource({
          "NOT_USED, 91, false, 1", "NOT_USED, 90, false, 2",
          "NOT_USED, 91, true, 2", "NOT_USED, 90, true, 3",
          "USED, 91, false, 3", "USED, 90, false, 4",
          "USED, 91, true, 4", "USED, 90, true, 5"
  })
  void scoresExperienceRecencyAndEvidencePhoto(VisitExperience experience, int daysAgo, boolean hasPhoto, int expectedConfidence) {
    CarWashHistory history = history(experience, evaluatedAt.minusDays(daysAgo), hasPhoto);

    int confidence = history.calculateConfidence(evaluatedAt);

    assertThat(confidence).isEqualTo(expectedConfidence);
  }

  @DisplayName("관찰 시점과 평가 시점이 같으면 최근성 점수를 가산한다.")
  @Test
  void anObservationAtEvaluationTimeIsRecent() {
    assertThat(history(VisitExperience.USED, evaluatedAt).calculateConfidence(evaluatedAt))
            .isEqualTo(4);
  }

  @DisplayName("관찰 시점이 90일 경계를 조금이라도 벗어나면 최근성 점수를 가산하지 않는다.")
  @Test
  void anObservationJustOutsideNinetyDaysLosesRecencyPoint() {
    OffsetDateTime observedAt = evaluatedAt.minusDays(90).minusNanos(1);

    assertThat(history(VisitExperience.USED, observedAt).calculateConfidence(evaluatedAt))
            .isEqualTo(3);
  }

  @DisplayName("시간대 표기가 달라도 같은 관찰 시각이면 동일한 신뢰도 점수를 계산한다.")
  @Test
  void differentOffsetsForTheSameInstantHaveTheSameScore() {
    OffsetDateTime observedAt = evaluatedAt.minusDays(90)
                                           .withOffsetSameInstant(ZoneOffset.ofHours(-7));

    assertThat(history(VisitExperience.USED, observedAt).calculateConfidence(evaluatedAt))
            .isEqualTo(4);
  }

  @DisplayName("같은 이력도 평가 시점에 따라 최근성 점수가 달라진다.")
  @Test
  void confidenceUsesTheGivenEvaluationTime() {
    CarWashHistory history = history(VisitExperience.USED, evaluatedAt.minusDays(90));

    assertThat(history.calculateConfidence(evaluatedAt)).isEqualTo(4);
    assertThat(history.calculateConfidence(evaluatedAt.plusNanos(1))).isEqualTo(3);
  }

  @DisplayName("평가 시점이 없으면 신뢰도 계산을 거절한다.")
  @Test
  void evaluationTimeIsRequired() {
    CarWashHistory history = history(VisitExperience.USED, evaluatedAt);

    assertThatThrownBy(() -> history.calculateConfidence(null)).isInstanceOf(NullPointerException.class);
  }

  private CarWashHistory history(VisitExperience experience,
                                 OffsetDateTime observedAt) {
    return history(experience, observedAt, false);
  }

  private CarWashHistory history(VisitExperience experience,
                                 OffsetDateTime observedAt,
                                 boolean hasPhoto) {
    Coordinates coordinates = new Coordinates(37.5, 127.0);
    CarWash carWash = CarWash.create("세차생활", coordinates, evaluatedAt);
    CarWashInformation information = CarWashInformation.builder().name("세차생활").coordinates(coordinates).build();
    return CarWashHistory.register(carWash, 1L, experience, observedAt, evaluatedAt, information, hasPhoto ? List.of(1L) : List.of());
  }
}
