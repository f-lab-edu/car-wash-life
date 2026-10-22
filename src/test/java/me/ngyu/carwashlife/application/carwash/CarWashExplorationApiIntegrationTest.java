package me.ngyu.carwashlife.application.carwash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import me.ngyu.carwashlife.application.carwash.api.dto.CarWashInformationDto;
import me.ngyu.carwashlife.application.carwash.api.dto.ModifyCarWashDto;
import me.ngyu.carwashlife.application.carwash.api.dto.RegisterCarWashDto;
import me.ngyu.carwashlife.application.carwash.domain.VisitExperience;
import me.ngyu.carwashlife.application.carwash.service.CarWashModificationService;
import me.ngyu.carwashlife.application.carwash.service.CarWashRegistrationService;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(CarWashRegistrationApiIntegrationTest.FixedClockConfig.class)
class CarWashExplorationApiIntegrationTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-07T12:00:00+09:00");

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private CarWashRegistrationService registrationService;
  @Autowired
  private CarWashModificationService modificationService;
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

  private Long memberId;

  @BeforeEach
  void setUp() {
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

    mockMvc.perform(bounds("37", "127", "38", "128"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.carWashes.length()").value(9))
           .andExpect(result -> {
             List<Number> returnedIds = JsonPath.read(result.getResponse().getContentAsString(), "$.carWashes[*].carWashId");
             assertThat(returnedIds.stream().map(Number::longValue).toList()).containsExactlyElementsOf(expected);
           })
           .andExpect(jsonPath("$.carWashes[0].name").value("범위 안"))
           .andExpect(jsonPath("$.carWashes[0].address").value("서울"))
           .andExpect(jsonPath("$.carWashes[0].latitude").value(37.5))
           .andExpect(jsonPath("$.carWashes[0].longitude").value(127.5))
           .andExpect(jsonPath("$.carWashes[0].memberId").doesNotExist())
           .andExpect(jsonPath("$.hasMore").value(false));
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

    mockMvc.perform(bounds("37", "127", "38", "128"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.carWashes.length()").value(100))
           .andExpect(result -> {
             List<Number> returnedIds = JsonPath.read(result.getResponse().getContentAsString(), "$.carWashes[*].carWashId");
             assertThat(returnedIds.stream().map(Number::longValue).toList()).containsExactlyElementsOf(ids.subList(0, 100));
           })
           .andExpect(jsonPath("$.hasMore").value(count > 100));
  }

  @Test
  @DisplayName("대표 정보의 위치가 이동하면 예전 범위에서 제외하고 새 범위에 현재 이름과 주소를 반환한다.")
  void currentRepresentativeCoordinatesAndAddressDetermineTheResult() throws Exception {
    RegisterCarWashDto.Response original = register("예전 이름", "예전 주소", 37.5, 127.5);
    modificationService.modify(memberId, original.carWashId(), new ModifyCarWashDto.Request(original.historyId(), VisitExperience.USED,
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
  @DisplayName("문자·유한하지 않은 값·범위 초과·역전된 지도 좌표는 검증 오류를 반환한다.")
  @CsvSource({"text,127,38,128", "NaN,127,38,128", "37,Infinity,38,128", "37,127,-Infinity,128",
          "-91,127,38,128", "37,-181,38,128", "37,127,91,128", "37,127,38,181",
          "39,127,38,128", "37,129,38,128", "-10,170,10,-170"})
  void invalidCoordinatesReturnBadRequest(String south, String west, String north, String east) throws Exception {
    mockMvc.perform(bounds(south, west, north, east))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("잘못된 토큰으로도 지도 범위의 공개 탐색 결과를 조회할 수 있다.")
  void invalidTokensCanReadPublicExplorationResults() throws Exception {
    register("공개 세차장", null, 37.5, 127.5);

    mockMvc.perform(bounds("37", "127", "38", "128").header("Authorization", "Bearer invalid-token"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.carWashes.length()").value(1));
  }

  private RegisterCarWashDto.Response register(String name, String address, double latitude, double longitude) {
    return registrationService.register(memberId, new RegisterCarWashDto.Request(VisitExperience.USED, NOW.minusDays(1),
                                                                                 information(name, address, latitude, longitude), List.of()));
  }

  private CarWashInformationDto.Request information(String name, String address, double latitude, double longitude) {
    return new CarWashInformationDto.Request(name, address, latitude, longitude, null, null, null, null, null, null, null, null, null);
  }

  private MockHttpServletRequestBuilder bounds(String south, String west, String north, String east) {
    return get("/car-washes").queryParam("southwestLatitude", south).queryParam("southwestLongitude", west)
                             .queryParam("northeastLatitude", north).queryParam("northeastLongitude", east);
  }
}
