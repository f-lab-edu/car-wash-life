package me.ngyu.carwashlife.application.carwash.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import me.ngyu.carwashlife.application.carwash.api.dto.CarWashDetailDto;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory;
import me.ngyu.carwashlife.application.carwash.domain.CarWashRepresentativePolicy;
import me.ngyu.carwashlife.application.member.domain.Member;
import me.ngyu.carwashlife.common.exception.ApplicationException;
import me.ngyu.carwashlife.common.exception.ErrorCode;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashRepository;
import me.ngyu.carwashlife.infrastructure.persistence.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CarWashQueryService {

  private final CarWashRepository carWashRepository;
  private final MemberRepository memberRepository;
  private final CarWashRepresentativeService representativeService;
  private final Clock clock;

  @Transactional
  public CarWashDetailDto.Response findDetail(Long memberId, Long carWashId) {
    if (memberId == null) {
      throw new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
    Member member = memberRepository.findById(memberId)
                                    .orElseThrow(() -> new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED));
    if (!member.isActive()) {
      throw new ApplicationException(ErrorCode.ACCOUNT_NOT_ACTIVE);
    }
    CarWash carWash = carWashRepository.findByIdForUpdate(carWashId)
                                       .orElseThrow(() -> new ApplicationException(ErrorCode.CAR_WASH_NOT_FOUND));
    CarWashRepresentativePolicy.Selection selection = representativeService.selectAndReflect(carWash, OffsetDateTime.now(clock));
    CarWashHistory history = selection.history();
    return new CarWashDetailDto.Response(
            carWash.getId(), history.getId(), selection.confidence(), carWash.isOperatorManaged(),
            history.getBaseHistory() == null ? null : history.getBaseHistory().getId(), history.getChangedFields(),
            history.getVisitExperience(), history.getObservedAt(), history.getSubmittedAt(),
            history.getName(), history.getAddress(), history.getLatitude(), history.getLongitude(),
            history.getHighPressureWaterPrice(), history.getFoamLanceAvailability(), history.getFoamGunPrice(),
            history.getAirGunAvailability(), history.getAirGunPrice(), history.getVacuumAvailability(), history.getVacuumPrice(),
            history.getWashBayCount(), history.getDryingBayCount());
  }
}
