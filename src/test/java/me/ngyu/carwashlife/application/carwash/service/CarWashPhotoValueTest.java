package me.ngyu.carwashlife.application.carwash.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CarWashPhotoValueTest {

  @Test
  @DisplayName("조회 사진은 배열의 내용과 파일 형식이 같으면 같은 값과 해시를 갖는다.")
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
  @DisplayName("검증한 사진은 배열의 내용과 파일 형식이 같으면 같은 값과 해시를 갖는다.")
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
  @DisplayName("조회 사진을 문자열로 표현하면 배열 주소 대신 내용과 파일 형식을 표시한다.")
  void photoContentStringShowsArrayContents() {
    var photo = new CarWashPhotoService.PhotoContent(new byte[]{1, 2}, "image/png");

    assertThat(photo.toString()).isEqualTo("PhotoContent[content=[1, 2], contentType=image/png]");
  }

  @Test
  @DisplayName("검증한 사진을 문자열로 표현하면 배열 주소 대신 내용과 파일 형식을 표시한다.")
  void validatedPhotoStringShowsArrayContents() {
    var photo = new CarWashPhotoValidator.ValidatedPhoto(new byte[]{1, 2}, "image/png");

    assertThat(photo.toString()).isEqualTo("ValidatedPhoto[content=[1, 2], contentType=image/png]");
  }
}
