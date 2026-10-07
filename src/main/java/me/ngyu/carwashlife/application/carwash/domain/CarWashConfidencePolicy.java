package me.ngyu.carwashlife.application.carwash.domain;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public class CarWashConfidencePolicy {

  public int evaluate(
      VisitExperience visitExperience,
      OffsetDateTime observedAt,
      boolean hasEvidencePhoto,
      OffsetDateTime evaluatedAt
  ) {
    Objects.requireNonNull(visitExperience, "경험 유형은 필수입니다.");
    Objects.requireNonNull(observedAt, "관찰 시점은 필수입니다.");
    Objects.requireNonNull(evaluatedAt, "평가 시점은 필수입니다.");
    if (observedAt.toInstant().isAfter(evaluatedAt.toInstant())) {
      throw new IllegalArgumentException("관찰 시점은 평가 시점보다 늦을 수 없습니다.");
    }

    int confidence = 1;
    if (visitExperience == VisitExperience.USED) {
      confidence += 2;
    }
    if (!observedAt.toInstant().isBefore(evaluatedAt.toInstant().minus(90, ChronoUnit.DAYS))) {
      confidence++;
    }
    if (hasEvidencePhoto) {
      confidence++;
    }
    return confidence;
  }
}
