package me.ngyu.carwashlife.infrastructure.persistence;

import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarWashHistoryRepository extends JpaRepository<CarWashHistory, Long> {
}
