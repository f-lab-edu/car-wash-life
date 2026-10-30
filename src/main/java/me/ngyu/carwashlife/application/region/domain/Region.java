package me.ngyu.carwashlife.application.region.domain;

import java.util.Arrays;
import java.util.Objects;
import me.ngyu.carwashlife.application.carwash.domain.Coordinates;
import me.ngyu.carwashlife.application.carwash.domain.MapBounds;

public record Region(String regionId, RegionLevel level, String name, String parentName, MapBounds bounds) {

  public Region {
    if (regionId == null || regionId.isBlank() || name == null || name.isBlank() || parentName == null) {
      throw new IllegalArgumentException("지역 식별자와 이름은 필수입니다.");
    }
    Objects.requireNonNull(level, "지역 단계는 필수입니다.");
    Objects.requireNonNull(bounds, "지역 범위는 필수입니다.");
  }

  public String fullName() {
    return parentName.isEmpty() ? name : parentName + " " + name;
  }

  public boolean matches(String keyword) {
    // 중복 제거한 세종시도 원본의 짧은 이름과 정식 이름 모두로 찾을 수 있다.
    String searchableName = fullName();
    return Arrays.stream(keyword.replace("세종시", "세종특별자치시").split("(?U)\\s+"))
                 .allMatch(searchableName::contains);
  }

  /**
   * 지도 표시용 범위 중앙이며 실제 지역 폴리곤 내부의 점을 보장하지 않는다.
   */
  public Coordinates center() {
    return new Coordinates((bounds.southwest().getLatitude() + bounds.northeast().getLatitude()) / 2,
                           (bounds.southwest().getLongitude() + bounds.northeast().getLongitude()) / 2);
  }
}
