package me.ngyu.carwashlife.application.carwash.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.Coordinates;
import me.ngyu.carwashlife.application.carwash.domain.MapBounds;
import me.ngyu.carwashlife.common.exception.ApplicationException;
import me.ngyu.carwashlife.common.exception.ErrorCode;

public class CarWashExplorationDto {

  private CarWashExplorationDto() {
  }

  public record Request(@NotNull Double southwestLatitude,
                        @NotNull Double southwestLongitude,
                        @NotNull Double northeastLatitude,
                        @NotNull Double northeastLongitude) {

    public MapBounds toDomain() {
      try {
        return new MapBounds(new Coordinates(southwestLatitude, southwestLongitude), new Coordinates(northeastLatitude, northeastLongitude));
      } catch (IllegalArgumentException exception) {
        throw new ApplicationException(ErrorCode.VALIDATION_ERROR, exception);
      }
    }
  }

  public record Response(List<CarWashSummary> carWashes, boolean hasMore) {

    public Response {
      carWashes = List.copyOf(carWashes);
    }
  }

  public record CarWashSummary(Long carWashId, String name, String address, double latitude, double longitude) {

    public static CarWashSummary from(CarWash carWash) {
      return new CarWashSummary(carWash.getId(), carWash.getName(), carWash.getTargetHistory().getAddress(), carWash.getLatitude(), carWash.getLongitude());
    }
  }
}
