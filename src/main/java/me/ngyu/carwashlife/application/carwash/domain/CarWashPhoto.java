package me.ngyu.carwashlife.application.carwash.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

@Getter
@Entity
@Table(name = "car_wash_photo")
public class CarWashPhoto {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private Long ownerId;

  @Column(nullable = false, updatable = false, unique = true, length = 36)
  private String storageKey;

  @Column(nullable = false, updatable = false)
  private String contentType;

  @Column(nullable = false, updatable = false)
  private long size;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "attached_history_id")
  private CarWashHistory attachedHistory;

  protected CarWashPhoto() {
  }

  private CarWashPhoto(Long ownerId, String storageKey, String contentType, long size, OffsetDateTime createdAt) {
    if (ownerId == null || ownerId <= 0 || size <= 0) {
      throw new IllegalArgumentException("사진 작성자와 크기가 올바르지 않습니다.");
    }
    if (!UUID.fromString(storageKey).toString().equals(storageKey)) {
      throw new IllegalArgumentException("사진 저장 키가 올바르지 않습니다.");
    }
    if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)) {
      throw new IllegalArgumentException("지원하지 않는 사진 형식입니다.");
    }
    this.ownerId = ownerId;
    this.storageKey = storageKey;
    this.contentType = contentType;
    this.size = size;
    this.createdAt = Objects.requireNonNull(createdAt);
  }

  public static CarWashPhoto create(Long ownerId, String storageKey, String contentType, long size, OffsetDateTime createdAt) {
    return new CarWashPhoto(ownerId, storageKey, contentType, size, createdAt);
  }

  public boolean isPending() {
    return attachedHistory == null;
  }

  public void attachTo(CarWashHistory history) {
    if (!isPending() || !ownerId.equals(history.getMemberId()) || !history.getEvidencePhotoIds().contains(id)) {
      throw new IllegalArgumentException("사진을 해당 이력에 연결할 수 없습니다.");
    }
    attachedHistory = history;
  }
}
