package me.ngyu.carwashlife.application.carwash;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistoryType;
import me.ngyu.carwashlife.application.carwash.domain.FacilityAvailability;
import me.ngyu.carwashlife.application.carwash.domain.VisitExperience;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CarWashPersistenceIntegrationTest {

  @Autowired
  private EntityManager entityManager;

  @Test
  void changingRepresentativeHistoryPersistsNewMasterInformationAndPreservesOldSnapshot() {
    OffsetDateTime submittedAt = OffsetDateTime.parse("2026-10-05T10:00:00+09:00");
    CarWash carWash = CarWash.create("세차생활", 37.5, 127.0, submittedAt);
    entityManager.persist(carWash);
    CarWashHistory firstHistory = CarWashHistory.builder()
                                                .carWash(carWash)
                                                .memberId(1L)
                                                .type(CarWashHistoryType.REGISTRATION)
                                                .visitExperience(VisitExperience.NOT_USED)
                                                .observedAt(submittedAt)
                                                .submittedAt(submittedAt)
                                                .name("세차생활")
                                                .address("서울시")
                                                .latitude(37.5)
                                                .longitude(127.0)
                                                .build();
    entityManager.persist(firstHistory);
    carWash.selectRepresentativeHistory(firstHistory, submittedAt);
    entityManager.flush();
    CarWashHistory nextHistory = CarWashHistory.builder()
                                               .carWash(carWash)
                                               .baseHistory(firstHistory)
                                               .memberId(2L)
                                               .type(CarWashHistoryType.MODIFICATION)
                                               .visitExperience(VisitExperience.USED)
                                               .observedAt(submittedAt.plusHours(1))
                                               .submittedAt(submittedAt.plusHours(1))
                                               .name("수정한 세차생활")
                                               .address("서울시")
                                               .latitude(37.6)
                                               .longitude(127.1)
                                               .foamLanceAvailability(FacilityAvailability.AVAILABLE)
                                               .highPressureWaterPrice(3000)
                                               .airGunAvailability(FacilityAvailability.AVAILABLE)
                                               .airGunPrice(0)
                                               .washBayCount(6)
                                               .dryingBayCount(10)
                                               .build();
    entityManager.persist(nextHistory);

    carWash.selectRepresentativeHistory(nextHistory, submittedAt.plusHours(1));
    entityManager.flush();
    entityManager.clear();

    CarWash reloaded = entityManager.find(CarWash.class, carWash.getId());
    assertThat(reloaded.getTargetHistory().getId()).isEqualTo(nextHistory.getId());
    assertThat(reloaded.getName()).isEqualTo("수정한 세차생활");
    assertThat(reloaded.getLatitude()).isEqualTo(37.6);
    assertThat(reloaded.getLongitude()).isEqualTo(127.1);
    assertThat(reloaded.getUpdatedAt()).isEqualTo(submittedAt.plusHours(1));
    assertThat(reloaded.getTargetHistory().getHighPressureWaterPrice()).isEqualTo(3000);
    assertThat(reloaded.getTargetHistory().getAirGunPrice()).isZero();
    assertThat(reloaded.getTargetHistory().getWashBayCount()).isEqualTo(6);
    CarWashHistory previous = entityManager.find(CarWashHistory.class, firstHistory.getId());
    assertThat(previous.getName()).isEqualTo("세차생활");
    assertThat(previous.getLatitude()).isEqualTo(37.5);
    assertThat(previous.getHighPressureWaterPrice()).isNull();
    assertThat(previous.getFoamLanceAvailability()).isEqualTo(FacilityAvailability.UNKNOWN);
    assertThat(previous.getObservedAt()).isEqualTo(submittedAt);
    assertThat(previous.getCarWash().getId()).isEqualTo(reloaded.getId());
  }
}
