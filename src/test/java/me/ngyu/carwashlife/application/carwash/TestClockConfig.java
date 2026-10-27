package me.ngyu.carwashlife.application.carwash;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
class TestClockConfig {

  private static final OffsetDateTime INITIAL_TIME = OffsetDateTime.parse("2026-10-07T12:00:00+09:00");

  /**
   * 테스트에서 시각을 조절할 수 있는 기본 시계를 제공한다.
   */
  @Bean
  @Primary
  AdjustableClock adjustableClock() {
    return new AdjustableClock(new AtomicReference<>(INITIAL_TIME.toInstant()), ZoneId.of("Asia/Seoul"));
  }
}
