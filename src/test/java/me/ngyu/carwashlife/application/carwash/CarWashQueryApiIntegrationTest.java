package me.ngyu.carwashlife.application.carwash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory;
import me.ngyu.carwashlife.application.member.domain.Member;
import me.ngyu.carwashlife.common.security.AccessTokenProvider;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashHistoryRepository;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashRepository;
import me.ngyu.carwashlife.infrastructure.persistence.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
@Import(CarWashQueryApiIntegrationTest.TestClockConfig.class)
class CarWashQueryApiIntegrationTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-07T12:00:00+09:00");
  private static final String SNAPSHOT = """
          {"name":"세차생활","address":"서울시","latitude":37.5,"longitude":127.0,
           "visitExperience":"USED","observedAt":"2026-10-05T12:00:00+09:00",
           "highPressureWaterPrice":3000,"foamLanceAvailability":"AVAILABLE",
           "foamGunPrice":4000,"airGunAvailability":"AVAILABLE","airGunPrice":0,
           "vacuumAvailability":"AVAILABLE","vacuumPrice":1000,"washBayCount":6,"dryingBayCount":10}
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
  private AccessTokenProvider tokenProvider;
  @Autowired
  private JdbcTemplate jdbcTemplate;
  @Autowired
  private AdjustableClock clock;

  private Long memberId;
  private Long carWashId;
  private Long originalHistoryId;
  private String token;

  @BeforeEach
  void setUp() throws Exception {
    clock.setInstant(NOW.toInstant());
    jdbcTemplate.update("update car_wash set target_history_id = null");
    historyRepository.deleteAllInBatch();
    carWashRepository.deleteAllInBatch();
    memberRepository.deleteAllInBatch();
    Member member = memberRepository.saveAndFlush(Member.create("query@example.com", "encoded", null, null, null, NOW));
    memberId = member.getId();
    token = tokenProvider.create(memberId);
    mockMvc.perform(post("/car-washes").header("Authorization", "Bearer " + token)
                                       .contentType(MediaType.APPLICATION_JSON).content(SNAPSHOT))
           .andExpect(status().isCreated());
    carWashId = carWashRepository.findAll().getFirst().getId();
    originalHistoryId = historyRepository.findAll().getFirst().getId();
  }

  @DisplayName("상세 조회하면 내부 회원 식별자를 노출하지 않고 대표 이력의 전체 정보와 신뢰도를 반환한다.")
  @Test
  void detailReturnsTheSelectedFullSnapshotAndConfidenceWithoutExposingMemberIds() throws Exception {
    mockMvc.perform(get("/car-washes/{id}", carWashId).header("Authorization", "Bearer " + token))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.carWashId").value(carWashId))
           .andExpect(jsonPath("$.historyId").value(originalHistoryId))
           .andExpect(jsonPath("$.confidence").value(4))
           .andExpect(jsonPath("$.operatorManaged").value(false))
           .andExpect(jsonPath("$.baseHistoryId").isEmpty())
           .andExpect(jsonPath("$.name").value("세차생활"))
           .andExpect(jsonPath("$.address").value("서울시"))
           .andExpect(jsonPath("$.latitude").value(37.5))
           .andExpect(jsonPath("$.longitude").value(127.0))
           .andExpect(jsonPath("$.highPressureWaterPrice").value(3000))
           .andExpect(jsonPath("$.foamLanceAvailability").value("AVAILABLE"))
           .andExpect(jsonPath("$.foamGunPrice").value(4000))
           .andExpect(jsonPath("$.airGunAvailability").value("AVAILABLE"))
           .andExpect(jsonPath("$.airGunPrice").value(0))
           .andExpect(jsonPath("$.vacuumAvailability").value("AVAILABLE"))
           .andExpect(jsonPath("$.vacuumPrice").value(1000))
           .andExpect(jsonPath("$.washBayCount").value(6))
           .andExpect(jsonPath("$.dryingBayCount").value(10))
           .andExpect(jsonPath("$.visitExperience").value("USED"))
           .andExpect(result -> {
             String observedAt = JsonPath.read(result.getResponse().getContentAsString(), "$.observedAt");
             String submittedAt = JsonPath.read(result.getResponse().getContentAsString(), "$.submittedAt");
             assertThat(OffsetDateTime.parse(observedAt).toInstant()).isEqualTo(NOW.minusDays(2).toInstant());
             assertThat(OffsetDateTime.parse(submittedAt).toInstant()).isEqualTo(NOW.toInstant());
           })
           .andExpect(jsonPath("$.changedFields.length()").value(13))
           .andExpect(jsonPath("$.memberId").doesNotExist());
  }

  @DisplayName("신뢰도가 낮은 새 수정 이력은 저장하되 기존 대표 이력을 교체하지 않는다.")
  @Test
  void lowerConfidenceNewHistoryDoesNotReplaceTheWinner() throws Exception {
    String request = modification().replace("\"USED\"", "\"NOT_USED\"").replace("세차생활", "낮은 점수 이름");
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId).header("Authorization", "Bearer " + token)
                                                                 .contentType(MediaType.APPLICATION_JSON).content(request))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.confidence").value(2));
    mockMvc.perform(get("/car-washes/{id}", carWashId).header("Authorization", "Bearer " + token))
           .andExpect(status().isOk()).andExpect(jsonPath("$.historyId").value(originalHistoryId))
           .andExpect(jsonPath("$.name").value("세차생활"));

    assertThat(historyRepository.count()).isEqualTo(2);
    assertThat(carWashRepository.findById(carWashId).orElseThrow().getTargetHistory().getId()).isEqualTo(originalHistoryId);
  }

  @DisplayName("신뢰도가 같으면 최신 관찰 이력을 대표로 선택하고 원장의 이름과 좌표를 함께 갱신한다.")
  @Test
  void latestObservationWinsATieAndUpdatesNameCoordinatesAndPointerTogether() throws Exception {
    String request = modification().replace("세차생활", "새 세차생활").replace("37.5", "37.6").replace("127.0", "127.1");
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId).header("Authorization", "Bearer " + token)
                                                                 .contentType(MediaType.APPLICATION_JSON).content(request))
           .andExpect(status().isCreated());
    Long winner = historyRepository.findAll().stream().filter(history -> !history.getId().equals(originalHistoryId)).findFirst().orElseThrow().getId();
    mockMvc.perform(get("/car-washes/{id}", carWashId).header("Authorization", "Bearer " + token))
           .andExpect(status().isOk()).andExpect(jsonPath("$.historyId").value(winner))
           .andExpect(jsonPath("$.baseHistoryId").value(originalHistoryId))
           .andExpect(jsonPath("$.name").value("새 세차생활"))
           .andExpect(jsonPath("$.latitude").value(37.6)).andExpect(jsonPath("$.longitude").value(127.1));

    CarWash master = carWashRepository.findById(carWashId).orElseThrow();
    assertThat(master.getTargetHistory().getId()).isEqualTo(winner);
    assertThat(master.getName()).isEqualTo("새 세차생활");
    assertThat(master.getLatitude()).isEqualTo(37.6);
    assertThat(master.getLongitude()).isEqualTo(127.1);
    CarWashHistory original = historyRepository.findById(originalHistoryId).orElseThrow();
    assertThat(original.getName()).isEqualTo("세차생활");
    assertThat(original.getLatitude()).isEqualTo(37.5);
    assertThat(original.getLongitude()).isEqualTo(127.0);
  }

  @DisplayName("나중에 제출한 수정 요청의 신뢰도가 낮으면 앞서 선택한 대표 정보를 유지한다.")
  @Test
  void aLaterLowerConfidenceCorrectionCannotReplaceAnEarlierWinner() throws Exception {
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId).header("Authorization", "Bearer " + token)
                                                                 .contentType(MediaType.APPLICATION_JSON).content(modification().replace("세차생활", "최고점 이름")))
           .andExpect(status().isCreated());
    Long winner = carWashRepository.findById(carWashId).orElseThrow().getTargetHistory().getId();
    clock.setInstant(NOW.plusHours(1).toInstant());
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId).header("Authorization", "Bearer " + token)
                                                                 .contentType(MediaType.APPLICATION_JSON)
                                                                 .content(modification().replace("세차생활", "최신 낮은점수 이름").replace("\"USED\"", "\"NOT_USED\"")))
           .andExpect(status().isCreated());
    mockMvc.perform(get("/car-washes/{id}", carWashId).header("Authorization", "Bearer " + token))
           .andExpect(status().isOk()).andExpect(jsonPath("$.historyId").value(winner))
           .andExpect(jsonPath("$.name").value("최고점 이름"));
  }

  @DisplayName("조회 시 90일이 지나면 신뢰도를 재계산하되 대표 이력이 같으면 갱신 시각을 유지한다.")
  @Test
  void queriesRecalculateConfidenceAfterNinetyDaysWithoutChangingAnUnchangedWinnerTimestamp() throws Exception {
    clock.setInstant(NOW.plusDays(91).toInstant());

    mockMvc.perform(get("/car-washes/{id}", carWashId).header("Authorization", "Bearer " + token))
           .andExpect(status().isOk()).andExpect(jsonPath("$.confidence").value(3))
           .andExpect(jsonPath("$.historyId").value(originalHistoryId));

    assertThat(carWashRepository.findById(carWashId).orElseThrow().getUpdatedAt()).isEqualTo(NOW);
  }

  @DisplayName("원장이 오래된 대표 이력을 가리키면 조회 시 최고점 이력과 해당 이름 및 좌표로 복구한다.")
  @Test
  void queriesRepairAnOutdatedRepresentativeAndItsMasterProjection() throws Exception {
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId).header("Authorization", "Bearer " + token)
                                                                 .contentType(MediaType.APPLICATION_JSON).content(modification().replace("세차생활", "정확한 이름")))
           .andExpect(status().isCreated());
    Long winner = carWashRepository.findById(carWashId).orElseThrow().getTargetHistory().getId();
    jdbcTemplate.update("update car_wash set target_history_id = ?, name = ?, latitude = 0, longitude = 0 where id = ?", originalHistoryId, "오래된 이름", carWashId);

    mockMvc.perform(get("/car-washes/{id}", carWashId).header("Authorization", "Bearer " + token))
           .andExpect(status().isOk()).andExpect(jsonPath("$.historyId").value(winner))
           .andExpect(jsonPath("$.name").value("정확한 이름"));

    CarWash repaired = carWashRepository.findById(carWashId).orElseThrow();
    assertThat(repaired.getTargetHistory().getId()).isEqualTo(winner);
    assertThat(repaired.getName()).isEqualTo("정확한 이름");
    assertThat(repaired.getLatitude()).isEqualTo(37.5);
    assertThat(repaired.getLongitude()).isEqualTo(127.0);
  }

  @DisplayName("존재하지 않는 세차장을 상세 조회하면 404 오류를 반환한다.")
  @Test
  void missingCarWashReturnsNotFound() throws Exception {
    mockMvc.perform(get("/car-washes/{id}", Long.MAX_VALUE).header("Authorization", "Bearer " + token))
           .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CAR_WASH_NOT_FOUND"));
  }

  @DisplayName("인증 없이 세차장을 상세 조회하면 거절한다.")
  @Test
  void missingAuthenticationIsRejected() throws Exception {
    mockMvc.perform(get("/car-washes/{id}", carWashId)).andExpect(status().isUnauthorized());
  }

  @DisplayName("잘못된 토큰으로 세차장을 상세 조회하면 거절한다.")
  @Test
  void invalidTokensAreRejected() throws Exception {
    mockMvc.perform(get("/car-washes/{id}", carWashId).header("Authorization", "Bearer invalid-token"))
           .andExpect(status().isUnauthorized());
  }

  @DisplayName("삭제된 회원의 토큰으로 세차장을 상세 조회하면 거절한다.")
  @Test
  void tokensForDeletedMembersAreRejected() throws Exception {
    memberRepository.deleteById(memberId);
    mockMvc.perform(get("/car-washes/{id}", carWashId).header("Authorization", "Bearer " + token))
           .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
  }

  @DisplayName("정지된 회원이 세차장을 상세 조회하면 비활성 계정 오류를 반환한다.")
  @Test
  void inactiveMembersCannotQueryOrChangeTheRepresentative() throws Exception {
    jdbcTemplate.update("update members set status = 'SUSPENDED' where id = ?", memberId);
    mockMvc.perform(get("/car-washes/{id}", carWashId).header("Authorization", "Bearer " + token))
           .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
  }

  private String modification() {
    return SNAPSHOT.replace("{", "{\"baseHistoryId\":" + originalHistoryId + ",").replace("2026-10-05", "2026-10-06");
  }

  @TestConfiguration
  static class TestClockConfig {

    @Bean
    @Primary
    AdjustableClock adjustableClock() {
      return new AdjustableClock(new AtomicReference<>(NOW.toInstant()), ZoneId.of("Asia/Seoul"));
    }
  }

  static class AdjustableClock extends Clock {

    private final AtomicReference<Instant> instant;
    private final ZoneId zone;

    AdjustableClock(AtomicReference<Instant> instant, ZoneId zone) {
      this.instant = instant;
      this.zone = zone;
    }

    void setInstant(Instant instant) {
      this.instant.set(instant);
    }

    @Override
    public ZoneId getZone() {
      return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return new AdjustableClock(instant, zone);
    }

    @Override
    public Instant instant() {
      return instant.get();
    }
  }
}
