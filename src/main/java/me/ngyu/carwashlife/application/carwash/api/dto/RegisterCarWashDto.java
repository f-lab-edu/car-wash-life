package me.ngyu.carwashlife.application.carwash.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import me.ngyu.carwashlife.application.carwash.domain.VisitExperience;

public class RegisterCarWashDto {

  private RegisterCarWashDto() {
  }

  /**
   * 사용자가 확인한 세차장 정보를 제출한다. 모르는 선택 항목은 생략하거나 null로 제출한다.
   *
   * @param observedAt 입력한 정보를 확인했다고 제보한 시점
   */
  public record Request(@NotNull VisitExperience visitExperience,
                        @NotNull OffsetDateTime observedAt,
                        @NotNull @Valid CarWashInformationDto.Request information) {

  }

  /**
   * @param confidence 제출 시점에 평가한 제보의 점수. 모든 항목의 확인 여부나 정확성을 보증하지 않는다.
   */
  public record Response(Long carWashId,
                         Long historyId,
                         int confidence,
                         OffsetDateTime submittedAt) {

  }
}
