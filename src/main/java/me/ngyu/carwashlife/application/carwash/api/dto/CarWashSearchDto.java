package me.ngyu.carwashlife.application.carwash.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import me.ngyu.carwashlife.application.carwash.domain.CarWashSearch;
import me.ngyu.carwashlife.application.carwash.domain.Coordinates;
import me.ngyu.carwashlife.common.exception.ApplicationException;
import me.ngyu.carwashlife.common.exception.ErrorCode;

public class CarWashSearchDto {

  private CarWashSearchDto() {
  }

  public record Request(@NotBlank @Size(max = 100) String keyword,
                        @DecimalMin("-90") @DecimalMax("90") Double currentLatitude,
                        @DecimalMin("-180") @DecimalMax("180") Double currentLongitude) {

    public Request {
      keyword = keyword == null ? null : keyword.strip();
    }

    public CarWashSearch toDomain() {
      return new CarWashSearch(keyword, toCurrentCoordinates());
    }

    private Coordinates toCurrentCoordinates() {
      if (currentLatitude == null && currentLongitude == null) {
        return null;
      }
      try {
        return new Coordinates(currentLatitude, currentLongitude);
      } catch (IllegalArgumentException exception) {
        throw new ApplicationException(ErrorCode.VALIDATION_ERROR, exception);
      }
    }
  }

  public record Response(List<CarWashExplorationDto.CarWashSummary> carWashes, boolean hasMore) {

    public Response {
      carWashes = List.copyOf(carWashes);
    }
  }
}
