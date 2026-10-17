package me.ngyu.carwashlife.application.carwash.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@Embeddable
public class Coordinates {

  @Column(name = "latitude", nullable = false)
  private double latitude;

  @Column(name = "longitude", nullable = false)
  private double longitude;

  public Coordinates(Double latitude, Double longitude) {
    if (latitude == null || !Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
      throw new IllegalArgumentException("위도는 -90부터 90 사이여야 합니다.");
    }
    if (longitude == null || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
      throw new IllegalArgumentException("경도는 -180부터 180 사이여야 합니다.");
    }
    this.latitude = latitude;
    this.longitude = longitude;
  }

  protected Coordinates() {
  }
}
