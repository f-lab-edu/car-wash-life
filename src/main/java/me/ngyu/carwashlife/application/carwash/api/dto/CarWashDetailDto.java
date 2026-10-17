package me.ngyu.carwashlife.application.carwash.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory.Fields;
import me.ngyu.carwashlife.application.carwash.domain.CarWashRepresentativePolicy;
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
                         Set<Fields> changedFields,
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

    public static Response from(CarWash carWash,
                                CarWashRepresentativePolicy.Selection selection,
                                List<CarWashPhotoDto.Snapshot> photos) {
      CarWashHistory history = selection.history();
      return new Response(carWash.getId(), history.getId(), selection.confidence(), carWash.isOperatorManaged(),
                          history.getBaseHistory() == null ? null : history.getBaseHistory().getId(), history.getChangedFields(),
                          history.getVisitExperience(), history.getObservedAt(), history.getSubmittedAt(),
                          history.getName(), history.getAddress(), history.getLatitude(), history.getLongitude(),
                          history.getHighPressureWaterPrice(), history.getFoamLanceAvailability(), history.getFoamGunPrice(),
                          history.getAirGunAvailability(), history.getAirGunPrice(), history.getVacuumAvailability(), history.getVacuumPrice(),
                          history.getWashBayCount(), history.getDryingBayCount(), photos);
    }
  }
}
