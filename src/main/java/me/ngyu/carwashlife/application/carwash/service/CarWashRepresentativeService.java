package me.ngyu.carwashlife.application.carwash.service;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.CarWashRepresentativePolicy;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashHistoryRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CarWashRepresentativeService {

  private final CarWashHistoryRepository historyRepository;
  private final CarWashRepresentativePolicy representativePolicy;

  public CarWashRepresentativePolicy.Selection selectAndReflect(CarWash carWash, OffsetDateTime evaluatedAt) {
    CarWashRepresentativePolicy.Selection selection = representativePolicy.select(
            historyRepository.findAllByCarWashId(carWash.getId()), evaluatedAt);
    carWash.selectRepresentativeHistory(selection.history(), evaluatedAt);
    return selection;
  }
}
