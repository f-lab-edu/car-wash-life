package me.ngyu.carwashlife.application.carwash.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import me.ngyu.carwashlife.application.carwash.domain.CarWashPhotoUpload;
import me.ngyu.carwashlife.common.exception.ApplicationException;
import me.ngyu.carwashlife.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

@Component
public class CarWashPhotoValidator {

  public static final int MAX_BYTES = CarWashPhotoUpload.MAX_BYTES;
  private static final int MAX_DIMENSION = 10000;
  private static final long MAX_PIXELS = 25_000_000L;

  /**
   * 업로드 내용의 실제 이미지 형식과 크기 및 해상도를 검증한다.
   */
  public ValidatedPhoto validate(CarWashPhotoUpload upload) {
    byte[] content = upload.content();
    if (content.length == 0) {
      throw new ApplicationException(ErrorCode.VALIDATION_ERROR);
    }
    if (content.length > MAX_BYTES) {
      throw new ApplicationException(ErrorCode.PHOTO_TOO_LARGE);
    }
    try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) {
        throw new ApplicationException(ErrorCode.VALIDATION_ERROR);
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(input, true, true);
        String contentType = switch (reader.getFormatName().toLowerCase(java.util.Locale.ROOT)) {
          case "jpeg", "jpg" -> "image/jpeg";
          case "png" -> "image/png";
          default -> throw new ApplicationException(ErrorCode.VALIDATION_ERROR);
        };
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        if (!contentType.equalsIgnoreCase(upload.declaredContentType()) || width <= 0 || height <= 0
                || width > MAX_DIMENSION || height > MAX_DIMENSION || (long) width * height > MAX_PIXELS || reader.read(0) == null) {
          throw new ApplicationException(ErrorCode.VALIDATION_ERROR);
        }
        return new ValidatedPhoto(content, contentType);
      } finally {
        reader.dispose();
      }
    } catch (IOException | IllegalArgumentException exception) {
      throw new ApplicationException(ErrorCode.VALIDATION_ERROR, exception);
    }
  }

  public record ValidatedPhoto(byte[] content, String contentType) {

    public ValidatedPhoto {
      content = content.clone();
    }

    @Override
    public byte[] content() {
      return content.clone();
    }

    /**
     * 검증한 사진 내용과 파일 형식으로 동등성을 비교한다.
     */
    @Override
    public boolean equals(Object other) {
      return other instanceof ValidatedPhoto photo && Arrays.equals(content, photo.content) && Objects.equals(contentType, photo.contentType);
    }

    /**
     * 검증한 사진 내용과 파일 형식으로 해시를 계산한다.
     */
    @Override
    public int hashCode() {
      return 31 * Arrays.hashCode(content) + Objects.hashCode(contentType);
    }

    @Override
    public String toString() {
      return "ValidatedPhoto[size=" + content.length + ", contentType=" + contentType + "]";
    }
  }
}
