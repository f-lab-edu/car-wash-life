package me.ngyu.carwashlife.application.carwash.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.ngyu.carwashlife.application.carwash.api.dto.CarWashDetailDto;
import me.ngyu.carwashlife.application.carwash.api.dto.CarWashExplorationDto;
import me.ngyu.carwashlife.application.carwash.api.dto.CarWashSearchDto;
import me.ngyu.carwashlife.application.carwash.api.dto.ModifyCarWashDto;
import me.ngyu.carwashlife.application.carwash.api.dto.RegisterCarWashDto;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.CarWashExploration;
import me.ngyu.carwashlife.application.carwash.domain.CarWashExplorationResult;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory;
import me.ngyu.carwashlife.application.carwash.domain.CarWashInformation;
import me.ngyu.carwashlife.application.carwash.domain.CarWashModification;
import me.ngyu.carwashlife.application.carwash.domain.CarWashPhoto;
import me.ngyu.carwashlife.application.carwash.domain.CarWashRegistration;
import me.ngyu.carwashlife.application.carwash.domain.CarWashSearch;
import me.ngyu.carwashlife.application.carwash.domain.Coordinates;
import me.ngyu.carwashlife.application.member.domain.Member;
import me.ngyu.carwashlife.common.exception.ApplicationException;
import me.ngyu.carwashlife.common.exception.ErrorCode;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashHistoryRepository;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashRepository;
import me.ngyu.carwashlife.infrastructure.persistence.MemberRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CarWashService {

  // 지도 응답 크기를 제한한다. 추가 결과 여부는 한 건 더 조회해 판단한다.
  private static final int EXPLORATION_LIMIT = 100;

  // 검색 응답 크기를 제한한다. 추가 결과 여부는 한 건 더 조회해 판단한다.
  private static final int SEARCH_LIMIT = 100;

  private final CarWashRepository carWashRepository;
  private final CarWashHistoryRepository historyRepository;
  private final MemberRepository memberRepository;
  private final CarWashRepresentativeService representativeService;
  private final CarWashPhotoService photoService;
  private final Clock clock;

  /**
   * 세차장과 최초 제보를 등록하고 사진 연결과 대표 정보 반영을 처리한다.
   */
  @Transactional
  public RegisterCarWashDto.Response register(Long memberId, CarWashRegistration registration) {
    Member member = requireActiveMember(memberId);

    List<CarWashPhoto> photos = photoService.lockPendingPhotos(member.getId(), registration.photoIds());
    OffsetDateTime submittedAt = OffsetDateTime.now(clock);

    CarWash carWash;
    CarWashHistory history;
    try {
      CarWashInformation information = registration.information();
      carWash = CarWash.create(information.name(), information.coordinates(), submittedAt);
      history = CarWashHistory.register(carWash, member.getId(), registration.visitExperience(), registration.observedAt(), submittedAt, information,
                                        photos.stream().map(CarWashPhoto::getId).toList());
    } catch (IllegalArgumentException exception) {
      throw new ApplicationException(ErrorCode.VALIDATION_ERROR, exception);
    }

    int confidence = history.calculateConfidence(submittedAt);
    carWashRepository.save(carWash);
    historyRepository.save(history);
    photoService.attachPhotos(photos, history);
    carWash.selectRepresentativeHistory(history, submittedAt);

    return new RegisterCarWashDto.Response(carWash.getId(), history.getId(), confidence, submittedAt);
  }

  /**
   * 기존 세차장에 수정 제보를 추가하고 대표 정보를 다시 선정한다.
   */
  @Transactional
  public ModifyCarWashDto.Response modify(Long memberId, Long carWashId, CarWashModification modification) {
    Member member = requireActiveMember(memberId);

    CarWash carWash = carWashRepository.findByIdForUpdate(carWashId)
                                       .orElseThrow(() -> new ApplicationException(ErrorCode.CAR_WASH_NOT_FOUND));
    CarWashHistory baseHistory = historyRepository.findById(modification.baseHistoryId())
                                                  .orElseThrow(() -> new ApplicationException(ErrorCode.CAR_WASH_HISTORY_NOT_FOUND));
    List<CarWashPhoto> photos = photoService.lockPendingPhotos(member.getId(), modification.photoIds());
    OffsetDateTime submittedAt = OffsetDateTime.now(clock);

    CarWashHistory history;
    try {
      history = CarWashHistory.modify(carWash, baseHistory, member.getId(), modification.visitExperience(), modification.observedAt(), submittedAt, modification.information(),
                                      photos.stream().map(CarWashPhoto::getId).toList());
    } catch (IllegalArgumentException exception) {
      throw new ApplicationException(ErrorCode.VALIDATION_ERROR, exception);
    }

    int confidence = history.calculateConfidence(submittedAt);
    historyRepository.save(history);
    photoService.attachPhotos(photos, history);
    representativeService.selectAndReflect(carWash, submittedAt);

    return new ModifyCarWashDto.Response(carWash.getId(), history.getId(), baseHistory.getId(), history.getChangedFields(), confidence, submittedAt);
  }

  /**
   * 요청한 회원이 존재하고 활성 상태인지 확인한다.
   */
  private Member requireActiveMember(Long memberId) {
    if (memberId == null) {
      throw new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    Member member = memberRepository.findById(memberId)
                                    .orElseThrow(() -> new ApplicationException(ErrorCode.AUTHENTICATION_REQUIRED));
    if (!member.isActive()) {
      throw new ApplicationException(ErrorCode.ACCOUNT_NOT_ACTIVE);
    }
    return member;
  }

  /**
   * 지도 범위의 세차장을 현재 위치 유무에 따른 순서로 조회하고 추가 결과 여부를 반환한다.
   */
  @Transactional(readOnly = true)
  public CarWashExplorationDto.Response findWithinBounds(CarWashExploration exploration) {
    Coordinates currentCoordinates = exploration.currentCoordinates();
    List<CarWashExplorationResult> rows = currentCoordinates == null
                                         ? carWashRepository.findWithinBounds(exploration.bounds(), Limit.of(EXPLORATION_LIMIT + 1))
                                         : carWashRepository.findWithinBoundsByDistance(exploration.bounds(), currentCoordinates, Limit.of(EXPLORATION_LIMIT + 1));

    List<CarWashExplorationResult> visibleRows = rows.subList(0, Math.min(rows.size(), EXPLORATION_LIMIT));
    List<CarWashExplorationDto.CarWashSummary> summaries = visibleRows.stream().map(CarWashExplorationDto.CarWashSummary::from).toList();

    return new CarWashExplorationDto.Response(summaries, rows.size() > EXPLORATION_LIMIT);
  }

  /**
   * 현재 대표 이름에 검색어가 포함된 세차장을 현재 위치 유무에 따른 순서로 조회한다.
   */
  @Transactional(readOnly = true)
  public CarWashSearchDto.Response search(CarWashSearch search) {
    Coordinates currentCoordinates = search.currentCoordinates();
    List<CarWashExplorationResult> rows = currentCoordinates == null
                                         ? carWashRepository.findByNameContaining(search.keyword(), Limit.of(SEARCH_LIMIT + 1))
                                         : carWashRepository.findByNameContainingByDistance(search.keyword(), currentCoordinates, Limit.of(SEARCH_LIMIT + 1));
    List<CarWashExplorationResult> visibleRows = rows.subList(0, Math.min(rows.size(), SEARCH_LIMIT));
    List<CarWashExplorationDto.CarWashSummary> summaries = visibleRows.stream().map(CarWashExplorationDto.CarWashSummary::from).toList();
    return new CarWashSearchDto.Response(summaries, rows.size() > SEARCH_LIMIT);
  }

  /**
   * 현재 대표 제보의 상세 정보와 조회 시점의 신뢰도, 사진을 반환한다.
   */
  @Transactional(readOnly = true)
  public CarWashDetailDto.Response findDetail(Long carWashId) {
    CarWash carWash = carWashRepository.findById(carWashId)
                                       .orElseThrow(() -> new ApplicationException(ErrorCode.CAR_WASH_NOT_FOUND));

    int confidence = carWash.getTargetHistory().calculateConfidence(OffsetDateTime.now(clock));
    return CarWashDetailDto.Response.from(carWash, confidence, photoService.snapshots(carWash.getTargetHistory()));
  }
}
