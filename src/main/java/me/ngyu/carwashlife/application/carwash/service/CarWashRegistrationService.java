package me.ngyu.carwashlife.application.carwash.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import me.ngyu.carwashlife.application.carwash.api.dto.RegisterCarWashDto;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.CarWashConfidencePolicy;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory;
import me.ngyu.carwashlife.application.carwash.domain.CarWashInformation;
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

    OffsetDateTime submittedAt = OffsetDateTime.now(clock);
    CarWash carWash;
    CarWashHistory history;
    int confidence;
    try {
      CarWashInformation information = request.information().toDomain();
      confidence = confidencePolicy.evaluate(request.visitExperience(), request.observedAt(), false, submittedAt);
      carWash = CarWash.create(information.name(), information.coordinates(), submittedAt);
      history = CarWashHistory.register(carWash, member.getId(), request.visitExperience(), request.observedAt(), submittedAt, information);
    } catch (IllegalArgumentException exception) {
      throw new ApplicationException(ErrorCode.VALIDATION_ERROR, exception);
    }

    carWashRepository.save(carWash);
    historyRepository.save(history);
    representativeService.selectAndReflect(carWash, submittedAt);
    return new RegisterCarWashDto.Response(carWash.getId(), history.getId(), confidence, submittedAt);
  }
}
