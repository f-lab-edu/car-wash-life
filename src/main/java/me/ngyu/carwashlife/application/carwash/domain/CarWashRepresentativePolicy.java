package me.ngyu.carwashlife.application.carwash.domain;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CarWashRepresentativePolicy {

  private final CarWashConfidencePolicy confidencePolicy;

  public Selection select(List<CarWashHistory> histories, OffsetDateTime evaluatedAt) {
    if (histories.stream().anyMatch(history -> history.getId() == null)) {
      throw new IllegalArgumentException("대표 정보는 저장된 이력에서 선택합니다.");
    }
    return histories.stream()
                    .map(history -> new Selection(history, confidencePolicy.evaluate(
                            history.getVisitExperience(), history.getObservedAt(), false, evaluatedAt)))
                    .max(Comparator.comparingInt(Selection::confidence)
                                   .thenComparing(selection -> selection.history().getObservedAt().toInstant())
                                   .thenComparing(selection -> selection.history().getSubmittedAt().toInstant())
                                   .thenComparing(selection -> selection.history().getId()))
                    .orElseThrow(() -> new IllegalArgumentException("대표 정보를 선택할 이력이 없습니다."));
  }

  public record Selection(CarWashHistory history, int confidence) {

  }
}
