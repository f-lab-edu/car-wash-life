package me.ngyu.carwashlife.application.carwash.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import me.ngyu.carwashlife.application.carwash.domain.CarWashSearch;
import me.ngyu.carwashlife.application.carwash.domain.Coordinates;
import me.ngyu.carwashlife.common.exception.ApplicationException;
import me.ngyu.carwashlife.common.exception.ErrorCode;

public class CarWashSearchDto {

  private CarWashSearchDto() {
  }

  public record Request(@NotBlank @Size(max = 100) @Pattern(regexp = "[^\\x00]*") String keyword,
                        @DecimalMin("-90") @DecimalMax("90") Double currentLatitude,
                        @DecimalMin("-180") @DecimalMax("180") Double currentLongitude,
                        @Min(0) Integer page,
                        @Min(1) @Max(CarWashSearch.MAX_PAGE_SIZE) Integer size) {

    public Request {
      keyword = keyword == null ? null : keyword.strip();
      page = page == null ? 0 : page;
      size = size == null ? CarWashSearch.DEFAULT_PAGE_SIZE : size;
    }

    public CarWashSearch toDomain() {
      return new CarWashSearch(keyword, toCurrentCoordinates(), page, size);
    }

    // JPA 조회 시작 위치는 int 범위여야 하므로 곱셈은 long으로 계산한다.
    @AssertTrue
    public boolean isOffsetWithinRange() {
      return (long) page * size <= Integer.MAX_VALUE;
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
