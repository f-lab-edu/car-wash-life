package me.ngyu.carwashlife.application.carwash.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.ngyu.carwashlife.application.carwash.api.dto.RegisterCarWashDto;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.CarWashConfidencePolicy;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistoryType;
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
  private final CarWashConfidencePolicy confidencePolicy;
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
    try {
      int confidence = confidencePolicy.evaluate(
              request.visitExperience(), request.observedAt(), !photos.isEmpty(), submittedAt);
      CarWash carWash = carWashRepository.save(CarWash.create(
              request.name(), request.latitude(), request.longitude(), submittedAt));
      CarWashHistory history = historyRepository.save(CarWashHistory.builder()
                                                                    .carWash(carWash)
                                                                    .memberId(member.getId())
                                                                    .type(CarWashHistoryType.REGISTRATION)
                                                                    .visitExperience(request.visitExperience())
                                                                    .observedAt(request.observedAt())
                                                                    .submittedAt(submittedAt)
                                                                    .name(request.name())
                                                                    .address(request.address())
                                                                    .latitude(request.latitude())
                                                                    .longitude(request.longitude())
                                                                    .highPressureWaterPrice(request.highPressureWaterPrice())
                                                                    .foamLanceAvailability(request.foamLanceAvailability())
                                                                    .foamGunPrice(request.foamGunPrice())
                                                                    .airGunAvailability(request.airGunAvailability())
                                                                    .airGunPrice(request.airGunPrice())
                                                                    .vacuumAvailability(request.vacuumAvailability())
                                                                    .vacuumPrice(request.vacuumPrice())
                                                                    .washBayCount(request.washBayCount())
                                                                    .dryingBayCount(request.dryingBayCount())
                                                                    .newPhotoIds(photos.stream().map(CarWashPhoto::getId).toList())
                                                                    .build());
      photoService.attachPhotos(photos, history);
      representativeService.selectAndReflect(carWash, submittedAt);
      return new RegisterCarWashDto.Response(
              carWash.getId(), history.getId(), confidence, submittedAt);
    } catch (IllegalArgumentException exception) {
      throw new ApplicationException(ErrorCode.VALIDATION_ERROR);
    }
  }
}
