package me.ngyu.carwashlife.application.carwash.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import me.ngyu.carwashlife.application.carwash.domain.CarWashSearch;

public class CarWashSearchDto {

  private CarWashSearchDto() {
  }

  public record Request(@NotBlank @Size(max = 100) String keyword) {

    public Request {
      keyword = keyword == null ? null : keyword.strip();
    }

    public CarWashSearch toDomain() {
      return new CarWashSearch(keyword);
    }
  }

  public record Response(List<CarWashExplorationDto.CarWashSummary> carWashes, boolean hasMore) {

    public Response {
      carWashes = List.copyOf(carWashes);
    }
  }
}
