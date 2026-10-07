package me.ngyu.carwashlife.application.carwash.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CarWashConfidencePolicyTest {

  private final CarWashConfidencePolicy policy = new CarWashConfidencePolicy();
  private final OffsetDateTime evaluatedAt = OffsetDateTime.parse("2026-10-07T12:00:00+09:00");

  @ParameterizedTest
  @CsvSource({
      "NOT_USED, 91, false, 1", "NOT_USED, 90, false, 2",
      "NOT_USED, 91, true, 2", "NOT_USED, 90, true, 3",
      "USED, 91, false, 3", "USED, 90, false, 4",
      "USED, 91, true, 4", "USED, 90, true, 5"
  })
  void scoresExperienceRecencyAndEvidencePhoto(
      VisitExperience experience, int daysAgo, boolean hasPhoto, int expectedConfidence
  ) {
    int confidence = policy.evaluate(
        experience, evaluatedAt.minusDays(daysAgo), hasPhoto, evaluatedAt);

    assertThat(confidence).isEqualTo(expectedConfidence);
  }

  @Test
  void anObservationAtEvaluationTimeIsRecent() {
    assertThat(policy.evaluate(VisitExperience.USED, evaluatedAt, false, evaluatedAt))
        .isEqualTo(4);
  }

  @Test
  void anObservationJustOutsideNinetyDaysLosesRecencyPoint() {
    OffsetDateTime observedAt = evaluatedAt.minusDays(90).minusNanos(1);

    assertThat(policy.evaluate(VisitExperience.USED, observedAt, false, evaluatedAt))
        .isEqualTo(3);
  }

  @Test
  void differentOffsetsForTheSameInstantHaveTheSameScore() {
    OffsetDateTime observedAt = evaluatedAt.minusDays(90)
        .withOffsetSameInstant(ZoneOffset.ofHours(-7));

    assertThat(policy.evaluate(VisitExperience.USED, observedAt, false, evaluatedAt))
        .isEqualTo(4);
  }

  @Test
  void aFutureObservationIsRejected() {
    assertThatThrownBy(() -> policy.evaluate(
        VisitExperience.USED, evaluatedAt.plusNanos(1), false, evaluatedAt))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
