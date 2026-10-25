package me.ngyu.carwashlife.application.carwash.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import me.ngyu.carwashlife.application.carwash.domain.CarWashPhotoUpload;
import me.ngyu.carwashlife.common.exception.ApplicationException;
import me.ngyu.carwashlife.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class CarWashPhotoDtoTest {

  @Test
  @DisplayName("신고한 크기가 작아도 실제 파일이 제한을 넘으면 업로드 값으로 변환하지 않는다.")
  void actualUploadSizeCannotBypassTheDeclaredSizeCheck() {
    MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[CarWashPhotoUpload.MAX_BYTES + 1]) {
      @Override
      public long getSize() {
        return 1;
      }
    };
    CarWashPhotoDto.Request request = new CarWashPhotoDto.Request(file);

    ApplicationException exception = catchThrowableOfType(ApplicationException.class, request::toDomain);

    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PHOTO_TOO_LARGE);
  }
}
