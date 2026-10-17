package me.ngyu.carwashlife.application.carwash.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CoordinatesTest {

  @DisplayName("위도가 없으면 좌표 생성을 거절한다.")
  @Test
  void missingLatitudeIsRejected() {
    assertThatThrownBy(() -> new Coordinates(null, 127.0)).isInstanceOf(IllegalArgumentException.class);
  }

  @DisplayName("경도가 없으면 좌표 생성을 거절한다.")
  @Test
  void missingLongitudeIsRejected() {
    assertThatThrownBy(() -> new Coordinates(37.5, null)).isInstanceOf(IllegalArgumentException.class);
  }

  @DisplayName("위도가 허용 범위를 벗어나거나 유한하지 않으면 좌표 생성을 거절한다.")
  @ParameterizedTest(name = "[{index}] {displayName}")
  @ValueSource(doubles = {-90.1, 90.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
  void invalidLatitudeIsRejected(double latitude) {
    assertThatThrownBy(() -> new Coordinates(latitude, 127.0)).isInstanceOf(IllegalArgumentException.class);
  }

  @DisplayName("경도가 허용 범위를 벗어나거나 유한하지 않으면 좌표 생성을 거절한다.")
  @ParameterizedTest(name = "[{index}] {displayName}")
  @ValueSource(doubles = {-180.1, 180.1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY})
  void invalidLongitudeIsRejected(double longitude) {
    assertThatThrownBy(() -> new Coordinates(37.5, longitude)).isInstanceOf(IllegalArgumentException.class);
  }

  @DisplayName("위도와 경도가 허용 범위의 경계이면 좌표를 생성한다.")
  @ParameterizedTest(name = "[{index}] {displayName}")
  @CsvSource({"-90, -180", "90, 180"})
  void coordinateBoundariesAreAccepted(double latitude, double longitude) {
    Coordinates coordinates = new Coordinates(latitude, longitude);

    assertThat(coordinates.getLatitude()).isEqualTo(latitude);
    assertThat(coordinates.getLongitude()).isEqualTo(longitude);
  }

  @DisplayName("위도와 경도가 같은 별개의 좌표는 동일한 값과 해시값을 가진다.")
  @Test
  void sameCoordinatesHaveValueEqualityAndTheSameHashCode() {
    Coordinates coordinates = new Coordinates(37.5, 127.0);
    Coordinates sameCoordinates = new Coordinates(37.5, 127.0);

    assertThat(coordinates).isEqualTo(sameCoordinates);
    assertThat(coordinates.hashCode()).isEqualTo(sameCoordinates.hashCode());
    assertThat(coordinates).isNotEqualTo(null).isNotEqualTo("coordinates");
  }

  @DisplayName("위도나 경도 중 하나만 달라도 좌표는 서로 다른 값으로 비교한다.")
  @ParameterizedTest(name = "[{index}] {displayName}")
  @CsvSource({"37.6, 127.0", "37.5, 127.1"})
  void changingEitherAxisChangesTheCoordinates(double latitude, double longitude) {
    assertThat(new Coordinates(37.5, 127.0)).isNotEqualTo(new Coordinates(latitude, longitude));
  }
}
