package me.ngyu.carwashlife.application.carwash;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

class AdjustableClock extends Clock {

  private final AtomicReference<Instant> instant;
  private final ZoneId zone;

  AdjustableClock(AtomicReference<Instant> instant, ZoneId zone) {
    this.instant = instant;
    this.zone = zone;
  }

  /**
   * 같은 시각 참조를 공유하는 시계들의 현재 시각을 변경한다.
   */
  void setInstant(Instant instant) {
    this.instant.set(instant);
  }

  /**
   * 시계에 설정된 시간대를 반환한다.
   */
  @Override
  public ZoneId getZone() {
    return zone;
  }

  /**
   * 현재 시각 참조를 공유하면서 시간대만 다른 시계를 반환한다.
   */
  @Override
  public Clock withZone(ZoneId zone) {
    return new AdjustableClock(instant, zone);
  }

  /**
   * 테스트에서 설정한 현재 시각을 반환한다.
   */
  @Override
  public Instant instant() {
    return instant.get();
  }
}
