package me.ngyu.carwashlife.common.config;

import java.time.Clock;
import java.time.ZoneId;
import me.ngyu.carwashlife.application.carwash.domain.CarWashConfidencePolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CarWashConfig {

  @Bean
  public Clock clock() {
    return Clock.system(ZoneId.of("Asia/Seoul"));
  }

  @Bean
  public CarWashConfidencePolicy carWashConfidencePolicy() {
    return new CarWashConfidencePolicy();
  }
}
