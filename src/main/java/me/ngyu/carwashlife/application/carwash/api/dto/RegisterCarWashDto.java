package me.ngyu.carwashlife.application.carwash.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import me.ngyu.carwashlife.application.carwash.domain.VisitExperience;

public class RegisterCarWashDto {

  private RegisterCarWashDto() {
  }

  public record Request(@NotNull VisitExperience visitExperience,
                        @NotNull OffsetDateTime observedAt,
                        @NotNull @Valid CarWashInformationDto.Request information,
                        @Size(max = 5) List<@NotNull @Positive Long> photoIds) {

  }

  public record Response(Long carWashId,
                         Long historyId,
                         int confidence,
                         OffsetDateTime submittedAt) {

  }
}
