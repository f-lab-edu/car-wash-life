package me.ngyu.carwashlife.application.carwash.domain;

import java.time.OffsetDateTime;

public record CarWashExplorationResult(Long carWashId,
                                       String name,
                                       String address,
                                       double latitude,
                                       double longitude,
                                       long reportCount,
                                       OffsetDateTime latestSubmittedAt,
                                       OffsetDateTime createdAt) {
}
