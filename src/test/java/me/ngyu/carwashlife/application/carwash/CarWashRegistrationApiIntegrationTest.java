package me.ngyu.carwashlife.application.carwash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.stream.Stream;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistoryType;
import me.ngyu.carwashlife.application.carwash.domain.FacilityAvailability;
import me.ngyu.carwashlife.application.carwash.domain.VisitExperience;
import me.ngyu.carwashlife.application.member.domain.Member;
import me.ngyu.carwashlife.common.security.AccessTokenProvider;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashHistoryRepository;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashRepository;
import me.ngyu.carwashlife.infrastructure.persistence.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CarWashRegistrationApiIntegrationTest.FixedClockConfig.class)
class CarWashRegistrationApiIntegrationTest {

  private static final OffsetDateTime SUBMITTED_AT =
          OffsetDateTime.parse("2026-10-07T12:00:00+09:00");
  private static final String REQUEST = """
          {
            "name": "세차생활",
            "address": "서울시 강남구",
            "latitude": 37.5,
            "longitude": 127.0,
            "visitExperience": "USED",
            "observedAt": "2026-10-06T12:00:00+09:00",
            "highPressureWaterPrice": 3000,
            "foamLanceAvailability": "AVAILABLE",
            "foamGunPrice": 4000,
            "airGunAvailability": "AVAILABLE",
            "airGunPrice": 0,
            "vacuumAvailability": "AVAILABLE",
            "vacuumPrice": 1000,
            "washBayCount": 6,
            "dryingBayCount": 10
          }
          """;

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private CarWashRepository carWashRepository;
  @Autowired
  private CarWashHistoryRepository historyRepository;
  @Autowired
  private MemberRepository memberRepository;
  @Autowired
  private AccessTokenProvider accessTokenProvider;
  @Autowired
  private JdbcTemplate jdbcTemplate;

  private Member member;
  private String token;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("update car_wash set target_history_id = null");
    historyRepository.deleteAllInBatch();
    carWashRepository.deleteAll();
    memberRepository.deleteAll();
    member = memberRepository.saveAndFlush(Member.create(
            "register@example.com", "encoded-password", null, null, null, SUBMITTED_AT));
    token = accessTokenProvider.create(member.getId());
  }

  @DisplayName("등록 요청을 제출하면 작성자와 전체 정보 이력을 저장하고 해당 이력을 원장에 대표로 반영한다.")
  @Test
  void registrationPersistsSnapshotAndReflectsItsRepresentativeInformation() throws Exception {
    mockMvc.perform(post("/car-washes")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.carWashId").isNumber())
           .andExpect(jsonPath("$.historyId").isNumber())
           .andExpect(jsonPath("$.confidence").value(4))
           .andExpect(jsonPath("$.submittedAt").value("2026-10-07T12:00:00+09:00"));

    assertThat(carWashRepository.count()).isEqualTo(1);
    assertThat(historyRepository.count()).isEqualTo(1);
    CarWash carWash = carWashRepository.findAll().getFirst();
    CarWashHistory history = historyRepository.findAll().getFirst();
    assertThat(carWash.getTargetHistory().getId()).isEqualTo(history.getId());
    assertThat(carWash.getName()).isEqualTo("세차생활");
    assertThat(carWash.getLatitude()).isEqualTo(37.5);
    assertThat(carWash.getLongitude()).isEqualTo(127.0);
    assertThat(carWash.isOperatorManaged()).isFalse();
    assertThat(carWash.getCreatedAt()).isEqualTo(SUBMITTED_AT);
    assertThat(carWash.getUpdatedAt()).isEqualTo(SUBMITTED_AT);
    assertThat(history.getCarWash().getId()).isEqualTo(carWash.getId());
    assertThat(history.getMemberId()).isEqualTo(member.getId());
    assertThat(history.getType()).isEqualTo(CarWashHistoryType.REGISTRATION);
    assertThat(history.getVisitExperience()).isEqualTo(VisitExperience.USED);
    assertThat(history.getName()).isEqualTo("세차생활");
    assertThat(history.getAddress()).isEqualTo("서울시 강남구");
    assertThat(history.getLatitude()).isEqualTo(37.5);
    assertThat(history.getLongitude()).isEqualTo(127.0);
    assertThat(history.getObservedAt()).isEqualTo(SUBMITTED_AT.minusDays(1));
    assertThat(history.getSubmittedAt()).isEqualTo(SUBMITTED_AT);
    assertThat(history.getHighPressureWaterPrice()).isEqualTo(3000);
    assertThat(history.getFoamLanceAvailability()).isEqualTo(FacilityAvailability.AVAILABLE);
    assertThat(history.getFoamGunPrice()).isEqualTo(4000);
    assertThat(history.getAirGunAvailability()).isEqualTo(FacilityAvailability.AVAILABLE);
    assertThat(history.getAirGunPrice()).isZero();
    assertThat(history.getVacuumAvailability()).isEqualTo(FacilityAvailability.AVAILABLE);
    assertThat(history.getVacuumPrice()).isEqualTo(1000);
    assertThat(history.getWashBayCount()).isEqualTo(6);
    assertThat(history.getDryingBayCount()).isEqualTo(10);
  }

  @DisplayName("필수 정보만 등록하면 입력하지 않은 시설과 가격 및 베이 수를 미확인 상태로 저장한다.")
  @Test
  void unknownFacilitiesAndPricesRemainUnknownForMinimalRegistration() throws Exception {
    mockMvc.perform(post("/car-washes")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                             {"name":"세차생활","latitude":37.5,"longitude":127.0,
                                              "visitExperience":"NOT_USED","observedAt":"2026-10-06T12:00:00+09:00"}
                                             """))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.confidence").value(2));

    CarWashHistory history = historyRepository.findAll().getFirst();
    assertThat(history.getAddress()).isNull();
    assertThat(history.getFoamLanceAvailability()).isEqualTo(FacilityAvailability.UNKNOWN);
    assertThat(history.getAirGunAvailability()).isEqualTo(FacilityAvailability.UNKNOWN);
    assertThat(history.getVacuumAvailability()).isEqualTo(FacilityAvailability.UNKNOWN);
    assertThat(history.getHighPressureWaterPrice()).isNull();
    assertThat(history.getFoamGunPrice()).isNull();
    assertThat(history.getAirGunPrice()).isNull();
    assertThat(history.getVacuumPrice()).isNull();
    assertThat(history.getWashBayCount()).isNull();
    assertThat(history.getDryingBayCount()).isNull();
  }

  @DisplayName("관찰한 지 90일이 넘은 정보를 등록하면 최근성 점수를 가산하지 않는다.")
  @Test
  void observationsOlderThanNinetyDaysDoNotReceiveTheRecencyPoint() throws Exception {
    mockMvc.perform(post("/car-washes")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST.replace("2026-10-06T12:00:00+09:00", "2026-07-08T12:00:00+09:00")))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.confidence").value(3));
  }

  @DisplayName("등록 요청값이 올바르지 않으면 원장과 이력을 저장하지 않고 거절한다.")
  @ParameterizedTest(name = "[{index}] {displayName}")
  @MethodSource("invalidRequests")
  void invalidRequestsDoNotPersistAnything(String scenario, String request) throws Exception {
    mockMvc.perform(post("/car-washes")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    assertThat(carWashRepository.count()).isZero();
    assertThat(historyRepository.count()).isZero();
  }

  static Stream<Arguments> invalidRequests() {
    return Stream.of(
            Arguments.of("blank name", REQUEST.replace("세차생활", " ")),
            Arguments.of("long name", REQUEST.replace("세차생활", "가".repeat(256))),
            Arguments.of("long address", REQUEST.replace("서울시 강남구", "가".repeat(256))),
            Arguments.of("null name", REQUEST.replace("\"세차생활\"", "null")),
            Arguments.of("null latitude", REQUEST.replace("37.5", "null")),
            Arguments.of("latitude", REQUEST.replace("37.5", "91")),
            Arguments.of("null longitude", REQUEST.replace("127.0", "null")),
            Arguments.of("longitude", REQUEST.replace("127.0", "181")),
            Arguments.of("null experience", REQUEST.replace("\"USED\"", "null")),
            Arguments.of("unknown experience", REQUEST.replace("\"USED\"", "\"UNKNOWN\"")),
            Arguments.of("null observation", REQUEST.replace("\"2026-10-06T12:00:00+09:00\"", "null")),
            Arguments.of("future observation", REQUEST.replace("2026-10-06", "2026-10-08")),
            Arguments.of("invalid date", REQUEST.replace("2026-10-06T12:00:00+09:00", "not-a-date")),
            Arguments.of("water price", REQUEST.replace("\"highPressureWaterPrice\": 3000", "\"highPressureWaterPrice\": -1")),
            Arguments.of("foam price", REQUEST.replace("\"foamGunPrice\": 4000", "\"foamGunPrice\": -1")),
            Arguments.of("air price", REQUEST.replace("\"airGunPrice\": 0", "\"airGunPrice\": -1")),
            Arguments.of("vacuum price", REQUEST.replace("\"vacuumPrice\": 1000", "\"vacuumPrice\": -1")),
            Arguments.of("wash bays", REQUEST.replace("\"washBayCount\": 6", "\"washBayCount\": -1")),
            Arguments.of("drying bays", REQUEST.replace("\"dryingBayCount\": 10", "\"dryingBayCount\": -1")),
            Arguments.of("unknown facility", REQUEST.replace("\"foamLanceAvailability\": \"AVAILABLE\"", "\"foamLanceAvailability\": \"INVALID\""))
    );
  }

  @DisplayName("사용 불가 시설에 가격을 입력하면 등록을 거절하고 먼저 저장한 원장도 롤백한다.")
  @Test
  void contradictoryFacilityPriceRollsBackTheAlreadySavedMaster() throws Exception {
    mockMvc.perform(post("/car-washes")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(REQUEST.replace("\"airGunAvailability\": \"AVAILABLE\"",
                                                     "\"airGunAvailability\": \"UNAVAILABLE\"")))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    assertThat(carWashRepository.count()).isZero();
    assertThat(historyRepository.count()).isZero();
  }

  @DisplayName("인증 없이 세차장을 등록하면 원장과 이력을 만들지 않고 거절한다.")
  @Test
  void registrationRequiresAuthentication() throws Exception {
    mockMvc.perform(post("/car-washes")
                            .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
           .andExpect(status().isUnauthorized());

    assertThat(carWashRepository.count()).isZero();
    assertThat(historyRepository.count()).isZero();
  }

  @DisplayName("잘못된 토큰으로 세차장을 등록하면 원장과 이력을 만들지 않고 거절한다.")
  @Test
  void malformedTokenCannotRegister() throws Exception {
    mockMvc.perform(post("/car-washes")
                            .header("Authorization", "Bearer invalid-token")
                            .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
           .andExpect(status().isUnauthorized());

    assertThat(carWashRepository.count()).isZero();
    assertThat(historyRepository.count()).isZero();
  }

  @DisplayName("토큰 발급 뒤 정지된 회원이 세차장을 등록하면 원장과 이력을 만들지 않고 거절한다.")
  @Test
  void aTokenIssuedBeforeAccountSuspensionCannotRegister() throws Exception {
    jdbcTemplate.update("update members set status = 'SUSPENDED' where id = ?", member.getId());

    mockMvc.perform(post("/car-washes")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));

    assertThat(carWashRepository.count()).isZero();
    assertThat(historyRepository.count()).isZero();
  }

  @DisplayName("삭제된 회원의 토큰으로 세차장을 등록하면 원장과 이력을 만들지 않고 거절한다.")
  @Test
  void aTokenForADeletedAccountCannotRegister() throws Exception {
    memberRepository.deleteById(member.getId());

    mockMvc.perform(post("/car-washes")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

    assertThat(carWashRepository.count()).isZero();
    assertThat(historyRepository.count()).isZero();
  }

  @TestConfiguration
  static class FixedClockConfig {

    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(Instant.parse("2026-10-07T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    }
  }
}
