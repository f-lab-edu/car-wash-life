package me.ngyu.carwashlife.application.carwash.domain;

public record CarWashSearch(String keyword) {

  public CarWashSearch {
    if (keyword == null) {
      throw new IllegalArgumentException("검색어는 필수입니다.");
    }
    keyword = keyword.strip();
    if (keyword.isEmpty() || keyword.length() > 100) {
      throw new IllegalArgumentException("검색어는 앞뒤 공백을 제외하고 1~100자여야 합니다.");
    }
  }
}
