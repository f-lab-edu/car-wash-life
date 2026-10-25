package me.ngyu.carwashlife.application.carwash.domain;

import java.util.Arrays;
import java.util.Objects;

public record CarWashPhotoUpload(byte[] content, String declaredContentType) {

  public static final int MAX_BYTES = 5 * 1024 * 1024;

  public CarWashPhotoUpload {
    content = content.clone();
  }

  /**
   * 업로드 내용이 외부에서 변경되지 않도록 복사본을 반환한다.
   */
  @Override
  public byte[] content() {
    return content.clone();
  }

  /**
   * 업로드 내용과 요청한 파일 형식으로 동등성을 비교한다.
   */
  @Override
  public boolean equals(Object other) {
    return other instanceof CarWashPhotoUpload photo && Arrays.equals(content, photo.content) && Objects.equals(declaredContentType, photo.declaredContentType);
  }

  /**
   * 업로드 내용과 요청한 파일 형식으로 해시를 계산한다.
   */
  @Override
  public int hashCode() {
    return 31 * Arrays.hashCode(content) + Objects.hashCode(declaredContentType);
  }

  /**
   * 파일 내용 대신 크기와 요청한 파일 형식만 표시한다.
   */
  @Override
  public String toString() {
    return "CarWashPhotoUpload[size=" + content.length + ", declaredContentType=" + declaredContentType + "]";
  }
}
