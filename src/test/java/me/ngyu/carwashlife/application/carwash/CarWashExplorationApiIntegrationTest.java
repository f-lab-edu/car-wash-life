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
import me.ngyu.carwashlife.application.carwash.api.dto.RegisterCarWashDto;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.CarWashInformation;
import me.ngyu.carwashlife.application.carwash.domain.CarWashModification;
import me.ngyu.carwashlife.application.carwash.domain.CarWashRegistration;
import me.ngyu.carwashlife.application.carwash.domain.Coordinates;
import me.ngyu.carwashlife.application.carwash.domain.VisitExperience;
import me.ngyu.carwashlife.application.carwash.service.CarWashService;
import me.ngyu.carwashlife.application.member.domain.Member;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashHistoryRepository;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashPhotoRepository;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashRepository;
import me.ngyu.carwashlife.infrastructure.persistence.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.MethodArgumentNotValidException;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestClockConfig.class)
class CarWashExplorationApiIntegrationTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-07T12:00:00+09:00");

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private CarWashService carWashService;
  @Autowired
  private CarWashRepository carWashRepository;
  @Autowired
  private CarWashHistoryRepository historyRepository;
  @Autowired
  private CarWashPhotoRepository photoRepository;
  @Autowired
  private MemberRepository memberRepository;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private AdjustableClock clock;

  private Long memberId;

  @BeforeEach
  void setUp() {
    clock.setInstant(NOW.toInstant());
    photoRepository.deleteAllInBatch();
    jdbcTemplate.update("update car_wash set target_history_id = null");
    historyRepository.deleteAllInBatch();
    carWashRepository.deleteAllInBatch();
    memberId = memberRepository.saveAndFlush(Member.create("exploration@example.com", "encoded", null, null, null, NOW)).getId();
  }

  @Test
  @DisplayName("비회원은 범위 내부와 네 변·모서리의 세차장을 ID 오름차순으로 조회하고 외부 세차장은 제외한다.")
  void anonymousVisitorsSeeInteriorAndAllBoundariesInIdOrder() throws Exception {
    List<Long> expected = new ArrayList<>();
    double[][] included = {{37.5, 127.5}, {37, 127.5}, {38, 127.5}, {37.5, 127}, {37.5, 128}, {37, 127}, {37, 128}, {38, 127}, {38, 128}};
    for (double[] coordinates : included) {
      expected.add(register("범위 안", "서울", coordinates[0], coordinates[1]).carWashId());
    }
    for (double[] coordinates : new double[][] {{36.99, 127.5}, {38.01, 127.5}, {37.5, 126.99}, {37.5, 128.01}}) {
      register("범위 밖", null, coordinates[0], coordinates[1]);
    }

    String response = mockMvc.perform(bounds("37", "127", "38", "128"))
                             .andExpect(status().isOk()).andExpect(jsonPath("$.carWashes.length()").value(9))
                             .andExpect(jsonPath("$.carWashes[0].name").value("범위 안"))
                             .andExpect(jsonPath("$.carWashes[0].address").value("서울"))
                             .andExpect(jsonPath("$.carWashes[0].latitude").value(37.5))
                             .andExpect(jsonPath("$.carWashes[0].longitude").value(127.5))
                             .andExpect(jsonPath("$.carWashes[0].reportCount").value(1))
                             .andExpect(result -> {
                               String latestSubmittedAt = JsonPath.read(result.getResponse().getContentAsString(), "$.carWashes[0].latestSubmittedAt");
                               String createdAt = JsonPath.read(result.getResponse().getContentAsString(), "$.carWashes[0].createdAt");
                               assertThat(OffsetDateTime.parse(latestSubmittedAt).toInstant()).isEqualTo(NOW.toInstant());
                               assertThat(OffsetDateTime.parse(createdAt).toInstant()).isEqualTo(NOW.toInstant());
                             })
                             .andExpect(jsonPath("$.carWashes[0].distanceMeters").value(org.hamcrest.Matchers.nullValue()))
                             .andExpect(jsonPath("$.carWashes[0].memberId").doesNotExist())
                             .andExpect(jsonPath("$.hasMore").value(false)).andReturn().getResponse().getContentAsString();

    List<Number> returnedIds = JsonPath.read(response, "$.carWashes[*].carWashId");
    assertThat(returnedIds.stream().map(Number::longValue).toList()).containsExactlyElementsOf(expected);
  }

  @Test
  @DisplayName("비대표 수정 제보도 건수와 최근 제출 시각에 반영하고 다른 세차장 통계와 대표 정보는 유지한다.")
  void nonRepresentativeReportsUpdateActivityWithoutChangingRepresentativeOrOtherCarWashes() throws Exception {
    RegisterCarWashDto.Response first = register("대표 이름", "서울", 37.5, 127.5);
    RegisterCarWashDto.Response second = register("다른 세차장", "경기", 37.6, 127.6);
    clock.setInstant(NOW.plusHours(3).toInstant());
    carWashService.modify(memberId, first.carWashId(), new CarWashModification(first.historyId(), VisitExperience.NOT_USED,
                                                                             NOW.minusDays(2), information("낮은 신뢰도 제보", "부산", 35.5, 129.5), List.of()));

    mockMvc.perform(bounds("37", "127", "38", "128"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.carWashes.length()").value(2))
           .andExpect(jsonPath("$.carWashes[0].carWashId").value(first.carWashId()))
           .andExpect(jsonPath("$.carWashes[0].name").value("대표 이름"))
           .andExpect(jsonPath("$.carWashes[0].reportCount").value(2))
           .andExpect(jsonPath("$.carWashes[1].carWashId").value(second.carWashId()))
           .andExpect(jsonPath("$.carWashes[1].reportCount").value(1))
           .andExpect(result -> {
             String latestSubmittedAt = JsonPath.read(result.getResponse().getContentAsString(), "$.carWashes[0].latestSubmittedAt");
             String createdAt = JsonPath.read(result.getResponse().getContentAsString(), "$.carWashes[0].createdAt");
             String otherLatestSubmittedAt = JsonPath.read(result.getResponse().getContentAsString(), "$.carWashes[1].latestSubmittedAt");
             assertThat(OffsetDateTime.parse(latestSubmittedAt).toInstant()).isEqualTo(NOW.plusHours(3).toInstant());
             assertThat(OffsetDateTime.parse(createdAt).toInstant()).isEqualTo(NOW.toInstant());
             assertThat(OffsetDateTime.parse(otherLatestSubmittedAt).toInstant()).isEqualTo(NOW.toInstant());
           });

    CarWash master = carWashRepository.findById(first.carWashId()).orElseThrow();
    assertThat(master.getTargetHistory().getId()).isEqualTo(first.historyId());
    assertThat(master.getTargetHistory().getSubmittedAt()).isEqualTo(NOW);
    assertThat(master.getUpdatedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("면적이 없는 지도 범위도 해당 좌표의 세차장과 미확인 주소를 반환한다.")
  void zeroAreaBoundsReturnExactCoordinatesAndNullAddress() throws Exception {
    Long carWashId = register("한 지점", null, 37.5, 127.5).carWashId();
    register("다른 지점", null, 37.5001, 127.5);

    mockMvc.perform(bounds("37.5", "127.5", "37.5", "127.5"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.carWashes.length()").value(1))
           .andExpect(jsonPath("$.carWashes[0].carWashId").value(carWashId))
           .andExpect(jsonPath("$.carWashes[0].address").isEmpty())
           .andExpect(jsonPath("$.hasMore").value(false));
  }

  @Test
  @DisplayName("지도 범위 안에 세차장이 없으면 빈 목록과 추가 결과 없음 상태를 반환한다.")
  void emptyBoundsReturnAnEmptyList() throws Exception {
    mockMvc.perform(bounds("37", "127", "38", "128"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.carWashes").isEmpty())
           .andExpect(jsonPath("$.hasMore").value(false));
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @DisplayName("결과가 100개이면 모두 반환하고 101개이면 앞의 100개와 추가 결과 상태를 반환한다.")
  @ValueSource(ints = {100, 101})
  void resultLimitAndHasMoreReflectTheExtraRow(int count) throws Exception {
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      ids.add(register("세차장 " + i, null, 37.5, 127.5).carWashId());
    }

    String response = mockMvc.perform(bounds("37", "127", "38", "128"))
                             .andExpect(status().isOk()).andExpect(jsonPath("$.carWashes.length()").value(100))
                             .andExpect(jsonPath("$.hasMore").value(count > 100)).andReturn().getResponse().getContentAsString();

    List<Number> returnedIds = JsonPath.read(response, "$.carWashes[*].carWashId");
    assertThat(returnedIds.stream().map(Number::longValue).toList()).containsExactlyElementsOf(ids.subList(0, 100));
  }

  @Test
  @DisplayName("대표 정보의 위치가 이동하면 예전 범위에서 제외하고 새 범위에 현재 이름과 주소를 반환한다.")
  void currentRepresentativeCoordinatesAndAddressDetermineTheResult() throws Exception {
    RegisterCarWashDto.Response original = register("예전 이름", "예전 주소", 37.5, 127.5);
    carWashService.modify(memberId, original.carWashId(), new CarWashModification(original.historyId(), VisitExperience.USED,
                                                                                NOW.minusHours(1), information("현재 이름", "현재 주소", 35.5, 129.5), List.of()));
    long historyCount = historyRepository.count();
    OffsetDateTime updatedAt = carWashRepository.findById(original.carWashId()).orElseThrow().getUpdatedAt();

    mockMvc.perform(bounds("37", "127", "38", "128"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.carWashes").isEmpty());
    mockMvc.perform(bounds("35", "129", "36", "130"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.carWashes.length()").value(1))
           .andExpect(jsonPath("$.carWashes[0].carWashId").value(original.carWashId()))
           .andExpect(jsonPath("$.carWashes[0].name").value("현재 이름"))
           .andExpect(jsonPath("$.carWashes[0].address").value("현재 주소"));
    assertThat(historyRepository.count()).isEqualTo(historyCount);
    assertThat(carWashRepository.findById(original.carWashId()).orElseThrow().getUpdatedAt()).isEqualTo(updatedAt);
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @DisplayName("지도 경계 좌표 중 하나라도 누락되면 검증 오류를 반환한다.")
  @ValueSource(strings = {"southwestLatitude", "southwestLongitude", "northeastLatitude", "northeastLongitude"})
  void allFourCoordinatesAreRequired(String missing) throws Exception {
    MockHttpServletRequestBuilder request = get("/car-washes");
    for (String parameter : List.of("southwestLatitude", "southwestLongitude", "northeastLatitude", "northeastLongitude")) {
      if (!parameter.equals(missing)) {
        request.queryParam(parameter, parameter.endsWith("Latitude") ? "37" : "127");
      }
    }
    mockMvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @DisplayName("문자·유한하지 않은 값·범위 초과인 지도 좌표는 요청 검증 오류를 반환한다.")
  @CsvSource({"text,127,38,128", "NaN,127,38,128", "37,Infinity,38,128", "37,127,-Infinity,128",
          "-91,127,38,128", "37,-181,38,128", "37,127,91,128", "37,127,38,181"})
  void invalidCoordinatesReturnBadRequest(String south, String west, String north, String east) throws Exception {
    mockMvc.perform(bounds(south, west, north, east))
           .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(MethodArgumentNotValidException.class))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @DisplayName("지도 좌표 범위가 역전되면 도메인 검증을 통해 입력 오류를 반환한다.")
  @CsvSource({"39,127,38,128", "37,129,38,128", "-10,170,10,-170"})
  void reversedBoundsReturnBadRequest(String south, String west, String north, String east) throws Exception {
    mockMvc.perform(bounds(south, west, north, east))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @DisplayName("지도 경계와 현재 위치의 위도·경도 최솟값과 최댓값은 허용한다.")
  @CsvSource({"-90,-180", "90,180"})
  void acceptsCoordinateRangeEndpoints(String latitude, String longitude) throws Exception {
    mockMvc.perform(bounds(latitude, longitude, latitude, longitude).queryParam("currentLatitude", latitude).queryParam("currentLongitude", longitude))
           .andExpect(status().isOk()).andExpect(jsonPath("$.carWashes").isEmpty());
  }

  @Test
  @DisplayName("잘못된 토큰으로도 지도 범위의 공개 탐색 결과를 조회할 수 있다.")
  void invalidTokensCanReadPublicExplorationResults() throws Exception {
    register("공개 세차장", null, 37.5, 127.5);

    mockMvc.perform(bounds("37", "127", "38", "128").header("Authorization", "Bearer invalid-token"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.carWashes.length()").value(1));
  }

  @Test
  @DisplayName("현재 위치가 있으면 거리를 미터로 반환하고 같은 거리의 세차장은 ID 순서로 조회한다.")
  void currentLocationReturnsDistanceInMetersAndOrdersTiesById() throws Exception {
    Long farther = register("먼 세차장", null, 0, 2).carWashId();
    Long firstTie = register("같은 거리 첫 번째", null, 0, 1).carWashId();
    Long secondTie = register("같은 거리 두 번째", null, 0, -1).carWashId();
    Long coincident = register("현재 위치", null, 0, 0).carWashId();

    mockMvc.perform(bounds("-1", "-3", "1", "3").queryParam("currentLatitude", "0").queryParam("currentLongitude", "0"))
           .andExpect(status().isOk())
           .andExpect(result -> {
             String json = result.getResponse().getContentAsString();
             List<Number> ids = JsonPath.read(json, "$.carWashes[*].carWashId");
             assertThat(ids.stream().map(Number::longValue).toList()).containsExactly(coincident, firstTie, secondTie, farther);
             Number zero = JsonPath.read(json, "$.carWashes[0].distanceMeters");
             Number oneDegree = JsonPath.read(json, "$.carWashes[1].distanceMeters");
             assertThat(zero.doubleValue()).isZero();
             assertThat(oneDegree.doubleValue()).isCloseTo(111195.0802, within(0.01));
           });
  }

  @Test
  @DisplayName("현재 위치가 지도 범위 밖이어도 거리 기준점으로만 사용하고 범위 내 세차장을 조회한다.")
  void currentLocationMayBeOutsideTheMapBounds() throws Exception {
    register("범위 내", null, 37.5, 127.5);
    register("범위 밖 현재 위치", null, 0, 0);

    mockMvc.perform(bounds("37", "127", "38", "128").queryParam("currentLatitude", "0").queryParam("currentLongitude", "0"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.carWashes.length()").value(1))
           .andExpect(jsonPath("$.carWashes[0].name").value("범위 내"))
           .andExpect(jsonPath("$.carWashes[0].distanceMeters").isNumber());
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @DisplayName("거리순으로 먼저 제한하므로 큰 ID의 최근접 세차장도 포함하고 추가 결과 여부를 반환한다.")
  @ValueSource(ints = {100, 101})
  void distanceOrderIsAppliedBeforeTheDatabaseLimit(int count) throws Exception {
    List<Long> farIds = new ArrayList<>();
    for (int i = 0; i < count - 1; i++) {
      farIds.add(register("먼 세차장 " + i, null, 37.9, 127.9).carWashId());
    }
    Long nearestId = register("마지막 ID 최근접", null, 37.5, 127.5).carWashId();

    String response = mockMvc.perform(bounds("37", "127", "38", "128").queryParam("currentLatitude", "37.5").queryParam("currentLongitude", "127.5"))
                             .andExpect(status().isOk()).andExpect(jsonPath("$.carWashes.length()").value(100))
                             .andExpect(jsonPath("$.carWashes[0].carWashId").value(nearestId))
                             .andExpect(jsonPath("$.hasMore").value(count > 100)).andReturn().getResponse().getContentAsString();

    List<Number> ids = JsonPath.read(response, "$.carWashes[*].carWashId");
    List<Long> expected = new ArrayList<>();
    expected.add(nearestId);
    expected.addAll(farIds.subList(0, 99));
    assertThat(ids.stream().map(Number::longValue).toList()).containsExactlyElementsOf(expected);
  }

  @Test
  @DisplayName("현재 위치가 있어도 지도 범위에 세차장이 없으면 빈 결과를 반환한다.")
  void emptyBoundsWithCurrentLocationReturnAnEmptyList() throws Exception {
    mockMvc.perform(bounds("37", "127", "38", "128").queryParam("currentLatitude", "37.5").queryParam("currentLongitude", "127.5"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.carWashes").isEmpty()).andExpect(jsonPath("$.hasMore").value(false));
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @DisplayName("현재 위치의 문자·유한하지 않은 값·범위 초과는 요청 검증 오류를 반환한다.")
  @CsvSource({"text,127", "37,text", "NaN,127", "37,NaN", "Infinity,127", "37,-Infinity", "-91,127", "91,127", "37,-181", "37,181"})
  void invalidCurrentCoordinatesReturnBadRequest(String latitude, String longitude) throws Exception {
    MockHttpServletRequestBuilder request = bounds("37", "127", "38", "128").queryParam("currentLatitude", latitude).queryParam("currentLongitude", longitude);
    mockMvc.perform(request)
           .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(MethodArgumentNotValidException.class))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @DisplayName("현재 위치의 위도와 경도 중 하나만 입력하면 검증 오류를 반환한다.")
  @ValueSource(strings = {"currentLatitude", "currentLongitude"})
  void incompleteCurrentCoordinatesReturnBadRequest(String parameter) throws Exception {
    MockHttpServletRequestBuilder request = bounds("37", "127", "38", "128").queryParam(parameter, "37");
    mockMvc.perform(request).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  private RegisterCarWashDto.Response register(String name, String address, double latitude, double longitude) {
    return carWashService.register(memberId, new CarWashRegistration(VisitExperience.USED, NOW.minusDays(1), information(name, address, latitude, longitude), List.of()));
  }

  private CarWashInformation information(String name, String address, double latitude, double longitude) {
    return CarWashInformation.builder().name(name).address(address).coordinates(new Coordinates(latitude, longitude)).build();
  }

  private MockHttpServletRequestBuilder bounds(String south, String west, String north, String east) {
    return get("/car-washes").queryParam("southwestLatitude", south).queryParam("southwestLongitude", west)
                             .queryParam("northeastLatitude", north).queryParam("northeastLongitude", east);
  }
}
