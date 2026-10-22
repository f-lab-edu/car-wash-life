package me.ngyu.carwashlife.application.carwash.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldNameConstants;

@Getter
@FieldNameConstants(asEnum = true, onlyExplicitlyIncluded = true)
@Entity
@Table(name = "car_wash_history", indexes = @Index(name = "idx_car_wash_history_car_wash_id", columnList = "car_wash_id"))
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

  @Getter(AccessLevel.NONE)
  @Column(updatable = false, columnDefinition = "text")
  private String evidencePhotoIdValues;

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
  @FieldNameConstants.Include
  private String name;

  @Column(updatable = false)
  @FieldNameConstants.Include
  private String address;

  @Embedded
  @AttributeOverrides({
          @AttributeOverride(name = "latitude", column = @Column(name = "latitude", nullable = false, updatable = false)),
          @AttributeOverride(name = "longitude", column = @Column(name = "longitude", nullable = false, updatable = false))
  })
  @FieldNameConstants.Include
  private Coordinates coordinates;

  @Column(updatable = false)
  @FieldNameConstants.Include
  private Integer highPressureWaterPrice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  @FieldNameConstants.Include
  private FacilityAvailability foamLanceAvailability;

  @Column(updatable = false)
  @FieldNameConstants.Include
  private Integer foamGunPrice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  @FieldNameConstants.Include
  private FacilityAvailability airGunAvailability;

  @Column(updatable = false)
  @FieldNameConstants.Include
  private Integer airGunPrice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  @FieldNameConstants.Include
  private FacilityAvailability vacuumAvailability;

  @Column(updatable = false)
  @FieldNameConstants.Include
  private Integer vacuumPrice;

  @Column(updatable = false)
  @FieldNameConstants.Include
  private Integer washBayCount;

  @Column(updatable = false)
  @FieldNameConstants.Include
  private Integer dryingBayCount;

  @Getter(AccessLevel.NONE)
  @Column(name = "photo_id_values", updatable = false, columnDefinition = "text")
  @FieldNameConstants.Include
  private String photos;

  public static CarWashHistory register(CarWash carWash,
                                        Long memberId,
                                        VisitExperience visitExperience,
                                        OffsetDateTime observedAt,
                                        OffsetDateTime submittedAt,
                                        CarWashInformation information) {
    return register(carWash, memberId, visitExperience, observedAt, submittedAt, information, List.of());
  }

  public static CarWashHistory register(CarWash carWash,
                                        Long memberId,
                                        VisitExperience visitExperience,
                                        OffsetDateTime observedAt,
                                        OffsetDateTime submittedAt,
                                        CarWashInformation information,
                                        List<Long> newPhotoIds) {
    return new CarWashHistory(carWash, null, memberId, CarWashHistoryType.REGISTRATION, visitExperience,
                              observedAt, submittedAt, information, newPhotoIds);
  }

  public static CarWashHistory modify(CarWash carWash,
                                      CarWashHistory baseHistory,
                                      Long memberId,
                                      VisitExperience visitExperience,
                                      OffsetDateTime observedAt,
                                      OffsetDateTime submittedAt,
                                      CarWashInformation information) {
    return modify(carWash, baseHistory, memberId, visitExperience, observedAt, submittedAt, information, List.of());
  }

  public static CarWashHistory modify(CarWash carWash,
                                      CarWashHistory baseHistory,
                                      Long memberId,
                                      VisitExperience visitExperience,
                                      OffsetDateTime observedAt,
                                      OffsetDateTime submittedAt,
                                      CarWashInformation information,
                                      List<Long> newPhotoIds) {
    return new CarWashHistory(carWash, baseHistory, memberId, CarWashHistoryType.MODIFICATION, visitExperience,
                              observedAt, submittedAt, information, newPhotoIds);
  }

  private CarWashHistory(CarWash carWash,
                         CarWashHistory baseHistory,
                         Long memberId,
                         CarWashHistoryType type,
                         VisitExperience visitExperience,
                         OffsetDateTime observedAt,
                         OffsetDateTime submittedAt,
                         CarWashInformation information,
                         List<Long> newPhotoIds) {
    this.carWash = Objects.requireNonNull(carWash, "세차장 원장은 필수입니다.");
    if (memberId == null || memberId <= 0) {
      throw new IllegalArgumentException("작성자 식별자는 양수여야 합니다.");
    }
    this.memberId = memberId;
    this.type = Objects.requireNonNull(type, "이력 유형은 필수입니다.");
    if (type == CarWashHistoryType.REGISTRATION && baseHistory != null) {
      throw new IllegalArgumentException("최초 등록에는 기준 이력을 지정할 수 없습니다.");
    }
    if (type == CarWashHistoryType.MODIFICATION && (baseHistory == null || !baseHistory.belongsTo(carWash))) {
      throw new IllegalArgumentException("수정 요청에는 같은 세차장의 기준 이력이 필요합니다.");
    }
    this.baseHistory = baseHistory;

    this.visitExperience = Objects.requireNonNull(visitExperience, "경험 유형은 필수입니다.");
    this.observedAt = Objects.requireNonNull(observedAt, "관찰 시점은 필수입니다.");
    this.submittedAt = Objects.requireNonNull(submittedAt, "제출 시점은 필수입니다.");
    if (observedAt.isAfter(submittedAt)) {
      throw new IllegalArgumentException("관찰 시점은 제출 시점보다 늦을 수 없습니다.");
    }

    Objects.requireNonNull(information, "세차장 상세정보는 필수입니다.");
    this.name = information.name();
    this.address = information.address();
    this.coordinates = information.coordinates();
    this.highPressureWaterPrice = information.highPressureWaterPrice();
    this.foamLanceAvailability = information.foamLanceAvailability();
    this.foamGunPrice = information.foamGunPrice();
    this.airGunAvailability = information.airGunAvailability();
    this.airGunPrice = information.airGunPrice();
    this.vacuumAvailability = information.vacuumAvailability();
    this.vacuumPrice = information.vacuumPrice();
    this.washBayCount = information.washBayCount();
    this.dryingBayCount = information.dryingBayCount();

    List<Long> evidenceIds = newPhotoIds == null ? List.of() : newPhotoIds;
    if (evidenceIds.size() > 5 || evidenceIds.stream().anyMatch(photoId -> photoId == null || photoId <= 0)
            || new HashSet<>(evidenceIds).size() != evidenceIds.size()) {
      throw new IllegalArgumentException("사진 식별자는 중복 없는 양수이며 최대 5개입니다.");
    }
    List<Long> inheritedIds = baseHistory == null ? List.of() : baseHistory.getPhotoIds();
    if (evidenceIds.stream().anyMatch(inheritedIds::contains)) {
      throw new IllegalArgumentException("상속한 사진을 새 관찰 근거로 제출할 수 없습니다.");
    }
    List<Long> snapshotIds = new ArrayList<>(inheritedIds);
    snapshotIds.addAll(evidenceIds);
    this.photos = encodePhotoIds(snapshotIds);
    this.evidencePhotoIdValues = encodePhotoIds(evidenceIds);

    this.changedFieldNames = calculateChangedFieldNames();
  }

  private String calculateChangedFieldNames() {
    Set<Fields> changedFields = EnumSet.noneOf(Fields.class);
    for (Fields field : Fields.values()) {
      if (baseHistory == null || !Objects.equals(valueOf(field), baseHistory.valueOf(field))) {
        changedFields.add(field);
      }
    }
    if (changedFields.isEmpty()) {
      throw new IllegalArgumentException("수정 요청에는 변경된 정보가 하나 이상 필요합니다.");
    }
    return changedFields.stream().map(Enum::name)
                        .collect(Collectors.joining(","));
  }

  protected CarWashHistory() {
  }

  public int calculateConfidence(OffsetDateTime evaluatedAt) {
    Objects.requireNonNull(evaluatedAt, "평가 시점은 필수입니다.");

    int confidence = 1;
    if (visitExperience == VisitExperience.USED) {
      confidence += 2;
    }
    if (!observedAt.toInstant().isBefore(evaluatedAt.toInstant().minus(90, ChronoUnit.DAYS))) {
      confidence++;
    }
    if (hasEvidencePhotos()) {
      confidence++;
    }
    return confidence;
  }

  public double getLatitude() {
    return coordinates.getLatitude();
  }

  public double getLongitude() {
    return coordinates.getLongitude();
  }

  boolean belongsTo(CarWash carWash) {
    return this.carWash == carWash || (this.carWash.getId() != null && this.carWash.getId().equals(carWash.getId()));
  }

  public List<Long> getPhotoIds() {
    return decodePhotoIds(photos);
  }

  public List<Long> getEvidencePhotoIds() {
    return decodePhotoIds(evidencePhotoIdValues);
  }

  public boolean hasEvidencePhotos() {
    return !getEvidencePhotoIds().isEmpty();
  }

  private static String encodePhotoIds(List<Long> ids) {
    return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
  }

  private static List<Long> decodePhotoIds(String ids) {
    if (ids == null || ids.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(ids.split(",")).map(Long::valueOf).toList();
  }

  private Object valueOf(Fields field) {
    return switch (field) {
      case NAME -> getName();
      case ADDRESS -> getAddress();
      case COORDINATES -> getCoordinates();
      case HIGH_PRESSURE_WATER_PRICE -> getHighPressureWaterPrice();
      case FOAM_LANCE_AVAILABILITY -> getFoamLanceAvailability();
      case FOAM_GUN_PRICE -> getFoamGunPrice();
      case AIR_GUN_AVAILABILITY -> getAirGunAvailability();
      case AIR_GUN_PRICE -> getAirGunPrice();
      case VACUUM_AVAILABILITY -> getVacuumAvailability();
      case VACUUM_PRICE -> getVacuumPrice();
      case WASH_BAY_COUNT -> getWashBayCount();
      case DRYING_BAY_COUNT -> getDryingBayCount();
      case PHOTOS -> getPhotoIds();
    };
  }

  public CarWashHistory getSourceHistory(Fields field) {
    Objects.requireNonNull(field, "정보 항목은 필수입니다.");
    CarWashHistory source = this;
    while (source.getBaseHistory() != null && !source.getChangedFields().contains(field)) {
      source = source.getBaseHistory();
    }
    return source;
  }

  public Set<Fields> getChangedFields() {
    if (changedFieldNames == null || changedFieldNames.isBlank()) {
      return Collections.unmodifiableSet(EnumSet.allOf(Fields.class));
    }
    Set<Fields> fields = EnumSet.noneOf(Fields.class);
    for (String fieldName : changedFieldNames.split(",")) {
      fields.add(switch (fieldName) {
        case "LATITUDE", "LONGITUDE" -> Fields.COORDINATES;
        default -> Fields.valueOf(fieldName);
      });
    }
    return Collections.unmodifiableSet(fields);
  }

}
