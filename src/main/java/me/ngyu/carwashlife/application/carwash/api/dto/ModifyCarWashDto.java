package me.ngyu.carwashlife.application.carwash.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory.Fields;
import me.ngyu.carwashlife.application.carwash.domain.CarWashModification;
import me.ngyu.carwashlife.application.carwash.domain.VisitExperience;

public class ModifyCarWashDto {

  private ModifyCarWashDto() {
  }

  /**
   * 수정 후의 전체 스냅샷을 제출한다. 확인한 값만 입력하고 모르는 선택 항목은 생략하거나 null로 제출한다.
   * 생략/null은 기존 값 유지가 아닌 미확인으로 처리하며, 시설 상태는 UNKNOWN으로 저장한다.
   * photoIds에는 이번 요청의 신규 사진만 제출한다. 생략/null/빈 목록이면 기준 이력의 사진을 유지한다.
   *
   * @param observedAt 입력한 정보를 확인했다고 제보한 시점
   */
  public record Request(@NotNull @Positive Long baseHistoryId,
                        @NotNull VisitExperience visitExperience,
                        @NotNull OffsetDateTime observedAt,
                        @NotNull @Valid CarWashInformationDto.Request information,
                        @Size(max = 5) List<@NotNull @Positive Long> photoIds) {

    /**
     * 수정 요청을 기준 제보와 수정할 정보를 포함한 도메인 값으로 변환한다.
     */
    public CarWashModification toDomain() {
      return new CarWashModification(baseHistoryId, visitExperience, observedAt, information.toDomain(), photoIds);
    }
  }

  /**
   * @param confidence 제출 시점에 평가한 제보의 점수. 모든 항목의 확인 여부나 정확성을 보증하지 않는다.
   */
  public record Response(Long carWashId,
                         Long historyId,
                         Long baseHistoryId,
                         Set<Fields> changedFields,
                         int confidence,
                         OffsetDateTime submittedAt) {

  }
}
