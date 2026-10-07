package me.ngyu.carwashlife.application.carwash.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CarWashRepresentativePolicyTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-07T12:00:00+09:00");
  private final CarWashRepresentativePolicy policy = new CarWashRepresentativePolicy(new CarWashConfidencePolicy());
  private final CarWash carWash = CarWash.create("세차생활", 37.5, 127.0, NOW.minusDays(200));

  @DisplayName("관찰 시점이 더 늦은 이력이 있어도 신뢰도가 가장 높은 이력을 대표로 선택한다.")
  @Test
  void highestScoreWinsEvenWhenALowerScoreWasObservedMoreRecently() {
    CarWashHistory used = history(1L, VisitExperience.USED, NOW.minusDays(100), NOW.minusDays(50));
    CarWashHistory recent = history(2L, VisitExperience.NOT_USED, NOW, NOW);

    CarWashRepresentativePolicy.Selection selected = policy.select(List.of(recent, used), NOW);

    assertThat(selected.history()).isSameAs(used);
    assertThat(selected.confidence()).isEqualTo(3);
  }

  @DisplayName("신뢰도가 같으면 제출 시점과 식별자보다 최신 관찰 시점을 우선하여 대표 이력을 선택한다.")
  @Test
  void equalScoresPreferTheLatestObservationBeforeSubmissionTimeOrId() {
    CarWashHistory older = history(10L, VisitExperience.USED, NOW.minusDays(2), NOW);
    CarWashHistory recent = history(1L, VisitExperience.USED, NOW.minusDays(1), NOW.minusDays(1));

    assertThat(policy.select(List.of(older, recent), NOW).history()).isSameAs(recent);
  }

  @DisplayName("신뢰도와 관찰 시점이 같으면 식별자보다 최신 제출 시점을 우선하여 대표 이력을 선택한다.")
  @Test
  void equalObservationsPreferTheLatestSubmissionBeforeId() {
    CarWashHistory older = history(10L, VisitExperience.USED, NOW.minusDays(2), NOW.minusDays(1));
    CarWashHistory newer = history(1L, VisitExperience.USED, NOW.minusDays(2), NOW);

    assertThat(policy.select(List.of(older, newer), NOW).history()).isSameAs(newer);
  }

  @DisplayName("신뢰도와 관찰 및 제출 시점이 같으면 입력 순서와 관계없이 가장 큰 식별자의 이력을 선택한다.")
  @Test
  void equalScoresAndTimesPreferTheLargestIdIndependentlyOfInputOrder() {
    CarWashHistory first = history(1L, VisitExperience.USED, NOW, NOW);
    CarWashHistory second = history(2L, VisitExperience.USED, NOW, NOW);

    assertThat(policy.select(List.of(second, first), NOW).history()).isSameAs(second);
    assertThat(policy.select(List.of(first, second), NOW).history()).isSameAs(second);
  }

  @DisplayName("시간대 표기가 달라도 같은 관찰 및 제출 시각이면 식별자로 동점을 판정한다.")
  @Test
  void differentOffsetsForTheSameInstantUseIdToBreakTheTie() {
    CarWashHistory first = history(1L, VisitExperience.USED, NOW, NOW);
    CarWashHistory second = history(2L, VisitExperience.USED, NOW.withOffsetSameInstant(ZoneOffset.ofHours(-7)),
                                    NOW.withOffsetSameInstant(ZoneOffset.UTC));

    assertThat(policy.select(List.of(first, second), NOW).history()).isSameAs(second);
  }

  @DisplayName("사진 없는 같은 경험 유형의 이력을 90일이 지난 뒤 재평가하면 대표를 유지하고 최근성 점수만 낮춘다.")
  @Test
  void reevaluationAfterNinetyDaysKeepsTheWinnerButReducesItsConfidence() {
    CarWashHistory recent = history(2L, VisitExperience.USED, NOW, NOW);
    CarWashHistory older = history(1L, VisitExperience.USED, NOW.minusDays(10), NOW);

    assertThat(policy.select(List.of(recent, older), NOW).confidence()).isEqualTo(4);
    CarWashRepresentativePolicy.Selection later = policy.select(List.of(recent, older), NOW.plusDays(91));
    assertThat(later.history()).isSameAs(recent);
    assertThat(later.confidence()).isEqualTo(3);
  }

  @DisplayName("저장하지 않아 식별자가 없는 이력은 대표로 선택하지 않는다.")
  @Test
  void unsavedHistoriesCannotBeSelected() {
    CarWashHistory unsaved = history(null, VisitExperience.USED, NOW, NOW);

    assertThatThrownBy(() -> policy.select(List.of(unsaved), NOW)).isInstanceOf(IllegalArgumentException.class);
  }

  @DisplayName("후보 이력이 없으면 대표 이력 선택을 거절한다.")
  @Test
  void anEmptyCandidateListCannotBeSelected() {
    assertThatThrownBy(() -> policy.select(List.of(), NOW)).isInstanceOf(IllegalArgumentException.class);
  }

  @DisplayName("사진 근거 이력의 최근성이 만료되어 동점이 되면 최신 관찰 이력을 대표로 선택한다.")
  @Test
  void anOlderPhotoBackedHistoryCanLoseItsLeadAsItsRecencyExpires() {
    CarWashHistory oldPhoto = CarWashHistory.builder().carWash(carWash).memberId(1L)
                                            .type(CarWashHistoryType.REGISTRATION).visitExperience(VisitExperience.USED)
                                            .observedAt(NOW.minusDays(90)).submittedAt(NOW)
                                            .name("사진 근거 이력").latitude(37.5).longitude(127.0).newPhotoIds(List.of(10L)).build();
    ReflectionTestUtils.setField(oldPhoto, "id", 1L);
    CarWashHistory recent = history(2L, VisitExperience.USED, NOW.minusDays(1), NOW);

    assertThat(policy.select(List.of(oldPhoto, recent), NOW).history()).isSameAs(oldPhoto);
    CarWashRepresentativePolicy.Selection later = policy.select(List.of(oldPhoto, recent), NOW.plusDays(1));
    assertThat(later.history()).isSameAs(recent);
    assertThat(later.confidence()).isEqualTo(4);
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
