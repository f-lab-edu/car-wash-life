package me.ngyu.carwashlife.application.carwash.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import me.ngyu.carwashlife.application.carwash.domain.FacilityAvailability;
import me.ngyu.carwashlife.application.carwash.domain.VisitExperience;

public class RegisterCarWashDto {

  private RegisterCarWashDto() {
  }

  public record Request(@NotBlank @Size(max = 255) String name,
                        @Size(max = 255) String address,
                        @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
                        @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
                        @NotNull VisitExperience visitExperience,
                        @NotNull OffsetDateTime observedAt,
                        @PositiveOrZero Integer highPressureWaterPrice,
                        FacilityAvailability foamLanceAvailability,
                        @PositiveOrZero Integer foamGunPrice,
                        FacilityAvailability airGunAvailability,
                        @PositiveOrZero Integer airGunPrice,
                        FacilityAvailability vacuumAvailability,
                        @PositiveOrZero Integer vacuumPrice,
                        @PositiveOrZero Integer washBayCount,
                        @PositiveOrZero Integer dryingBayCount) {

  }

  public record Response(Long carWashId,
                         Long historyId,
                         int confidence,
                         OffsetDateTime submittedAt) {

  }
}
