package me.ngyu.carwashlife.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.CarWashExplorationResult;
import me.ngyu.carwashlife.application.carwash.domain.Coordinates;
import me.ngyu.carwashlife.application.carwash.domain.MapBounds;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CarWashRepository extends JpaRepository<CarWash, Long> {

  // Haversine 공식으로 미터 단위의 구면 거리를 계산하고, 부동소수점 오차가 역삼각함수의 정의역을 벗어나지 않게 한다.
  String DISTANCE_METERS = """
          (2.0 * 6371008.8 * asin(sqrt(least(1.0, greatest(0.0,
            power(sin(radians(carWash.coordinates.latitude - :#{#current.getLatitude()}) / 2.0), 2)
            + cos(radians(:#{#current.getLatitude()})) * cos(radians(carWash.coordinates.latitude))
            * power(sin(radians(carWash.coordinates.longitude - :#{#current.getLongitude()}) / 2.0), 2)
          )))))
          """;

  // 현재 위치 유무와 관계없이 같은 조회 조건과 제보 집계 기준을 사용한다.
  String EXPLORATION_FROM_WHERE_GROUP_BY = """
          from CarWashHistory history
          join history.carWash carWash
          join carWash.targetHistory representative
          where carWash.coordinates.latitude between :#{#bounds.southwest().getLatitude()} and :#{#bounds.northeast().getLatitude()}
            and carWash.coordinates.longitude between :#{#bounds.southwest().getLongitude()} and :#{#bounds.northeast().getLongitude()}
          group by carWash.id, carWash.name, representative.address, carWash.coordinates.latitude, carWash.coordinates.longitude, carWash.createdAt
          """;

  @Query("""
          select carWash.id, carWash.name, representative.address, carWash.coordinates.latitude, carWash.coordinates.longitude,
                 count(history), max(history.submittedAt), carWash.createdAt, cast(null as Double)
          """ + EXPLORATION_FROM_WHERE_GROUP_BY + """
          order by carWash.id asc
          """)
  List<CarWashExplorationResult> findWithinBounds(@Param("bounds") MapBounds bounds, Limit limit);

  @Query("""
          select carWash.id, carWash.name, representative.address, carWash.coordinates.latitude, carWash.coordinates.longitude,
                 count(history), max(history.submittedAt), carWash.createdAt,
          """ + DISTANCE_METERS + EXPLORATION_FROM_WHERE_GROUP_BY + """
          order by
          """ + DISTANCE_METERS + " asc, carWash.id asc")
  List<CarWashExplorationResult> findWithinBoundsByDistance(@Param("bounds") MapBounds bounds, @Param("current") Coordinates current, Limit limit);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select carWash from CarWash carWash where carWash.id = :id")
  Optional<CarWash> findByIdForUpdate(@Param("id") Long id);

}
