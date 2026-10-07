package me.ngyu.carwashlife.application.carwash.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CarWashRepresentativePolicyTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-07T12:00:00+09:00");
  private final CarWashRepresentativePolicy policy = new CarWashRepresentativePolicy(new CarWashConfidencePolicy());
  private final CarWash carWash = CarWash.create("세차생활", 37.5, 127.0, NOW.minusDays(200));

  @Test
  void highestScoreWinsEvenWhenALowerScoreWasObservedMoreRecently() {
    CarWashHistory used = history(1L, VisitExperience.USED, NOW.minusDays(100), NOW.minusDays(50));
    CarWashHistory recent = history(2L, VisitExperience.NOT_USED, NOW, NOW);

    CarWashRepresentativePolicy.Selection selected = policy.select(List.of(recent, used), NOW);

    assertThat(selected.history()).isSameAs(used);
    assertThat(selected.confidence()).isEqualTo(3);
  }

  @Test
  void equalScoresPreferTheLatestObservationBeforeSubmissionTimeOrId() {
    CarWashHistory older = history(10L, VisitExperience.USED, NOW.minusDays(2), NOW);
    CarWashHistory recent = history(1L, VisitExperience.USED, NOW.minusDays(1), NOW.minusDays(1));

    assertThat(policy.select(List.of(older, recent), NOW).history()).isSameAs(recent);
  }

  @Test
  void equalObservationsPreferTheLatestSubmissionBeforeId() {
    CarWashHistory older = history(10L, VisitExperience.USED, NOW.minusDays(2), NOW.minusDays(1));
    CarWashHistory newer = history(1L, VisitExperience.USED, NOW.minusDays(2), NOW);

    assertThat(policy.select(List.of(older, newer), NOW).history()).isSameAs(newer);
  }

  @Test
  void equalScoresAndTimesPreferTheLargestIdIndependentlyOfInputOrder() {
    CarWashHistory first = history(1L, VisitExperience.USED, NOW, NOW);
    CarWashHistory second = history(2L, VisitExperience.USED, NOW, NOW);

    assertThat(policy.select(List.of(second, first), NOW).history()).isSameAs(second);
    assertThat(policy.select(List.of(first, second), NOW).history()).isSameAs(second);
  }

  @Test
  void differentOffsetsForTheSameInstantUseIdToBreakTheTie() {
    CarWashHistory first = history(1L, VisitExperience.USED, NOW, NOW);
    CarWashHistory second = history(2L, VisitExperience.USED, NOW.withOffsetSameInstant(ZoneOffset.ofHours(-7)),
                                    NOW.withOffsetSameInstant(ZoneOffset.UTC));

    assertThat(policy.select(List.of(first, second), NOW).history()).isSameAs(second);
  }

  @Test
  void reevaluationAfterNinetyDaysKeepsTheWinnerButReducesItsConfidence() {
    CarWashHistory recent = history(2L, VisitExperience.USED, NOW, NOW);
    CarWashHistory older = history(1L, VisitExperience.USED, NOW.minusDays(10), NOW);

    assertThat(policy.select(List.of(recent, older), NOW).confidence()).isEqualTo(4);
    CarWashRepresentativePolicy.Selection later = policy.select(List.of(recent, older), NOW.plusDays(91));
    assertThat(later.history()).isSameAs(recent);
    assertThat(later.confidence()).isEqualTo(3);
  }

  @Test
  void unsavedHistoriesCannotBeSelected() {
    CarWashHistory unsaved = history(null, VisitExperience.USED, NOW, NOW);

    assertThatThrownBy(() -> policy.select(List.of(unsaved), NOW)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anEmptyCandidateListCannotBeSelected() {
    assertThatThrownBy(() -> policy.select(List.of(), NOW)).isInstanceOf(IllegalArgumentException.class);
  }

  private CarWashHistory history(Long id, VisitExperience experience, OffsetDateTime observedAt, OffsetDateTime submittedAt) {
    CarWashHistory history = CarWashHistory.builder().carWash(carWash).memberId(1L)
                                           .type(CarWashHistoryType.REGISTRATION).visitExperience(experience)
                                           .observedAt(observedAt).submittedAt(submittedAt)
                                           .name("세차생활").latitude(37.5).longitude(127.0).build();
    ReflectionTestUtils.setField(history, "id", id);
    return history;
  }
}
