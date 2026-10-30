package me.ngyu.carwashlife.application.carwash.domain;

public record CarWashSearch(String keyword, Coordinates currentCoordinates, int page, int size) {

  public static final int DEFAULT_PAGE_SIZE = 100;

  public static final int MAX_PAGE_SIZE = 100;

}
