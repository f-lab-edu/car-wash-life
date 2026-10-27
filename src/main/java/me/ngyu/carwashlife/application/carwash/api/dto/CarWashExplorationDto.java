package me.ngyu.carwashlife.application.carwash.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import me.ngyu.carwashlife.application.carwash.domain.CarWashExploration;
import me.ngyu.carwashlife.application.carwash.domain.CarWashExplorationResult;
import me.ngyu.carwashlife.application.carwash.domain.Coordinates;
import me.ngyu.carwashlife.application.carwash.domain.MapBounds;
import me.ngyu.carwashlife.common.exception.ApplicationException;
import me.ngyu.carwashlife.common.exception.ErrorCode;

public class CarWashExplorationDto {

  private CarWashExplorationDto() {
  }

  public record Request(@NotNull @DecimalMin("-90") @DecimalMax("90") Double southwestLatitude,
                        @NotNull @DecimalMin("-180") @DecimalMax("180") Double southwestLongitude,
                        @NotNull @DecimalMin("-90") @DecimalMax("90") Double northeastLatitude,
                        @NotNull @DecimalMin("-180") @DecimalMax("180") Double northeastLongitude,
                        @DecimalMin("-90") @DecimalMax("90") Double currentLatitude,
                        @DecimalMin("-180") @DecimalMax("180") Double currentLongitude) {

    /**
     * 지도 범위와 선택한 현재 위치를 하나의 탐색 조건으로 변환한다.
     */
    public CarWashExploration toDomain() {
      try {
        MapBounds bounds = new MapBounds(new Coordinates(southwestLatitude, southwestLongitude), new Coordinates(northeastLatitude, northeastLongitude));
        return new CarWashExploration(bounds, toCurrentCoordinates());
      } catch (IllegalArgumentException exception) {
        throw new ApplicationException(ErrorCode.VALIDATION_ERROR, exception);
      }
    }

    /**
     * 현재 위치를 생략하면 null을 반환하고 입력한 좌표는 함께 검증한다.
     */
    private Coordinates toCurrentCoordinates() {
      return currentLatitude == null && currentLongitude == null ? null : new Coordinates(currentLatitude, currentLongitude);
    }
  }

  public record Response(List<CarWashSummary> carWashes, boolean hasMore) {

    public Response {
      carWashes = List.copyOf(carWashes);
    }
  }

  public record CarWashSummary(Long carWashId,
                               String name,
                               String address,
                               double latitude,
                               double longitude,
                               long reportCount,
                               OffsetDateTime latestSubmittedAt,
                               OffsetDateTime createdAt,
                               Double distanceMeters) {

    public static CarWashSummary from(CarWashExplorationResult result) {
      return new CarWashSummary(result.carWashId(), result.name(), result.address(), result.latitude(), result.longitude(),
                                result.reportCount(), result.latestSubmittedAt(), result.createdAt(), result.distanceMeters());
    }
  }
}
