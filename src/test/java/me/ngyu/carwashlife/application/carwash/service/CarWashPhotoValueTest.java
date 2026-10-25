package me.ngyu.carwashlife.application.carwash.service;

import static org.assertj.core.api.Assertions.assertThat;

import me.ngyu.carwashlife.application.carwash.domain.CarWashPhotoUpload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CarWashPhotoValueTest {

  @Test
  @DisplayName("업로드 사진은 배열 내용과 선언한 파일 형식이 같으면 같은 값과 해시를 갖는다.")
  void uploadUsesArrayContentsForEquality() {
    var photo = new CarWashPhotoUpload(new byte[]{1, 2}, "image/png");
    var same = new CarWashPhotoUpload(new byte[]{1, 2}, "image/png");

    assertThat(photo).isEqualTo(same);
    assertThat(photo.hashCode()).isEqualTo(same.hashCode());
    assertThat(photo).isNotEqualTo(new CarWashPhotoUpload(new byte[]{1, 3}, "image/png"));
    assertThat(photo).isNotEqualTo(new CarWashPhotoUpload(new byte[]{1, 2}, "image/jpeg"));
    assertThat(photo).isNotEqualTo(null).isNotEqualTo("image/png");
  }

  @Test
  @DisplayName("조회 사진은 배열 내용과 파일 형식이 같으면 같은 값과 해시를 갖는다.")
  void photoContentUsesArrayContentsForEquality() {
    var photo = new CarWashPhotoService.PhotoContent(new byte[]{1, 2}, "image/png");
    var same = new CarWashPhotoService.PhotoContent(new byte[]{1, 2}, "image/png");

    assertThat(photo).isEqualTo(same);
    assertThat(photo.hashCode()).isEqualTo(same.hashCode());
    assertThat(photo).isNotEqualTo(new CarWashPhotoService.PhotoContent(new byte[]{1, 3}, "image/png"));
    assertThat(photo).isNotEqualTo(new CarWashPhotoService.PhotoContent(new byte[]{1, 2}, "image/jpeg"));
    assertThat(photo).isNotEqualTo(null).isNotEqualTo("image/png");
  }

  @Test
  @DisplayName("검증한 사진은 배열 내용과 파일 형식이 같으면 같은 값과 해시를 갖는다.")
  void validatedPhotoUsesArrayContentsForEquality() {
    var photo = new CarWashPhotoValidator.ValidatedPhoto(new byte[]{1, 2}, "image/png");
    var same = new CarWashPhotoValidator.ValidatedPhoto(new byte[]{1, 2}, "image/png");

    assertThat(photo).isEqualTo(same);
    assertThat(photo.hashCode()).isEqualTo(same.hashCode());
    assertThat(photo).isNotEqualTo(new CarWashPhotoValidator.ValidatedPhoto(new byte[]{1, 3}, "image/png"));
    assertThat(photo).isNotEqualTo(new CarWashPhotoValidator.ValidatedPhoto(new byte[]{1, 2}, "image/jpeg"));
    assertThat(photo).isNotEqualTo(null).isNotEqualTo("image/png");
  }

  @Test
  @DisplayName("업로드 사진은 원본이나 반환 배열을 변경해도 내용을 유지한다.")
  void uploadCannotBeChangedThroughSharedArrays() {
    byte[] source = {1, 2};
    var photo = new CarWashPhotoUpload(source, "image/png");

    source[0] = 9;
    photo.content()[1] = 9;

    assertThat(photo.content()).containsExactly((byte) 1, (byte) 2);
  }

  @Test
  @DisplayName("최대 크기의 업로드 사진도 문자열에는 크기와 요청 파일 형식만 짧게 표시한다.")
  void uploadStringShowsOnlyMetadataForAMaxSizedPhoto() {
    var photo = new CarWashPhotoUpload(new byte[CarWashPhotoUpload.MAX_BYTES], "image/png");

    assertThat(photo.toString()).contains("size=" + CarWashPhotoUpload.MAX_BYTES, "declaredContentType=image/png")
                                .doesNotContain("content=").hasSizeLessThan(128);
  }

  @Test
  @DisplayName("조회 사진은 원본이나 반환 배열을 변경해도 내용을 유지한다.")
  void photoContentCannotBeChangedThroughSharedArrays() {
    byte[] source = {1, 2};
    var photo = new CarWashPhotoService.PhotoContent(source, "image/png");

    source[0] = 9;
    photo.content()[1] = 9;

    assertThat(photo.content()).containsExactly((byte) 1, (byte) 2);
  }

  @Test
  @DisplayName("검증한 사진은 원본이나 반환 배열을 변경해도 내용을 유지한다.")
  void validatedPhotoCannotBeChangedThroughSharedArrays() {
    byte[] source = {1, 2};
    var photo = new CarWashPhotoValidator.ValidatedPhoto(source, "image/png");

    source[0] = 9;
    photo.content()[1] = 9;

    assertThat(photo.content()).containsExactly((byte) 1, (byte) 2);
  }

  @Test
  @DisplayName("최대 크기의 조회 사진도 문자열에는 크기와 파일 형식만 짧게 표시한다.")
  void photoContentStringShowsOnlyMetadataForAMaxSizedPhoto() {
    var photo = new CarWashPhotoService.PhotoContent(new byte[CarWashPhotoValidator.MAX_BYTES], "image/png");

    assertThat(photo.toString()).contains("size=" + CarWashPhotoValidator.MAX_BYTES, "contentType=image/png")
                                .doesNotContain("content=").hasSizeLessThan(128);
  }

  @Test
  @DisplayName("최대 크기의 검증한 사진도 문자열에는 크기와 파일 형식만 짧게 표시한다.")
  void validatedPhotoStringShowsOnlyMetadataForAMaxSizedPhoto() {
    var photo = new CarWashPhotoValidator.ValidatedPhoto(new byte[CarWashPhotoValidator.MAX_BYTES], "image/png");

    assertThat(photo.toString()).contains("size=" + CarWashPhotoValidator.MAX_BYTES, "contentType=image/png")
                                .doesNotContain("content=").hasSizeLessThan(128);
  }
}
