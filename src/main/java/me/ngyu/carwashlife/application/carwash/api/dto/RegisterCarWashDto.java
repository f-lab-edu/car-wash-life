package me.ngyu.carwashlife.application.carwash.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import me.ngyu.carwashlife.application.carwash.domain.VisitExperience;

public class RegisterCarWashDto {

  private RegisterCarWashDto() {
  }

  public record Request(@NotNull VisitExperience visitExperience,
                        @NotNull OffsetDateTime observedAt,
                        @NotNull @Valid CarWashInformationDto.Request information) {

  }

  public record Response(Long carWashId,
                         Long historyId,
                         int confidence,
                         OffsetDateTime submittedAt) {

  }
}
