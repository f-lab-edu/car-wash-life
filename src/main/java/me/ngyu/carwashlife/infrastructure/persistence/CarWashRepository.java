package me.ngyu.carwashlife.infrastructure.persistence;

import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CarWashRepository extends JpaRepository<CarWash, Long> {
}
