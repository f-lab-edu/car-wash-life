package me.ngyu.carwashlife.infrastructure.persistence;

import java.util.Optional;
import me.ngyu.carwashlife.application.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

  boolean existsByEmail(String email);

  Optional<Member> findByEmail(String email);
}
