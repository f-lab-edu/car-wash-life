package me.ngyu.carwashlife.application.carwash.service;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import me.ngyu.carwashlife.application.carwash.api.dto.CarWashPhotoDto;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory;
import me.ngyu.carwashlife.application.carwash.domain.CarWashPhoto;
import me.ngyu.carwashlife.application.member.domain.Member;
import me.ngyu.carwashlife.common.exception.ApplicationException;
import me.ngyu.carwashlife.common.exception.ErrorCode;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashPhotoRepository;
import me.ngyu.carwashlife.infrastructure.persistence.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class CarWashPhotoService {

  private final CarWashPhotoRepository photoRepository;
  private final MemberRepository memberRepository;
  private final CarWashPhotoStorage storage;
  private final CarWashPhotoValidator validator;
  private final Clock clock;

  @Transactional
  public CarWashPhotoDto.Response upload(Long memberId, MultipartFile file) {
    requireActiveMember(memberId);
    CarWashPhotoValidator.ValidatedPhoto photo = validator.validate(file);
    String key;
    try {
      key = storage.write(photo.content());
    } catch (IOException exception) {
      throw new ApplicationException(ErrorCode.PHOTO_STORAGE_ERROR);
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCompletion(int status) {
        if (status != STATUS_COMMITTED) {
          try {
            storage.delete(key);
          } catch (IOException exception) {
            System.getLogger(CarWashPhotoService.class.getName()).log(System.Logger.Level.ERROR, "롤백된 사진 파일을 정리하지 못했습니다.");
          }
        }
      }
    });
    CarWashPhoto saved = photoRepository.save(CarWashPhoto.create(memberId, key, photo.contentType(), photo.content().length, OffsetDateTime.now(clock)));
    return new CarWashPhotoDto.Response(saved.getId(), url(saved.getId()));
  }

  @Transactional(readOnly = true)
  public PhotoContent read(Long memberId, Long photoId) {
    requireActiveMember(memberId);
    CarWashPhoto photo = photoRepository.findById(photoId)
                                        .orElseThrow(() -> new ApplicationException(ErrorCode.CAR_WASH_PHOTO_NOT_FOUND));
    if (photo.isPending() && !photo.getOwnerId().equals(memberId)) {
      throw new ApplicationException(ErrorCode.CAR_WASH_PHOTO_NOT_FOUND);
    }
    try {
      byte[] content = storage.read(photo.getStorageKey());
      if (content.length != photo.getSize()) {
        throw new IOException("사진 크기가 일치하지 않습니다.");
      }
      return new PhotoContent(content, photo.getContentType());
    } catch (IOException exception) {
      throw new ApplicationException(ErrorCode.PHOTO_STORAGE_ERROR);
    }
  }

  public List<CarWashPhoto> lockPendingPhotos(Long memberId, List<Long> photoIds) {
    if (photoIds == null || photoIds.isEmpty()) {
      return List.of();
    }
    if (photoIds.size() > 5 || photoIds.stream().anyMatch(id -> id == null || id <= 0) || new HashSet<>(photoIds).size() != photoIds.size()) {
      throw new ApplicationException(ErrorCode.VALIDATION_ERROR);
    }
    return photoIds.stream().sorted().map(id -> {
      CarWashPhoto photo = photoRepository.findByIdForUpdate(id)
                                          .orElseThrow(() -> new ApplicationException(ErrorCode.VALIDATION_ERROR));
      if (!photo.isPending() || !photo.getOwnerId().equals(memberId)) {
        throw new ApplicationException(ErrorCode.VALIDATION_ERROR);
      }
      return photo;
    }).toList();
  }

  public void attachPhotos(List<CarWashPhoto> photos, CarWashHistory history) {
    photos.forEach(photo -> photo.attachTo(history));
  }

  public List<CarWashPhotoDto.Snapshot> snapshots(CarWashHistory history) {
    Map<Long, CarWashPhoto> photos = photoRepository.findAllById(history.getPhotoIds()).stream()
                                                    .collect(Collectors.toMap(CarWashPhoto::getId, Function.identity()));
    return history.getPhotoIds().stream().map(id -> {
      CarWashPhoto photo = photos.get(id);
      if (photo == null || photo.getAttachedHistory() == null) {
        throw new ApplicationException(ErrorCode.CAR_WASH_PHOTO_NOT_FOUND);
      }
      return new CarWashPhotoDto.Snapshot(id, url(id), photo.getAttachedHistory().getId());
    }).toList();
  }

  private void requireActiveMember(Long memberId) {
    if (memberId == null) {
      throw new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
    Member member = memberRepository.findById(memberId)
                                    .orElseThrow(() -> new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED));
    if (!member.isActive()) {
      throw new ApplicationException(ErrorCode.ACCOUNT_NOT_ACTIVE);
    }
  }

  private static String url(Long photoId) {
    return "/car-wash-photos/" + photoId;
  }

  public record PhotoContent(byte[] content, String contentType) {

    @Override
    public boolean equals(Object other) {
      return other instanceof PhotoContent photo && Arrays.equals(content, photo.content) && Objects.equals(contentType, photo.contentType);
    }

    @Override
    public int hashCode() {
      return 31 * Arrays.hashCode(content) + Objects.hashCode(contentType);
    }

    @Override
    public String toString() {
      return "PhotoContent[content=" + Arrays.toString(content) + ", contentType=" + contentType + "]";
    }
  }
}
