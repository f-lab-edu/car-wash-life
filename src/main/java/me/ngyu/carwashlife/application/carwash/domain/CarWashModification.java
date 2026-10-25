package me.ngyu.carwashlife.application.carwash.domain;

import java.time.OffsetDateTime;
import java.util.List;

public record CarWashModification(Long baseHistoryId,
                                  VisitExperience visitExperience,
                                  OffsetDateTime observedAt,
                                  CarWashInformation information,
                                  List<Long> photoIds) {

  public CarWashModification {
    photoIds = photoIds == null ? List.of() : List.copyOf(photoIds);
  }
}
