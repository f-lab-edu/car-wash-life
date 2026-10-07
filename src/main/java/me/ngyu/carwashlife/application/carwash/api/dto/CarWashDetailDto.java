package me.ngyu.carwashlife.application.carwash.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistoryField;
import me.ngyu.carwashlife.application.carwash.domain.FacilityAvailability;
import me.ngyu.carwashlife.application.carwash.domain.VisitExperience;

public class CarWashDetailDto {

  private CarWashDetailDto() {
  }

  public record Response(Long carWashId,
                         Long historyId,
                         int confidence,
                         boolean operatorManaged,
                         Long baseHistoryId,
                         Set<CarWashHistoryField> changedFields,
                         VisitExperience visitExperience,
                         OffsetDateTime observedAt,
                         OffsetDateTime submittedAt,
                         String name,
                         String address,
                         double latitude,
                         double longitude,
                         Integer highPressureWaterPrice,
                         FacilityAvailability foamLanceAvailability,
                         Integer foamGunPrice,
                         FacilityAvailability airGunAvailability,
                         Integer airGunPrice,
                         FacilityAvailability vacuumAvailability,
                         Integer vacuumPrice,
                         Integer washBayCount,
                         Integer dryingBayCount,
                         List<CarWashPhotoDto.Snapshot> photos) {

  }
}
