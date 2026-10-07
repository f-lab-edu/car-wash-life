package me.ngyu.carwashlife.application.carwash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.stream.Stream;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistoryField;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistoryType;
import me.ngyu.carwashlife.application.carwash.domain.VisitExperience;
import me.ngyu.carwashlife.application.member.domain.Member;
import me.ngyu.carwashlife.common.security.AccessTokenProvider;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashHistoryRepository;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashRepository;
import me.ngyu.carwashlife.infrastructure.persistence.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CarWashRegistrationApiIntegrationTest.FixedClockConfig.class)
class CarWashModificationApiIntegrationTest {

  private static final OffsetDateTime SUBMITTED_AT = OffsetDateTime.parse("2026-10-07T12:00:00+09:00");
  private static final String SNAPSHOT = """
          {"name":"세차생활","address":"서울시","latitude":37.5,"longitude":127.0,
           "visitExperience":"NOT_USED","observedAt":"2026-06-29T12:00:00+09:00",
           "highPressureWaterPrice":3000}
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
  @Autowired
  private PlatformTransactionManager transactionManager;

  private Long carWashId;
  private Long baseHistoryId;
  private Long originalAuthorId;
  private Member modifyingMember;
  private String token;

  @BeforeEach
  void setUp() throws Exception {
    jdbcTemplate.update("update car_wash set target_history_id = null");
    historyRepository.deleteAllInBatch();
    carWashRepository.deleteAllInBatch();
    memberRepository.deleteAllInBatch();
    Member author = memberRepository.saveAndFlush(Member.create(
            "original@example.com", "encoded-password", null, null, null, SUBMITTED_AT));
    originalAuthorId = author.getId();
    mockMvc.perform(post("/car-washes")
                            .header("Authorization", "Bearer " + accessTokenProvider.create(author.getId()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SNAPSHOT))
           .andExpect(status().isCreated());
    carWashId = carWashRepository.findAll().getFirst().getId();
    baseHistoryId = historyRepository.findAll().getFirst().getId();
    modifyingMember = memberRepository.saveAndFlush(Member.create(
            "modifying@example.com", "encoded-password", null, null, null, SUBMITTED_AT));
    token = accessTokenProvider.create(modifyingMember.getId());
  }

  @Test
  void modificationAppendsASnapshotPreservesTheOriginalAndReflectsTheHigherConfidenceHistory() throws Exception {
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(modification(baseHistoryId, 3500)))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.carWashId").value(carWashId))
           .andExpect(jsonPath("$.historyId").isNumber())
           .andExpect(jsonPath("$.baseHistoryId").value(baseHistoryId))
           .andExpect(jsonPath("$.changedFields[0]").value("HIGH_PRESSURE_WATER_PRICE"))
           .andExpect(jsonPath("$.changedFields.length()").value(1))
           .andExpect(jsonPath("$.confidence").value(4))
           .andExpect(jsonPath("$.submittedAt").value("2026-10-07T12:00:00+09:00"));

    assertThat(carWashRepository.count()).isEqualTo(1);
    assertThat(historyRepository.count()).isEqualTo(2);
    CarWashHistory next = modificationHistory();
    assertThat(next.getBaseHistory().getId()).isEqualTo(baseHistoryId);
    assertThat(next.getCarWash().getId()).isEqualTo(carWashId);
    assertThat(next.getMemberId()).isEqualTo(modifyingMember.getId());
    assertThat(next.getVisitExperience()).isEqualTo(VisitExperience.USED);
    assertThat(next.getObservedAt()).isEqualTo(SUBMITTED_AT.minusDays(1));
    assertThat(next.getSubmittedAt()).isEqualTo(SUBMITTED_AT);
    assertThat(next.getName()).isEqualTo("세차생활");
    assertThat(next.getAddress()).isEqualTo("서울시");
    assertThat(next.getLatitude()).isEqualTo(37.5);
    assertThat(next.getLongitude()).isEqualTo(127.0);
    assertThat(next.getHighPressureWaterPrice()).isEqualTo(3500);
    assertThat(next.getChangedFields()).containsExactly(CarWashHistoryField.HIGH_PRESSURE_WATER_PRICE);
    CarWashHistory original = historyRepository.findById(baseHistoryId).orElseThrow();
    assertThat(original.getHighPressureWaterPrice()).isEqualTo(3000);
    assertThat(original.getMemberId()).isEqualTo(originalAuthorId);
    assertThat(original.getVisitExperience()).isEqualTo(VisitExperience.NOT_USED);
    assertThat(original.getObservedAt()).isEqualTo(SUBMITTED_AT.minusDays(100));
    CarWash carWash = carWashRepository.findById(carWashId).orElseThrow();
    assertThat(carWash.getTargetHistory().getId()).isEqualTo(next.getId());
    assertThat(carWash.getName()).isEqualTo("세차생활");
    assertThat(carWash.getLatitude()).isEqualTo(37.5);
    assertThat(carWash.getLongitude()).isEqualTo(127.0);
    assertThat(carWash.getUpdatedAt()).isEqualTo(SUBMITTED_AT);
  }

  @Test
  void aHigherConfidenceNameAndLocationCorrectionUpdatesTheMasterTogether() throws Exception {
    String request = modification(baseHistoryId, 3000).replace("세차생활", "새 세차생활")
                                                      .replace("37.5", "37.6")
                                                      .replace("127.0", "127.1");
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(request))
           .andExpect(status().isCreated());

    CarWash master = carWashRepository.findById(carWashId).orElseThrow();
    assertThat(master.getTargetHistory().getId()).isEqualTo(modificationHistory().getId());
    assertThat(master.getName()).isEqualTo("새 세차생활");
    assertThat(master.getLatitude()).isEqualTo(37.6);
    assertThat(master.getLongitude()).isEqualTo(127.1);
  }

  @Test
  void unchangedFieldsRetainOriginalSourcesAfterReloadingAMultiLevelChain() throws Exception {
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(modification(baseHistoryId, 3500)))
           .andExpect(status().isCreated());
    Long firstModificationId = modificationHistory().getId();
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(modification(firstModificationId, 3500).replace("세차생활", "새 이름")))
           .andExpect(status().isCreated());

    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      CarWashHistory latest = historyRepository.findAll().stream()
                                               .filter(history -> history.getChangedFields().equals(EnumSet.of(CarWashHistoryField.NAME)))
                                               .findFirst().orElseThrow();
      assertThat(latest.getSourceHistory(CarWashHistoryField.NAME).getId()).isEqualTo(latest.getId());
      assertThat(latest.getSourceHistory(CarWashHistoryField.HIGH_PRESSURE_WATER_PRICE).getId()).isEqualTo(firstModificationId);
      CarWashHistory original = latest.getSourceHistory(CarWashHistoryField.LATITUDE);
      assertThat(original.getId()).isEqualTo(baseHistoryId);
      assertThat(original.getObservedAt()).isEqualTo(SUBMITTED_AT.minusDays(100));
      assertThat(original.getVisitExperience()).isEqualTo(VisitExperience.NOT_USED);
    });
  }

  @Test
  void legacyRegistrationWithoutChangedFieldsRemainsAValidOriginalSource() throws Exception {
    jdbcTemplate.update("update car_wash_history set changed_field_names = null where id = ?", baseHistoryId);
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(modification(baseHistoryId, 3500)))
           .andExpect(status().isCreated());

    Long modificationId = modificationHistory().getId();
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      CarWashHistory legacy = historyRepository.findById(baseHistoryId).orElseThrow();
      assertThat(legacy.getChangedFields()).isEqualTo(EnumSet.allOf(CarWashHistoryField.class));
      CarWashHistory next = historyRepository.findById(modificationId).orElseThrow();
      assertThat(next.getChangedFields()).containsExactly(CarWashHistoryField.HIGH_PRESSURE_WATER_PRICE);
      assertThat(next.getSourceHistory(CarWashHistoryField.LATITUDE).getId()).isEqualTo(baseHistoryId);
      assertThat(next.getSourceHistory(CarWashHistoryField.HIGH_PRESSURE_WATER_PRICE).getId()).isEqualTo(modificationId);
    });
  }

  @Test
  void aCorrectionCanForkFromAnOlderHistory() throws Exception {
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(modification(baseHistoryId, 3500)))
           .andExpect(status().isCreated());
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(modification(baseHistoryId, 4000)))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.baseHistoryId").value(baseHistoryId));

    assertThat(historyRepository.count()).isEqualTo(3);
  }

  @Test
  void clearingNullableInformationIsStoredAsAChange() throws Exception {
    String request = modification(baseHistoryId, 3000).replace("\"address\":\"서울시\"", "\"address\":null")
                                                      .replace("\"highPressureWaterPrice\":3000", "\"highPressureWaterPrice\":null");
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(request))
           .andExpect(status().isCreated());

    CarWashHistory next = modificationHistory();
    assertThat(next.getAddress()).isNull();
    assertThat(next.getHighPressureWaterPrice()).isNull();
    assertThat(next.getChangedFields()).containsExactly(CarWashHistoryField.ADDRESS, CarWashHistoryField.HIGH_PRESSURE_WATER_PRICE);
  }

  @Test
  void changingOnlyMetadataDoesNotAppendAHistory() throws Exception {
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(modification(baseHistoryId, 3000)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    assertThat(historyRepository.count()).isEqualTo(1);
  }

  @Test
  void anotherCarWashHistoryCannotBeUsedAsTheBase() throws Exception {
    mockMvc.perform(post("/car-washes")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(SNAPSHOT))
           .andExpect(status().isCreated());
    Long otherHistoryId = historyRepository.findAll().stream().filter(history -> !history.getId().equals(baseHistoryId))
                                           .findFirst().orElseThrow().getId();
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(modification(otherHistoryId, 3500)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    assertThat(historyRepository.count()).isEqualTo(2);
  }

  @Test
  void missingCarWashIsReportedAsNotFound() throws Exception {
    mockMvc.perform(post("/car-washes/{id}/histories", Long.MAX_VALUE)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(modification(baseHistoryId, 3500)))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value("CAR_WASH_NOT_FOUND"));

    assertThat(historyRepository.count()).isEqualTo(1);
  }

  @Test
  void missingBaseHistoryIsReportedAsNotFound() throws Exception {
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(modification(Long.MAX_VALUE, 3500)))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.code").value("CAR_WASH_HISTORY_NOT_FOUND"));

    assertThat(historyRepository.count()).isEqualTo(1);
  }

  @ParameterizedTest(name = "invalid {0}")
  @MethodSource("invalidFields")
  void invalidRequestsCannotAppendOrChangeExistingInformation(String scenario, String original, String replacement) throws Exception {
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(modification(baseHistoryId, 3500).replace(original, replacement)))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    assertThat(historyRepository.count()).isEqualTo(1);
    assertThat(historyRepository.findById(baseHistoryId).orElseThrow().getHighPressureWaterPrice()).isEqualTo(3000);
    assertThat(carWashRepository.findById(carWashId).orElseThrow().getTargetHistory().getId()).isEqualTo(baseHistoryId);
  }

  static Stream<Arguments> invalidFields() {
    return Stream.of(
            Arguments.of("missing base", "\"baseHistoryId\":", "\"ignoredBase\":"),
            Arguments.of("null name", "\"name\":\"세차생활\"", "\"name\":null"),
            Arguments.of("blank name", "세차생활", " "),
            Arguments.of("long name", "세차생활", "가".repeat(256)),
            Arguments.of("long address", "서울시", "가".repeat(256)),
            Arguments.of("latitude", "37.5", "91"),
            Arguments.of("longitude", "127.0", "181"),
            Arguments.of("missing experience", "\"USED\"", "null"),
            Arguments.of("invalid experience", "\"USED\"", "\"UNKNOWN\""),
            Arguments.of("missing observation", "\"2026-10-06T12:00:00+09:00\"", "null"),
            Arguments.of("future observation", "2026-10-06", "2026-10-08"),
            Arguments.of("price", "\"highPressureWaterPrice\":3500", "\"highPressureWaterPrice\":-1"),
            Arguments.of("negative bays", "\"highPressureWaterPrice\":3500", "\"washBayCount\":-1"),
            Arguments.of("facility contradiction", "\"highPressureWaterPrice\":3500", "\"airGunAvailability\":\"UNAVAILABLE\",\"airGunPrice\":0")
    );
  }

  @Test
  void unauthenticatedRequestsCannotModify() throws Exception {
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId)
                            .contentType(MediaType.APPLICATION_JSON).content(modification(baseHistoryId, 3500)))
           .andExpect(status().isUnauthorized());

    assertThat(historyRepository.count()).isEqualTo(1);
  }

  @Test
  void malformedTokensCannotModify() throws Exception {
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId)
                            .header("Authorization", "Bearer invalid-token")
                            .contentType(MediaType.APPLICATION_JSON).content(modification(baseHistoryId, 3500)))
           .andExpect(status().isUnauthorized());

    assertThat(historyRepository.count()).isEqualTo(1);
  }

  @Test
  void suspendedMembersCannotModifyEvenWithAPreviouslyIssuedToken() throws Exception {
    jdbcTemplate.update("update members set status = 'SUSPENDED' where id = ?", modifyingMember.getId());
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(modification(baseHistoryId, 3500)))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));

    assertThat(historyRepository.count()).isEqualTo(1);
  }

  @Test
  void deletedMembersCannotModify() throws Exception {
    memberRepository.deleteById(modifyingMember.getId());
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(modification(baseHistoryId, 3500)))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

    assertThat(historyRepository.count()).isEqualTo(1);
  }

  private String modification(Long baseId, int price) {
    return SNAPSHOT.replace("{", "{\"baseHistoryId\":" + baseId + ",")
                   .replace("\"NOT_USED\"", "\"USED\"")
                   .replace("2026-06-29", "2026-10-06")
                   .replace("\"highPressureWaterPrice\":3000", "\"highPressureWaterPrice\":" + price);
  }

  private CarWashHistory modificationHistory() {
    return historyRepository.findAll().stream().filter(history -> history.getType() == CarWashHistoryType.MODIFICATION)
                            .findFirst().orElseThrow();
  }
}
