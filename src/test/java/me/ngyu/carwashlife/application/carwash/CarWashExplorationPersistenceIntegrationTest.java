package me.ngyu.carwashlife.application.carwash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.List;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.CarWashExplorationResult;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Limit;
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
  @DisplayName("지도 조회는 ID 순서로 DB에서 결과 수를 제한하고 대표 정보와 집계를 값으로 반환한다.")
  void queryLimitsInIdOrderAndReturnsRepresentativeAndAggregateValues() {
    Long firstId = save("첫 번째", "서울").getId();
    Long secondId = save("두 번째", "경기").getId();
    save("세 번째", "인천");
    entityManager.flush();
    entityManager.clear();

    MapBounds bounds = new MapBounds(new Coordinates(37.0, 127.0), new Coordinates(38.0, 128.0));
    List<CarWashExplorationResult> results = carWashRepository.findWithinBounds(bounds, Limit.of(2));
    entityManager.clear();

    assertThat(results).extracting(CarWashExplorationResult::carWashId).containsExactly(firstId, secondId);
    assertThat(results).extracting(CarWashExplorationResult::address).containsExactly("서울", "경기");
    assertThat(results).extracting(CarWashExplorationResult::distanceMeters).containsOnlyNulls();
  }

  @Test
  @DisplayName("지도 범위에 있는 세차장들의 비대표 제보를 포함해 건수와 최근 제출 시각을 각각 반환한다.")
  void explorationAggregatesAllReportsOnlyForCarWashesWithinBounds() {
    CarWash first = save("첫 번째", "서울");
    CarWash second = save("두 번째", "경기");
    Coordinates outside = new Coordinates(35.0, 129.0);
    CarWash excluded = carWashRepository.save(CarWash.create("조회 제외", outside, NOW));
    CarWashHistory excludedHistory = historyRepository.save(CarWashHistory.register(excluded, 1L, VisitExperience.USED, NOW, NOW,
                                                                                      CarWashInformation.builder().name("조회 제외").coordinates(outside).build()));
    excluded.selectRepresentativeHistory(excludedHistory, NOW);
    CarWashInformation corrected = CarWashInformation.builder().name("비대표 제보").coordinates(new Coordinates(35.5, 129.5)).build();
    historyRepository.save(CarWashHistory.modify(first, first.getTargetHistory(), 1L, VisitExperience.NOT_USED,
                                                  NOW.minusDays(2), NOW.plusHours(3), corrected));
    entityManager.flush();
    entityManager.clear();

    MapBounds bounds = new MapBounds(new Coordinates(37.0, 127.0), new Coordinates(38.0, 128.0));
    List<CarWashExplorationResult> activities = carWashRepository.findWithinBounds(bounds, Limit.of(100));

    assertThat(activities).extracting(CarWashExplorationResult::carWashId, CarWashExplorationResult::reportCount, activity -> activity.latestSubmittedAt().toInstant())
                          .containsExactlyInAnyOrder(tuple(first.getId(), 2L, NOW.plusHours(3).toInstant()), tuple(second.getId(), 1L, NOW.toInstant()));
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @DisplayName("동일 위치·매우 가까운 위치·극점·대척점에서도 거리는 유한한 음수 아닌 미터 값이다.")
  @CsvSource({"0,0,0,0,0", "0,0,0,0.000000001,0.000111195", "90,0,90,180,0", "0,0,0,180,20015114.442",
          "-90,-180,90,180,20015114.442", "37.5,127.5,-37.5,-52.5,20015114.442"})
  void distancesRemainFiniteAtNumericalBoundaries(double originLatitude, double originLongitude, double latitude, double longitude, double expectedMeters) {
    save("거리 검증", "주소", new Coordinates(latitude, longitude));
    entityManager.flush();
    entityManager.clear();
    MapBounds world = new MapBounds(new Coordinates(-90.0, -180.0), new Coordinates(90.0, 180.0));

    List<CarWashExplorationResult> results = carWashRepository.findWithinBoundsByDistance(world, new Coordinates(originLatitude, originLongitude), Limit.of(101));

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().distanceMeters()).isFinite().isNotNegative().isCloseTo(expectedMeters, within(0.01));
  }

  @Test
  @DisplayName("거리 조회도 대표 정보와 비대표 제보를 포함한 집계를 반환한다.")
  void distanceQueryPreservesRepresentativeInformationAndReportActivity() {
    CarWash carWash = save("대표 이름", "대표 주소");
    CarWashInformation corrected = CarWashInformation.builder().name("비대표 이름").coordinates(new Coordinates(35.5, 129.5)).build();
    historyRepository.save(CarWashHistory.modify(carWash, carWash.getTargetHistory(), 1L, VisitExperience.NOT_USED,
                                                  NOW.minusDays(2), NOW.plusHours(3), corrected));
    entityManager.flush();
    entityManager.clear();
    MapBounds bounds = new MapBounds(new Coordinates(37.0, 127.0), new Coordinates(38.0, 128.0));

    List<CarWashExplorationResult> results = carWashRepository.findWithinBoundsByDistance(bounds, new Coordinates(37.5, 127.5), Limit.of(101));

    assertThat(results).hasSize(1);
    CarWashExplorationResult result = results.getFirst();
    assertThat(result.name()).isEqualTo("대표 이름");
    assertThat(result.address()).isEqualTo("대표 주소");
    assertThat(result.latitude()).isEqualTo(37.5);
    assertThat(result.longitude()).isEqualTo(127.5);
    assertThat(result.reportCount()).isEqualTo(2);
    assertThat(result.latestSubmittedAt().toInstant()).isEqualTo(NOW.plusHours(3).toInstant());
    assertThat(result.createdAt().toInstant()).isEqualTo(NOW.toInstant());
    assertThat(result.distanceMeters()).isZero();
  }

  private CarWash save(String name, String address) {
    return save(name, address, new Coordinates(37.5, 127.5));
  }

  private CarWash save(String name, String address, Coordinates coordinates) {
    CarWash carWash = carWashRepository.save(CarWash.create(name, coordinates, NOW));
    CarWashInformation information = CarWashInformation.builder().name(name).address(address).coordinates(coordinates).build();
    CarWashHistory history = historyRepository.save(CarWashHistory.register(carWash, 1L, VisitExperience.USED, NOW, NOW, information));
    carWash.selectRepresentativeHistory(history, NOW);
    return carWash;
  }
}
