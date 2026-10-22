package me.ngyu.carwashlife.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.MapBounds;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CarWashRepository extends JpaRepository<CarWash, Long> {

  @Query("""
          select carWash from CarWash carWash
          join fetch carWash.targetHistory
          where carWash.coordinates.latitude between :#{#bounds.southwest().getLatitude()} and :#{#bounds.northeast().getLatitude()}
            and carWash.coordinates.longitude between :#{#bounds.southwest().getLongitude()} and :#{#bounds.northeast().getLongitude()}
          order by carWash.id asc
          """)
  List<CarWash> findWithinBounds(@Param("bounds") MapBounds bounds, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select carWash from CarWash carWash where carWash.id = :id")
  Optional<CarWash> findByIdForUpdate(@Param("id") Long id);

}
