package me.ngyu.carwashlife.application.carwash.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.ngyu.carwashlife.application.carwash.api.dto.CarWashDetailDto;
import me.ngyu.carwashlife.application.carwash.api.dto.CarWashExplorationDto;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.MapBounds;
import me.ngyu.carwashlife.common.exception.ApplicationException;
import me.ngyu.carwashlife.common.exception.ErrorCode;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CarWashQueryService {

  // 지도 응답 크기를 제한한다. 추가 결과 여부는 한 건 더 조회해 판단한다.
  private static final int EXPLORATION_LIMIT = 100;

  private final CarWashRepository carWashRepository;
  private final CarWashPhotoService photoService;
  private final Clock clock;

  @Transactional(readOnly = true)
  public CarWashExplorationDto.Response findWithinBounds(MapBounds bounds) {
    List<CarWash> carWashes = carWashRepository.findWithinBounds(bounds, PageRequest.of(0, EXPLORATION_LIMIT + 1));
    return new CarWashExplorationDto.Response(carWashes.stream().limit(EXPLORATION_LIMIT).map(CarWashExplorationDto.CarWashSummary::from).toList(),
                                              carWashes.size() > EXPLORATION_LIMIT);
  }

  @Transactional(readOnly = true)
  public CarWashDetailDto.Response findDetail(Long carWashId) {
    CarWash carWash = carWashRepository.findById(carWashId)
                                       .orElseThrow(() -> new ApplicationException(ErrorCode.CAR_WASH_NOT_FOUND));
    int confidence = carWash.getTargetHistory().calculateConfidence(OffsetDateTime.now(clock));
    return CarWashDetailDto.Response.from(carWash, confidence, photoService.snapshots(carWash.getTargetHistory()));
  }
}
