package me.ngyu.carwashlife.application.carwash;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory;
import me.ngyu.carwashlife.application.carwash.domain.CarWashInformation;
import me.ngyu.carwashlife.application.carwash.domain.Coordinates;
import me.ngyu.carwashlife.application.carwash.domain.FacilityAvailability;
import me.ngyu.carwashlife.application.carwash.domain.VisitExperience;
import org.junit.jupiter.api.DisplayName;
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
  @DisplayName("대표 이력을 바꾸어 저장하면 원장의 새 정보와 이전 이력의 원본 정보를 함께 보존한다.")
  void changingRepresentativeHistoryPersistsNewMasterInformationAndPreservesOldSnapshot() {
    OffsetDateTime submittedAt = OffsetDateTime.parse("2026-10-05T10:00:00+09:00");
    CarWash carWash = CarWash.create("세차생활", new Coordinates(37.5, 127.0), submittedAt);
    CarWashHistory firstHistory = CarWashHistory.register(carWash, 1L, VisitExperience.NOT_USED, submittedAt, submittedAt,
                                                          CarWashInformation.builder()
                                                                            .name("세차생활")
                                                                            .address("서울시")
                                                                            .coordinates(new Coordinates(37.5, 127.0))
                                                                            .build());
    assertThat(carWash.getId()).isNull();
    assertThat(firstHistory.getId()).isNull();
    entityManager.persist(carWash);
    entityManager.persist(firstHistory);
    carWash.selectRepresentativeHistory(firstHistory, submittedAt);
    entityManager.flush();
    entityManager.clear();
    carWash = entityManager.find(CarWash.class, carWash.getId());
    firstHistory = entityManager.find(CarWashHistory.class, firstHistory.getId());
    assertThat(carWash.getTargetHistory()).isSameAs(firstHistory);
    assertThat(firstHistory.getCarWash()).isSameAs(carWash);
    CarWashHistory nextHistory = CarWashHistory.modify(carWash, firstHistory, 2L, VisitExperience.USED, submittedAt.plusHours(1), submittedAt.plusHours(1),
                                                       CarWashInformation.builder()
                                                                         .name("수정한 세차생활")
                                                                         .address("서울시")
                                                                         .coordinates(new Coordinates(37.6, 127.1))
                                                                         .highPressureWaterPrice(3000)
                                                                         .foamLanceAvailability(FacilityAvailability.AVAILABLE)
                                                                         .airGunAvailability(FacilityAvailability.AVAILABLE)
                                                                         .airGunPrice(0)
                                                                         .washBayCount(6)
                                                                         .dryingBayCount(10)
                                                                         .build());
    entityManager.persist(nextHistory);

    carWash.selectRepresentativeHistory(nextHistory, submittedAt.plusHours(1));
    entityManager.flush();
    entityManager.clear();

    CarWash reloaded = entityManager.find(CarWash.class, carWash.getId());
    assertThat(reloaded.getTargetHistory().getId()).isEqualTo(nextHistory.getId());
    assertThat(reloaded.getName()).isEqualTo("수정한 세차생활");
    assertThat(reloaded.getCoordinates()).isEqualTo(new Coordinates(37.6, 127.1));
    assertThat(reloaded.getTargetHistory().getCoordinates()).isEqualTo(reloaded.getCoordinates());
    assertThat(reloaded.getLatitude()).isEqualTo(37.6);
    assertThat(reloaded.getLongitude()).isEqualTo(127.1);
    assertThat(reloaded.getUpdatedAt()).isEqualTo(submittedAt.plusHours(1));
    assertThat(reloaded.getTargetHistory().getHighPressureWaterPrice()).isEqualTo(3000);
    assertThat(reloaded.getTargetHistory().getAirGunPrice()).isZero();
    assertThat(reloaded.getTargetHistory().getWashBayCount()).isEqualTo(6);
    Object[] masterCoordinates = (Object[]) entityManager.createNativeQuery("select latitude, longitude from car_wash where id = :id")
                                                         .setParameter("id", carWash.getId()).getSingleResult();
    Object[] historyCoordinates = (Object[]) entityManager.createNativeQuery("select latitude, longitude from car_wash_history where id = :id")
                                                          .setParameter("id", firstHistory.getId()).getSingleResult();
    assertThat(masterCoordinates).containsExactly(37.6, 127.1);
    assertThat(historyCoordinates).containsExactly(37.5, 127.0);
    CarWashHistory previous = entityManager.find(CarWashHistory.class, firstHistory.getId());
    assertThat(previous.getName()).isEqualTo("세차생활");
    assertThat(previous.getCoordinates()).isEqualTo(new Coordinates(37.5, 127.0));
    assertThat(previous.getLatitude()).isEqualTo(37.5);
    assertThat(previous.getLongitude()).isEqualTo(127.0);
    assertThat(previous.getHighPressureWaterPrice()).isNull();
    assertThat(previous.getFoamLanceAvailability()).isEqualTo(FacilityAvailability.UNKNOWN);
    assertThat(previous.getObservedAt()).isEqualTo(submittedAt);
    assertThat(previous.getCarWash().getId()).isEqualTo(reloaded.getId());
  }
}
