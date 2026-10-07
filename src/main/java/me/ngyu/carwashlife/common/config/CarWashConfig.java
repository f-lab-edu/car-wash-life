package me.ngyu.carwashlife.common.config;

import java.time.Clock;
import java.time.ZoneId;
import me.ngyu.carwashlife.application.carwash.domain.CarWashConfidencePolicy;
import me.ngyu.carwashlife.application.carwash.domain.CarWashRepresentativePolicy;
import me.ngyu.carwashlife.application.carwash.service.CarWashPhotoStorage;
import me.ngyu.carwashlife.infrastructure.storage.LocalCarWashPhotoStorage;
import org.springframework.beans.factory.annotation.Value;
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

  @Bean
  public CarWashRepresentativePolicy carWashRepresentativePolicy(CarWashConfidencePolicy confidencePolicy) {
    return new CarWashRepresentativePolicy(confidencePolicy);
  }

  @Bean
  public CarWashPhotoStorage carWashPhotoStorage(@Value("${carwash.photo.root:./data/car-wash/photos}") String root) {
    return new LocalCarWashPhotoStorage(root);
  }
}
