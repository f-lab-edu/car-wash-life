package me.ngyu.carwashlife.application.carwash.domain;

public enum FacilityAvailability {
  UNKNOWN,
  AVAILABLE,
  UNAVAILABLE;

  public boolean supportsPrice() {
    return this != UNAVAILABLE;
  }
}
