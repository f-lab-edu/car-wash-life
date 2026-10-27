package me.ngyu.carwashlife.application.carwash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory;
import me.ngyu.carwashlife.application.carwash.domain.CarWashInformation;
import me.ngyu.carwashlife.application.carwash.domain.Coordinates;
import me.ngyu.carwashlife.application.carwash.domain.VisitExperience;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashHistoryRepository;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashPhotoRepository;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.MethodArgumentNotValidException;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CarWashSearchApiIntegrationTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-07T12:00:00+09:00");

  private final MockMvc mockMvc;
  private final CarWashRepository carWashRepository;
  private final CarWashHistoryRepository historyRepository;
  private final CarWashPhotoRepository photoRepository;
  private final JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    photoRepository.deleteAllInBatch();
    jdbcTemplate.update("update car_wash set target_history_id = null");
    historyRepository.deleteAllInBatch();
    carWashRepository.deleteAllInBatch();
  }

  @Test
  @DisplayName("비회원도 대표 이름의 일부로 검색하고 ID 순서의 대표 정보와 제보 집계를 받는다.")
  void anonymousSearchReturnsRepresentativeInformationAndReportActivity() throws Exception {
    CarWash first = save("서울 세차생활");
    CarWash second = save("세차생활 판교점");
    save("이름 불일치");
    CarWashInformation information = CarWashInformation.builder().name("비대표 이름").coordinates(new Coordinates(37.5, 127.0)).build();
    historyRepository.save(CarWashHistory.modify(first, first.getTargetHistory(), 2L, VisitExperience.NOT_USED, NOW.minusDays(2), NOW.plusHours(1), information));

    mockMvc.perform(get("/car-washes/search").param("keyword", "세차생활"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.carWashes.length()").value(2))
           .andExpect(jsonPath("$.carWashes[0].carWashId").value(first.getId()))
           .andExpect(jsonPath("$.carWashes[0].name").value("서울 세차생활"))
           .andExpect(jsonPath("$.carWashes[0].address").value("서울시"))
           .andExpect(jsonPath("$.carWashes[0].latitude").value(37.5))
           .andExpect(jsonPath("$.carWashes[0].longitude").value(127.0))
           .andExpect(jsonPath("$.carWashes[0].reportCount").value(2))
           .andExpect(result -> {
             String body = result.getResponse().getContentAsString();
             String latestSubmittedAt = JsonPath.read(body, "$.carWashes[0].latestSubmittedAt");
             String createdAt = JsonPath.read(body, "$.carWashes[0].createdAt");
             assertThat(OffsetDateTime.parse(latestSubmittedAt).toInstant()).isEqualTo(NOW.plusHours(1).toInstant());
             assertThat(OffsetDateTime.parse(createdAt).toInstant()).isEqualTo(NOW.toInstant());
           })
           .andExpect(jsonPath("$.carWashes[0].distanceMeters").isEmpty())
           .andExpect(jsonPath("$.carWashes[1].carWashId").value(second.getId()))
           .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  @DisplayName("검색어 앞뒤 공백을 제거한 값으로 이름을 검색한다.")
  void trimsKeywordBeforeSearching() throws Exception {
    save("세차생활");

    mockMvc.perform(get("/car-washes/search").param("keyword", " \t세차\n"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.carWashes.length()").value(1));
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @ValueSource(strings = {"", " ", "\t\n", "\u2003"})
  @DisplayName("빈 검색어나 공백뿐인 검색어는 입력 오류로 응답한다.")
  void rejectsBlankKeyword(String keyword) throws Exception {
    mockMvc.perform(get("/car-washes/search").param("keyword", keyword))
           .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(MethodArgumentNotValidException.class))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("검색어를 생략하면 입력 오류로 응답한다.")
  void rejectsMissingKeyword() throws Exception {
    mockMvc.perform(get("/car-washes/search"))
           .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(MethodArgumentNotValidException.class))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("100자를 넘는 검색어는 입력 오류로 응답한다.")
  void rejectsExcessiveKeywordLength() throws Exception {
    mockMvc.perform(get("/car-washes/search").param("keyword", "가".repeat(101)))
           .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(MethodArgumentNotValidException.class))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("앞뒤 공백을 제외한 검색어가 100자이면 요청을 허용한다.")
  void acceptsMaximumKeywordLengthAfterStripping() throws Exception {
    String keyword = "가".repeat(100);
    CarWash carWash = save(keyword);

    mockMvc.perform(get("/car-washes/search").param("keyword", "\u2003" + keyword + "\u2003"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.carWashes.length()").value(1))
           .andExpect(jsonPath("$.carWashes[0].carWashId").value(carWash.getId()));
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @ValueSource(strings = {"%", "_", "'"})
  @DisplayName("검색어의 특수문자는 검색 패턴이 아닌 이름에 포함된 문자로 처리한다.")
  void searchesSpecialCharactersLiterally(String keyword) throws Exception {
    CarWash matching = save("세차" + keyword + "생활");
    save("세차생활");

    mockMvc.perform(get("/car-washes/search").param("keyword", keyword))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.carWashes.length()").value(1))
           .andExpect(jsonPath("$.carWashes[0].carWashId").value(matching.getId()));
  }

  @Test
  @DisplayName("비대표 이력에만 존재하는 이름은 검색 결과에 포함하지 않는다.")
  void ignoresNamesFromNonRepresentativeHistories() throws Exception {
    CarWash carWash = save("대표 이름");
    CarWashInformation information = CarWashInformation.builder().name("과거 제보 이름").coordinates(new Coordinates(37.5, 127.0)).build();
    historyRepository.save(CarWashHistory.modify(carWash, carWash.getTargetHistory(), 2L, VisitExperience.NOT_USED, NOW.minusDays(2), NOW.plusHours(1), information));

    mockMvc.perform(get("/car-washes/search").param("keyword", "과거 제보"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.carWashes").isEmpty()).andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  @DisplayName("대표가 변경되면 새 대표 이름으로 검색하고 이전 이름은 결과에서 제외한다.")
  void searchesUpdatedRepresentativeName() throws Exception {
    CarWash carWash = save("이전 이름");
    CarWashInformation information = CarWashInformation.builder().name("새 대표 이름").coordinates(new Coordinates(37.5, 127.0)).build();
    CarWashHistory history = historyRepository.save(CarWashHistory.modify(carWash, carWash.getTargetHistory(), 2L, VisitExperience.USED, NOW, NOW.plusHours(1), information));
    carWash.selectRepresentativeHistory(history, NOW.plusHours(1));

    mockMvc.perform(get("/car-washes/search").param("keyword", "새 대표"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.carWashes[0].carWashId").value(carWash.getId()));
    mockMvc.perform(get("/car-washes/search").param("keyword", "이전 이름"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.carWashes").isEmpty());
  }

  @Test
  @DisplayName("영문 검색어의 대소문자를 구분한다.")
  void searchesCaseSensitively() throws Exception {
    save("Car Wash");

    mockMvc.perform(get("/car-washes/search").param("keyword", "car"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.carWashes").isEmpty());
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @CsvSource({"0,0,false", "100,100,false", "101,100,true"})
  @DisplayName("검색 결과는 최대 100개를 반환하고 추가 결과가 있을 때만 알린다.")
  void limitsSearchResultsAndReportsMore(int count, int visibleCount, boolean hasMore) throws Exception {
    for (int index = 0; index < count; index++) {
      save("세차생활 " + index);
    }

    mockMvc.perform(get("/car-washes/search").param("keyword", "세차생활"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.carWashes.length()").value(visibleCount))
           .andExpect(jsonPath("$.hasMore").value(hasMore));
  }

  @Test
  @DisplayName("현재 위치가 있으면 이름이 일치하는 세차장을 거리순과 같은 거리의 ID순으로 반환한다.")
  void sortsMatchingNamesByDistanceAndIdAndReturnsActivity() throws Exception {
    CarWash farther = save("세차생활 먼 지점", new Coordinates(0.0, 2.0));
    CarWash firstTie = save("세차생활 동쪽", new Coordinates(0.0, 1.0));
    CarWash secondTie = save("세차생활 서쪽", new Coordinates(0.0, -1.0));
    CarWash coincident = save("세차생활 현재 위치", new Coordinates(0.0, 0.0));
    save("다른 이름", new Coordinates(0.0, 0.0));
    CarWashInformation information = CarWashInformation.builder().name("과거 제보").coordinates(new Coordinates(0.0, 0.0)).build();
    historyRepository.save(CarWashHistory.modify(firstTie, firstTie.getTargetHistory(), 2L, VisitExperience.NOT_USED, NOW.minusDays(2), NOW.plusHours(1), information));

    mockMvc.perform(get("/car-washes/search").param("keyword", "세차생활").param("currentLatitude", "0").param("currentLongitude", "0"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.carWashes.length()").value(4))
           .andExpect(result -> {
             String body = result.getResponse().getContentAsString();
             List<Number> ids = JsonPath.read(body, "$.carWashes[*].carWashId");
             assertThat(ids.stream().map(Number::longValue).toList()).containsExactly(coincident.getId(), firstTie.getId(), secondTie.getId(), farther.getId());
             Number zero = JsonPath.read(body, "$.carWashes[0].distanceMeters");
             Number oneDegree = JsonPath.read(body, "$.carWashes[1].distanceMeters");
             assertThat(zero.doubleValue()).isZero();
             assertThat(oneDegree.doubleValue()).isCloseTo(111195.0802, within(0.01));
             String latestSubmittedAt = JsonPath.read(body, "$.carWashes[1].latestSubmittedAt");
             assertThat(OffsetDateTime.parse(latestSubmittedAt).toInstant()).isEqualTo(NOW.plusHours(1).toInstant());
           })
           .andExpect(jsonPath("$.carWashes[1].reportCount").value(2))
           .andExpect(jsonPath("$.carWashes[1].longitude").value(1.0))
           .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  @DisplayName("대표가 변경되면 현재 대표 좌표로 거리를 계산하고 정렬한다.")
  void updatedRepresentativeCoordinatesDetermineDistance() throws Exception {
    CarWash moved = save("세차생활 이동 지점", new Coordinates(0.0, 0.0));
    CarWash nearby = save("세차생활 가까운 지점", new Coordinates(0.0, 1.0));
    CarWashInformation information = CarWashInformation.builder().name("세차생활 새 위치").coordinates(new Coordinates(0.0, 2.0)).build();
    CarWashHistory history = historyRepository.save(CarWashHistory.modify(moved, moved.getTargetHistory(), 2L, VisitExperience.USED, NOW, NOW.plusHours(1), information));
    moved.selectRepresentativeHistory(history, NOW.plusHours(1));

    mockMvc.perform(get("/car-washes/search").param("keyword", "세차생활").param("currentLatitude", "0").param("currentLongitude", "0"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.carWashes[0].carWashId").value(nearby.getId()))
           .andExpect(jsonPath("$.carWashes[1].carWashId").value(moved.getId()))
           .andExpect(jsonPath("$.carWashes[1].name").value("세차생활 새 위치"))
           .andExpect(jsonPath("$.carWashes[1].longitude").value(2.0))
           .andExpect(result -> {
             Number distance = JsonPath.read(result.getResponse().getContentAsString(), "$.carWashes[1].distanceMeters");
             assertThat(distance.doubleValue()).isCloseTo(222390.1605, within(0.01));
           });
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @ValueSource(ints = {100, 101})
  @DisplayName("거리 정렬 후 100개로 제한하므로 마지막 ID인 최근접 세차장도 포함한다.")
  void distanceOrderPrecedesResultLimit(int count) throws Exception {
    List<Long> fartherIds = new ArrayList<>();
    for (int index = 0; index < count - 1; index++) {
      fartherIds.add(save("세차생활 먼 지점 " + index, new Coordinates(0.0, 2.0)).getId());
    }
    Long nearestId = save("세차생활 가까운 지점", new Coordinates(0.0, 0.0)).getId();
    List<Long> expectedIds = new ArrayList<>();
    expectedIds.add(nearestId);
    expectedIds.addAll(fartherIds.subList(0, 99));

    mockMvc.perform(get("/car-washes/search").param("keyword", "세차생활").param("currentLatitude", "0").param("currentLongitude", "0"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.carWashes.length()").value(100))
           .andExpect(jsonPath("$.hasMore").value(count > 100))
           .andExpect(result -> {
             List<Number> ids = JsonPath.read(result.getResponse().getContentAsString(), "$.carWashes[*].carWashId");
             assertThat(ids.stream().map(Number::longValue).toList()).containsExactlyElementsOf(expectedIds);
           });
  }

  @Test
  @DisplayName("현재 위치가 있어도 이름이 일치하는 세차장이 없으면 빈 결과를 반환한다.")
  void emptyDistanceSearchReturnsNoMoreResults() throws Exception {
    save("다른 이름", new Coordinates(0.0, 0.0));

    mockMvc.perform(get("/car-washes/search").param("keyword", "세차생활").param("currentLatitude", "0").param("currentLongitude", "0"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.carWashes").isEmpty())
           .andExpect(jsonPath("$.hasMore").value(false));
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @CsvSource({"text,127", "37,text", "NaN,127", "37,NaN", "Infinity,127", "-Infinity,127", "37,Infinity", "37,-Infinity",
          "-91,127", "91,127", "37,-181", "37,181"})
  @DisplayName("현재 위치의 문자·유한하지 않은 값·범위 초과는 요청 검증 오류를 반환한다.")
  void invalidCurrentCoordinatesAreRejectedBeforeDomainConversion(String latitude, String longitude) throws Exception {
    mockMvc.perform(get("/car-washes/search").param("keyword", "세차생활").param("currentLatitude", latitude).param("currentLongitude", longitude))
           .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(MethodArgumentNotValidException.class))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @ValueSource(strings = {"currentLatitude", "currentLongitude"})
  @DisplayName("현재 위치의 위도와 경도 중 하나만 입력하면 검증 오류를 반환한다.")
  void incompleteCurrentCoordinatesAreRejected(String parameter) throws Exception {
    mockMvc.perform(get("/car-washes/search").param("keyword", "세차생활").param(parameter, "37"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @CsvSource({"-90,-180", "-90,180", "90,-180", "90,180"})
  @DisplayName("현재 위치의 위도·경도 최솟값과 최댓값은 허용한다.")
  void acceptsCurrentCoordinateRangeEndpoints(double latitude, double longitude) throws Exception {
    CarWash matching = save("세차생활", new Coordinates(latitude, longitude));

    mockMvc.perform(get("/car-washes/search").param("keyword", "세차생활").param("currentLatitude", Double.toString(latitude)).param("currentLongitude", Double.toString(longitude)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.carWashes[0].carWashId").value(matching.getId()))
           .andExpect(jsonPath("$.carWashes[0].distanceMeters").value(0.0));
  }

  private CarWash save(String name) {
    return save(name, new Coordinates(37.5, 127.0));
  }

  private CarWash save(String name, Coordinates coordinates) {
    CarWash carWash = carWashRepository.save(CarWash.create(name, coordinates, NOW));
    CarWashInformation information = CarWashInformation.builder().name(name).address("서울시").coordinates(coordinates).build();
    CarWashHistory history = historyRepository.save(CarWashHistory.register(carWash, 1L, VisitExperience.USED, NOW, NOW, information));
    carWash.selectRepresentativeHistory(history, NOW);
    return carWash;
  }
}
