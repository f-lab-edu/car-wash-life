package me.ngyu.carwashlife.infrastructure.persistence;

import java.util.Optional;
import me.ngyu.carwashlife.infrastructure.persistence.entity.MemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<MemberEntity, Long> {

  boolean existsByEmail(String email);

  Optional<MemberEntity> findByEmail(String email);
}
