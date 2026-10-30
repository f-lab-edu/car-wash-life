package me.ngyu.carwashlife.application.region.domain;

public record RegionSearch(String keyword, int page, int size) {

  public static final int DEFAULT_PAGE_SIZE = 20;

  public static final int MAX_PAGE_SIZE = 100;

}
