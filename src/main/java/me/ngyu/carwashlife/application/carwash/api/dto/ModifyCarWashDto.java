package me.ngyu.carwashlife.application.carwash.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory.Fields;
import me.ngyu.carwashlife.application.carwash.domain.VisitExperience;

public class ModifyCarWashDto {

  private ModifyCarWashDto() {
  }

  /**
   * 조회 가능한 전체 스냅샷을 제출한다. 부분 수정 요청이 아니며, nullable 정보의 생략/null은
   * 해당 정보를 미확인으로 변경한다. 시설의 생략/null은 UNKNOWN으로 저장한다.
   * photoIds에는 이번 요청의 신규 사진만 제출한다. 생략/null/빈 목록이면 기준 이력의 사진을 유지한다.
   */
  public record Request(@NotNull @Positive Long baseHistoryId,
                        @NotNull VisitExperience visitExperience,
                        @NotNull OffsetDateTime observedAt,
                        @NotNull @Valid CarWashInformationDto.Request information,
                        @Size(max = 5) List<@NotNull @Positive Long> photoIds) {

  }

  public record Response(Long carWashId,
                         Long historyId,
                         Long baseHistoryId,
                         Set<Fields> changedFields,
                         int confidence,
                         OffsetDateTime submittedAt) {

  }
}
