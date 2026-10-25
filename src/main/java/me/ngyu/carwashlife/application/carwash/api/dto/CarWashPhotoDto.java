package me.ngyu.carwashlife.application.carwash.api.dto;

import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.InputStream;
import me.ngyu.carwashlife.application.carwash.domain.CarWashPhotoUpload;
import me.ngyu.carwashlife.common.exception.ApplicationException;
import me.ngyu.carwashlife.common.exception.ErrorCode;
import org.springframework.web.multipart.MultipartFile;

public class CarWashPhotoDto {

  private CarWashPhotoDto() {
  }

  public record Request(@NotNull MultipartFile file) {

    /**
     * 업로드 파일을 크기 제한 안에서 읽어 도메인 값으로 변환한다.
     */
    public CarWashPhotoUpload toDomain() {
      if (file == null || file.isEmpty()) {
        throw new ApplicationException(ErrorCode.VALIDATION_ERROR);
      }
      if (file.getSize() > CarWashPhotoUpload.MAX_BYTES) {
        throw new ApplicationException(ErrorCode.PHOTO_TOO_LARGE);
      }
      byte[] content;
      try (InputStream input = file.getInputStream()) {
        content = input.readNBytes(CarWashPhotoUpload.MAX_BYTES + 1);
      } catch (IOException exception) {
        throw new ApplicationException(ErrorCode.PHOTO_STORAGE_ERROR, exception);
      }
      if (content.length > CarWashPhotoUpload.MAX_BYTES) {
        throw new ApplicationException(ErrorCode.PHOTO_TOO_LARGE);
      }
      return new CarWashPhotoUpload(content, file.getContentType());
    }
  }

  public record Response(Long photoId, String url) {

  }

  public record Snapshot(Long photoId, String url, Long sourceHistoryId) {

  }
}
