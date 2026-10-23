package me.ngyu.carwashlife.application.carwash.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MapBoundsTest {

  @Test
  @DisplayName("동일한 좌표의 지도 범위는 면적이 없어도 허용하고 값으로 비교한다.")
  void equalCoordinatesAreValidAndHaveValueEquality() {
    MapBounds bounds = new MapBounds(new Coordinates(37.5, 127.0), new Coordinates(37.5, 127.0));

    assertThat(bounds).isEqualTo(new MapBounds(new Coordinates(37.5, 127.0), new Coordinates(37.5, 127.0)));
    assertThat(bounds.southwest()).isEqualTo(bounds.northeast());
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @DisplayName("남서쪽 좌표가 북동쪽보다 크거나 날짜변경선을 교차하면 거절한다.")
  @CsvSource({"38,127,37,128", "37,128,38,127", "-10,170,10,-170"})
  void reversedCoordinatesAreRejected(double south, double west, double north, double east) {
    Coordinates southwest = new Coordinates(south, west);
    Coordinates northeast = new Coordinates(north, east);

    assertThatThrownBy(() -> new MapBounds(southwest, northeast)).isInstanceOf(IllegalArgumentException.class);
  }
}
