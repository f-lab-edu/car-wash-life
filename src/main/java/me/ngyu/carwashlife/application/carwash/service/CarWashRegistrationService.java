package me.ngyu.carwashlife.application.carwash.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.ngyu.carwashlife.application.carwash.api.dto.RegisterCarWashDto;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory;
import me.ngyu.carwashlife.application.carwash.domain.CarWashInformation;
import me.ngyu.carwashlife.application.carwash.domain.CarWashPhoto;
import me.ngyu.carwashlife.application.member.domain.Member;
import me.ngyu.carwashlife.common.exception.ApplicationException;
import me.ngyu.carwashlife.common.exception.ErrorCode;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashHistoryRepository;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashRepository;
import me.ngyu.carwashlife.infrastructure.persistence.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CarWashRegistrationService {

  private final CarWashRepository carWashRepository;
  private final CarWashHistoryRepository historyRepository;
  private final MemberRepository memberRepository;
  private final CarWashRepresentativeService representativeService;
  private final CarWashPhotoService photoService;
  private final Clock clock;

  @Transactional
  public RegisterCarWashDto.Response register(Long memberId, RegisterCarWashDto.Request request) {
    if (memberId == null) {
      throw new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
    Member member = memberRepository.findById(memberId)
                                    .orElseThrow(() -> new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED));
    if (!member.isActive()) {
      throw new ApplicationException(ErrorCode.ACCOUNT_NOT_ACTIVE);
    }

    List<CarWashPhoto> photos = photoService.lockPendingPhotos(member.getId(), request.photoIds());
    OffsetDateTime submittedAt = OffsetDateTime.now(clock);
    CarWash carWash;
    CarWashHistory history;
    try {
      CarWashInformation information = request.information().toDomain();
      carWash = CarWash.create(information.name(), information.coordinates(), submittedAt);
      history = CarWashHistory.register(carWash, member.getId(), request.visitExperience(), request.observedAt(), submittedAt, information, photos.stream().map(CarWashPhoto::getId).toList());
    } catch (IllegalArgumentException exception) {
      throw new ApplicationException(ErrorCode.VALIDATION_ERROR, exception);
    }

    int confidence = history.calculateConfidence(history.hasEvidencePhotos(), submittedAt);
    carWashRepository.save(carWash);
    historyRepository.save(history);
    photoService.attachPhotos(photos, history);
    representativeService.selectAndReflect(carWash, submittedAt);
    return new RegisterCarWashDto.Response(carWash.getId(), history.getId(), confidence, submittedAt);
  }
}
