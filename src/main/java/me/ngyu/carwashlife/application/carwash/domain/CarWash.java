package me.ngyu.carwashlife.application.carwash.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import lombok.Getter;

@Getter
@Entity
@Table(name = "car_wash")
public class CarWash {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private double latitude;

  @Column(nullable = false)
  private double longitude;

  @Column(nullable = false)
  private boolean operatorManaged;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "target_history_id")
  private CarWashHistory targetHistory;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(nullable = false)
  private OffsetDateTime updatedAt;

  protected CarWash() {
  }

  private CarWash(String name, double latitude, double longitude, OffsetDateTime createdAt) {
    validateLocation(name, latitude, longitude);
    this.name = name;
    this.latitude = latitude;
    this.longitude = longitude;
    this.createdAt = Objects.requireNonNull(createdAt, "최초 등록 시점은 필수입니다.");
    this.updatedAt = createdAt;
    this.operatorManaged = false;
  }

  public static CarWash create(String name,
                               double latitude,
                               double longitude,
                               OffsetDateTime createdAt) {
    return new CarWash(name, latitude, longitude, createdAt);
  }

  public void selectRepresentativeHistory(CarWashHistory history, OffsetDateTime selectedAt) {
    Objects.requireNonNull(history, "대표 이력은 필수입니다.");
    Objects.requireNonNull(selectedAt, "대표 정보 반영 시점은 필수입니다.");
    if (!history.belongsTo(this)) {
      throw new IllegalArgumentException("다른 세차장의 이력은 대표 정보로 선택할 수 없습니다.");
    }
    if (selectedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("대표 정보 반영 시점은 최초 등록 시점보다 빠를 수 없습니다.");
    }
    if (targetHistory == history
            || (targetHistory != null && targetHistory.getId() != null
            && targetHistory.getId().equals(history.getId()))) {
      return;
    }

    this.targetHistory = history;
    this.name = history.getName();
    this.latitude = history.getLatitude();
    this.longitude = history.getLongitude();
    this.updatedAt = selectedAt;
  }

  private static void validateLocation(String name, double latitude, double longitude) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("세차장 이름은 필수입니다.");
    }
    if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
      throw new IllegalArgumentException("위도는 -90부터 90 사이여야 합니다.");
    }
    if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
      throw new IllegalArgumentException("경도는 -180부터 180 사이여야 합니다.");
    }
  }
}
