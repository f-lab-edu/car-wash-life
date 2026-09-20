package me.ngyu.carwashlife.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import me.ngyu.carwashlife.application.member.domain.MemberStatus;
import me.ngyu.carwashlife.application.member.domain.VehicleType;
import me.ngyu.carwashlife.application.member.domain.WashExperience;

@Entity
@Table(name = "members")
public class MemberEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false, length = 60)
  private String password;

  private String residenceRegionCode;

  @Enumerated(EnumType.STRING)
  private VehicleType vehicleType;

  @Enumerated(EnumType.STRING)
  private WashExperience washExperience;

  @Column(nullable = false)
  private boolean emailVerified;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MemberStatus status;

  @Column(nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  protected MemberEntity() {
  }

  private MemberEntity(
      String email,
      String password,
      String residenceRegionCode,
      VehicleType vehicleType,
      WashExperience washExperience,
      OffsetDateTime createdAt
  ) {
    this.email = email;
    this.password = password;
    this.residenceRegionCode = residenceRegionCode;
    this.vehicleType = vehicleType;
    this.washExperience = washExperience;
    this.emailVerified = true;
    this.status = MemberStatus.ACTIVE;
    this.createdAt = createdAt;
  }

  public static MemberEntity create(
      String email,
      String encodedPassword,
      String residenceRegionCode,
      VehicleType vehicleType,
      WashExperience washExperience,
      OffsetDateTime createdAt
  ) {
    return new MemberEntity(
        email,
        encodedPassword,
        residenceRegionCode,
        vehicleType,
        washExperience,
        createdAt
    );
  }

  public Long getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPassword() {
    return password;
  }

  public String getResidenceRegionCode() {
    return residenceRegionCode;
  }

  public VehicleType getVehicleType() {
    return vehicleType;
  }

  public WashExperience getWashExperience() {
    return washExperience;
  }

  public boolean isEmailVerified() {
    return emailVerified;
  }

  public MemberStatus getStatus() {
    return status;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public boolean isActive() {
    return status == MemberStatus.ACTIVE;
  }
}
