package me.ngyu.carwashlife.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import me.ngyu.carwashlife.application.carwash.domain.CarWashPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CarWashPhotoRepository extends JpaRepository<CarWashPhoto, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select photo from CarWashPhoto photo where photo.id = :id")
  Optional<CarWashPhoto> findByIdForUpdate(@Param("id") Long id);
}
