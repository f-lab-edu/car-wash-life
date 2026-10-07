package me.ngyu.carwashlife.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CarWashRepository extends JpaRepository<CarWash, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select carWash from CarWash carWash where carWash.id = :id")
  Optional<CarWash> findByIdForUpdate(@Param("id") Long id);

}
