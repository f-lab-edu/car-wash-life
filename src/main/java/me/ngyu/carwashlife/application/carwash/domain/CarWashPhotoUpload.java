package me.ngyu.carwashlife.application.carwash.domain;

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
   * 파일 내용 대신 크기와 요청한 파일 형식만 표시한다.
   */
  @Override
  public String toString() {
    return "CarWashPhotoUpload[size=" + content.length + ", declaredContentType=" + declaredContentType + "]";
  }
}
