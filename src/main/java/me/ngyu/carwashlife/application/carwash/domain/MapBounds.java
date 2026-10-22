package me.ngyu.carwashlife.application.carwash.domain;

import java.util.Objects;

public record MapBounds(Coordinates southwest, Coordinates northeast) {

  public MapBounds {
    Objects.requireNonNull(southwest, "남서쪽 좌표는 필수입니다.");
    Objects.requireNonNull(northeast, "북동쪽 좌표는 필수입니다.");
    if (southwest.getLatitude() > northeast.getLatitude() || southwest.getLongitude() > northeast.getLongitude()) {
      throw new IllegalArgumentException("남서쪽 좌표는 북동쪽 좌표보다 클 수 없습니다.");
    }
  }
}
