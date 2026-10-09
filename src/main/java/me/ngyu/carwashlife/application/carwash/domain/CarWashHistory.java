package me.ngyu.carwashlife.application.carwash.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

@Getter
@Entity
@Table(name = "car_wash_history")
public class CarWashHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "car_wash_id", nullable = false, updatable = false)
  private CarWash carWash;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "base_history_id", updatable = false)
  private CarWashHistory baseHistory;

  @Getter(AccessLevel.NONE)
  @Column(updatable = false, length = 1000)
  private String changedFieldNames;

  @Column(nullable = false, updatable = false)
  private Long memberId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private CarWashHistoryType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private VisitExperience visitExperience;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime observedAt;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime submittedAt;

  @Column(nullable = false, updatable = false)
  private String name;

  @Column(updatable = false)
  private String address;

  @Column(nullable = false, updatable = false)
  private double latitude;

  @Column(nullable = false, updatable = false)
  private double longitude;

  @Column(updatable = false)
  private Integer highPressureWaterPrice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private FacilityAvailability foamLanceAvailability;

  @Column(updatable = false)
  private Integer foamGunPrice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private FacilityAvailability airGunAvailability;

  @Column(updatable = false)
  private Integer airGunPrice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private FacilityAvailability vacuumAvailability;

  @Column(updatable = false)
  private Integer vacuumPrice;

  @Column(updatable = false)
  private Integer washBayCount;

  @Column(updatable = false)
  private Integer dryingBayCount;

  protected CarWashHistory() {
  }

  @Builder
  private CarWashHistory(CarWash carWash,
                         CarWashHistory baseHistory,
                         Long memberId,
                         CarWashHistoryType type,
                         VisitExperience visitExperience,
                         OffsetDateTime observedAt,
                         OffsetDateTime submittedAt,
                         String name,
                         String address,
                         Double latitude,
                         Double longitude,
                         Integer highPressureWaterPrice,
                         FacilityAvailability foamLanceAvailability,
                         Integer foamGunPrice,
                         FacilityAvailability airGunAvailability,
                         Integer airGunPrice,
                         FacilityAvailability vacuumAvailability,
                         Integer vacuumPrice,
                         Integer washBayCount,
                         Integer dryingBayCount) {
    this.carWash = Objects.requireNonNull(carWash, "세차장 원장은 필수입니다.");
    if (memberId == null || memberId <= 0) {
      throw new IllegalArgumentException("작성자 식별자는 양수여야 합니다.");
    }
    this.memberId = memberId;
    this.type = Objects.requireNonNull(type, "이력 유형은 필수입니다.");
    if (type == CarWashHistoryType.REGISTRATION && baseHistory != null) {
      throw new IllegalArgumentException("최초 등록에는 기준 이력을 지정할 수 없습니다.");
    }
    if (type == CarWashHistoryType.MODIFICATION
            && (baseHistory == null || !baseHistory.belongsTo(carWash))) {
      throw new IllegalArgumentException("수정 요청에는 같은 세차장의 기준 이력이 필요합니다.");
    }
    this.baseHistory = baseHistory;
    this.visitExperience = Objects.requireNonNull(visitExperience, "경험 유형은 필수입니다.");
    this.observedAt = Objects.requireNonNull(observedAt, "관찰 시점은 필수입니다.");
    this.submittedAt = Objects.requireNonNull(submittedAt, "제출 시점은 필수입니다.");
    if (observedAt.isAfter(submittedAt)) {
      throw new IllegalArgumentException("관찰 시점은 제출 시점보다 늦을 수 없습니다.");
    }
    validateLocation(name, latitude, longitude);
    this.name = name;
    this.address = address;
    this.latitude = latitude;
    this.longitude = longitude;
    validateNonNegative(highPressureWaterPrice, "고압수 가격");
    validateNonNegative(foamGunPrice, "폼건 가격");
    validateNonNegative(airGunPrice, "에어건 가격");
    validateNonNegative(vacuumPrice, "청소기 가격");
    validateNonNegative(washBayCount, "세차 베이 수");
    validateNonNegative(dryingBayCount, "드라잉 존 베이 수");
    this.highPressureWaterPrice = highPressureWaterPrice;
    this.foamLanceAvailability = defaultAvailability(foamLanceAvailability);
    this.foamGunPrice = foamGunPrice;
    this.airGunAvailability = defaultAvailability(airGunAvailability);
    this.airGunPrice = airGunPrice;
    this.vacuumAvailability = defaultAvailability(vacuumAvailability);
    this.vacuumPrice = vacuumPrice;
    this.washBayCount = washBayCount;
    this.dryingBayCount = dryingBayCount;
    validateFacilityPrice(this.airGunAvailability, airGunPrice, "에어건");
    validateFacilityPrice(this.vacuumAvailability, vacuumPrice, "청소기");
    Set<CarWashHistoryField> changedFields = EnumSet.noneOf(CarWashHistoryField.class);
    for (CarWashHistoryField field : CarWashHistoryField.values()) {
      if (baseHistory == null || !Objects.equals(valueOf(field), baseHistory.valueOf(field))) {
        changedFields.add(field);
      }
    }
    if (changedFields.isEmpty()) {
      throw new IllegalArgumentException("수정 요청에는 변경된 정보가 하나 이상 필요합니다.");
    }
    this.changedFieldNames = changedFields.stream().map(Enum::name)
                                          .collect(Collectors.joining(","));
  }

  public Set<CarWashHistoryField> getChangedFields() {
    if (changedFieldNames == null || changedFieldNames.isBlank()) {
      return Collections.unmodifiableSet(EnumSet.allOf(CarWashHistoryField.class));
    }
    Set<CarWashHistoryField> fields = EnumSet.noneOf(CarWashHistoryField.class);
    for (String fieldName : changedFieldNames.split(",")) {
      fields.add(CarWashHistoryField.valueOf(fieldName));
    }
    return Collections.unmodifiableSet(fields);
  }

  public CarWashHistory getSourceHistory(CarWashHistoryField field) {
    Objects.requireNonNull(field, "정보 항목은 필수입니다.");
    CarWashHistory source = this;
    while (source.getBaseHistory() != null && !source.getChangedFields().contains(field)) {
      source = source.getBaseHistory();
    }
    return source;
  }

  private Object valueOf(CarWashHistoryField field) {
    return switch (field) {
      case NAME -> getName();
      case ADDRESS -> getAddress();
      case LATITUDE -> getLatitude();
      case LONGITUDE -> getLongitude();
      case HIGH_PRESSURE_WATER_PRICE -> getHighPressureWaterPrice();
      case FOAM_LANCE_AVAILABILITY -> getFoamLanceAvailability();
      case FOAM_GUN_PRICE -> getFoamGunPrice();
      case AIR_GUN_AVAILABILITY -> getAirGunAvailability();
      case AIR_GUN_PRICE -> getAirGunPrice();
      case VACUUM_AVAILABILITY -> getVacuumAvailability();
      case VACUUM_PRICE -> getVacuumPrice();
      case WASH_BAY_COUNT -> getWashBayCount();
      case DRYING_BAY_COUNT -> getDryingBayCount();
    };
  }

  boolean belongsTo(CarWash carWash) {
    return this.carWash == carWash
            || (this.carWash.getId() != null && this.carWash.getId().equals(carWash.getId()));
  }

  private static FacilityAvailability defaultAvailability(FacilityAvailability availability) {
    return availability == null ? FacilityAvailability.UNKNOWN : availability;
  }

  private static void validateLocation(String name, Double latitude, Double longitude) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("세차장 이름은 필수입니다.");
    }
    if (latitude == null || !Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
      throw new IllegalArgumentException("위도는 -90부터 90 사이여야 합니다.");
    }
    if (longitude == null || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
      throw new IllegalArgumentException("경도는 -180부터 180 사이여야 합니다.");
    }
  }

  private static void validateNonNegative(Integer value, String label) {
    if (value != null && value < 0) {
      throw new IllegalArgumentException(label + "은 음수일 수 없습니다.");
    }
  }

  private static void validateFacilityPrice(FacilityAvailability availability,
                                            Integer price,
                                            String label) {
    if (availability == FacilityAvailability.UNAVAILABLE && price != null) {
      throw new IllegalArgumentException(label + "을 사용할 수 없으면 가격을 입력할 수 없습니다.");
    }
  }
}
