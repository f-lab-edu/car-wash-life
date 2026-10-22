package me.ngyu.carwashlife.application.carwash;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory;
import me.ngyu.carwashlife.application.carwash.domain.CarWashInformation;
import me.ngyu.carwashlife.application.carwash.domain.Coordinates;
import me.ngyu.carwashlife.application.carwash.domain.MapBounds;
import me.ngyu.carwashlife.application.carwash.domain.VisitExperience;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashHistoryRepository;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashPhotoRepository;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CarWashExplorationPersistenceIntegrationTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-07T12:00:00+09:00");

  @Autowired
  private CarWashRepository carWashRepository;
  @Autowired
  private CarWashHistoryRepository historyRepository;
  @Autowired
  private CarWashPhotoRepository photoRepository;
  @Autowired
  private EntityManager entityManager;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    photoRepository.deleteAllInBatch();
    jdbcTemplate.update("update car_wash set target_history_id = null");
    historyRepository.deleteAllInBatch();
    carWashRepository.deleteAllInBatch();
  }

  @Test
  @DisplayName("지도 조회는 ID 순서로 제한된 결과와 대표 정보를 함께 읽어 영속성 컨텍스트 밖에서도 주소를 제공한다.")
  void queryLimitsInIdOrderAndFetchesTheRepresentative() {
    Long firstId = save("첫 번째", "서울").getId();
    Long secondId = save("두 번째", "경기").getId();
    save("세 번째", "인천");
    entityManager.flush();
    entityManager.clear();

    MapBounds bounds = new MapBounds(new Coordinates(37.0, 127.0), new Coordinates(38.0, 128.0));
    List<CarWash> results = carWashRepository.findWithinBounds(bounds, PageRequest.of(0, 2));
    entityManager.clear();

    assertThat(results).extracting(CarWash::getId).containsExactly(firstId, secondId);
    assertThat(results).extracting(carWash -> carWash.getTargetHistory().getAddress()).containsExactly("서울", "경기");
  }

  private CarWash save(String name, String address) {
    Coordinates coordinates = new Coordinates(37.5, 127.5);
    CarWash carWash = carWashRepository.save(CarWash.create(name, coordinates, NOW));
    CarWashInformation information = CarWashInformation.builder().name(name).address(address).coordinates(coordinates).build();
    CarWashHistory history = historyRepository.save(CarWashHistory.register(carWash, 1L, VisitExperience.USED, NOW, NOW, information));
    carWash.selectRepresentativeHistory(history, NOW);
    return carWash;
  }
}
