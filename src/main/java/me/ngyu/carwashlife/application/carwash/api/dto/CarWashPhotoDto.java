package me.ngyu.carwashlife.application.carwash.api.dto;

public class CarWashPhotoDto {

  private CarWashPhotoDto() {
  }

  public record Response(Long photoId, String url) {

  }

  public record Snapshot(Long photoId, String url, Long sourceHistoryId) {

  }
}
