package me.ngyu.carwashlife.infrastructure.persistence;

import java.util.List;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarWashHistoryRepository extends JpaRepository<CarWashHistory, Long> {

  List<CarWashHistory> findAllByCarWashId(Long carWashId);

}
