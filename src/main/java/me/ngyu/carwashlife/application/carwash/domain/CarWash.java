package me.ngyu.carwashlife.application.carwash.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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

  @Embedded
  private Coordinates coordinates;

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

  private CarWash(String name, Coordinates coordinates, OffsetDateTime createdAt) {
    validateName(name);
    this.name = name;
    this.coordinates = Objects.requireNonNull(coordinates, "좌표는 필수입니다.");
    this.createdAt = Objects.requireNonNull(createdAt, "최초 등록 시점은 필수입니다.");
    this.updatedAt = createdAt;
    this.operatorManaged = false;
  }

  public static CarWash create(String name,
                               Coordinates coordinates,
                               OffsetDateTime createdAt) {
    return new CarWash(name, coordinates, createdAt);
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
    this.coordinates = history.getCoordinates();
    this.updatedAt = selectedAt;
  }

  public double getLatitude() {
    return coordinates.getLatitude();
  }

  public double getLongitude() {
    return coordinates.getLongitude();
  }

  private static void validateName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("세차장 이름은 필수입니다.");
    }
  }
}
