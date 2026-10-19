package me.ngyu.carwashlife.application.carwash.api.dto;

import java.time.OffsetDateTime;
import java.util.Set;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory.Fields;
import me.ngyu.carwashlife.application.carwash.domain.FacilityAvailability;
import me.ngyu.carwashlife.application.carwash.domain.VisitExperience;

public class CarWashDetailDto {

  private CarWashDetailDto() {
  }

  /**
   * 선택된 대표 제보의 전체 스냅샷을 반환한다. 미확인 항목은 과거 이력의 값으로 채우지 않는다.
   *
   * @param confidence 조회 시점에 평가한 대표 제보의 점수. 항목별 정확성이나 모든 항목의 확인 여부를 보증하지 않는다.
   * @param changedFields 기준 이력과 값이 달라진 항목. 이번에 재확인한 항목의 목록이 아니다.
   * @param observedAt 대표 제보에서 입력한 정보를 확인했다고 제보한 시점
   */
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
                         Integer dryingBayCount) {

    public static Response from(CarWash carWash,
                                int confidence) {
      CarWashHistory history = carWash.getTargetHistory();
      return new Response(carWash.getId(), history.getId(), confidence, carWash.isOperatorManaged(),
                          history.getBaseHistory() == null ? null : history.getBaseHistory().getId(), history.getChangedFields(),
                          history.getVisitExperience(), history.getObservedAt(), history.getSubmittedAt(),
                          history.getName(), history.getAddress(), history.getLatitude(), history.getLongitude(),
                          history.getHighPressureWaterPrice(), history.getFoamLanceAvailability(), history.getFoamGunPrice(),
                          history.getAirGunAvailability(), history.getAirGunPrice(), history.getVacuumAvailability(), history.getVacuumPrice(),
                          history.getWashBayCount(), history.getDryingBayCount());
    }
  }
}
