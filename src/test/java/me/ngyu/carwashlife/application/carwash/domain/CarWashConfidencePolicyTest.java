package me.ngyu.carwashlife.application.carwash.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CarWashConfidencePolicyTest {

  private final CarWashConfidencePolicy policy = new CarWashConfidencePolicy();
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
    int confidence = policy.evaluate(
            experience, evaluatedAt.minusDays(daysAgo), hasPhoto, evaluatedAt);

    assertThat(confidence).isEqualTo(expectedConfidence);
  }

  @DisplayName("관찰 시점과 평가 시점이 같으면 최근성 점수를 가산한다.")
  @Test
  void anObservationAtEvaluationTimeIsRecent() {
    assertThat(policy.evaluate(VisitExperience.USED, evaluatedAt, false, evaluatedAt))
            .isEqualTo(4);
  }

  @DisplayName("관찰 시점이 90일 경계를 조금이라도 벗어나면 최근성 점수를 가산하지 않는다.")
  @Test
  void anObservationJustOutsideNinetyDaysLosesRecencyPoint() {
    OffsetDateTime observedAt = evaluatedAt.minusDays(90).minusNanos(1);

    assertThat(policy.evaluate(VisitExperience.USED, observedAt, false, evaluatedAt))
            .isEqualTo(3);
  }

  @DisplayName("시간대 표기가 달라도 같은 관찰 시각이면 동일한 신뢰도 점수를 계산한다.")
  @Test
  void differentOffsetsForTheSameInstantHaveTheSameScore() {
    OffsetDateTime observedAt = evaluatedAt.minusDays(90)
                                           .withOffsetSameInstant(ZoneOffset.ofHours(-7));

    assertThat(policy.evaluate(VisitExperience.USED, observedAt, false, evaluatedAt))
            .isEqualTo(4);
  }

  @DisplayName("관찰 시점이 평가 시점보다 미래이면 신뢰도 평가를 거절한다.")
  @Test
  void aFutureObservationIsRejected() {
    assertThatThrownBy(() -> policy.evaluate(
            VisitExperience.USED, evaluatedAt.plusNanos(1), false, evaluatedAt))
            .isInstanceOf(IllegalArgumentException.class);
  }
}
