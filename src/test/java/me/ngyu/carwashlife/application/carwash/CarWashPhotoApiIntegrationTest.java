package me.ngyu.carwashlife.application.carwash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import me.ngyu.carwashlife.application.carwash.api.dto.CarWashInformationDto;
import me.ngyu.carwashlife.application.carwash.api.dto.RegisterCarWashDto;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory;
import me.ngyu.carwashlife.application.carwash.domain.CarWashPhoto;
import me.ngyu.carwashlife.application.carwash.domain.VisitExperience;
import me.ngyu.carwashlife.application.carwash.service.CarWashPhotoService;
import me.ngyu.carwashlife.application.carwash.service.CarWashRegistrationService;
import me.ngyu.carwashlife.application.carwash.service.CarWashRepresentativeService;
import me.ngyu.carwashlife.application.member.domain.Member;
import me.ngyu.carwashlife.common.exception.ApplicationException;
import me.ngyu.carwashlife.common.exception.ErrorCode;
import me.ngyu.carwashlife.common.security.AccessTokenProvider;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashHistoryRepository;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashPhotoRepository;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashRepository;
import me.ngyu.carwashlife.infrastructure.persistence.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Import(CarWashRegistrationApiIntegrationTest.FixedClockConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class CarWashPhotoApiIntegrationTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-07T12:00:00+09:00");
  @TempDir
  static Path root;
  private static final String SNAPSHOT = """
          {"visitExperience":"USED","observedAt":"2026-10-06T12:00:00+09:00",
           "information":{"name":"세차생활","latitude":37.5,"longitude":127.0}}
          """;

  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private CarWashPhotoRepository photoRepository;
  @Autowired
  private CarWashHistoryRepository historyRepository;
  @Autowired
  private CarWashRepository carWashRepository;
  @Autowired
  private MemberRepository memberRepository;
  @Autowired
  private AccessTokenProvider tokenProvider;
  @Autowired
  private CarWashPhotoService photoService;
  @Autowired
  private CarWashRegistrationService registrationService;
  @Autowired
  private CarWashRepresentativeService representativeService;
  @Autowired
  private PlatformTransactionManager transactionManager;
  @Autowired
  private JdbcTemplate jdbcTemplate;
  @Autowired
  private EntityManager entityManager;

  private Long memberId;
  private String token;
  private String otherToken;

  @DynamicPropertySource
  static void storageRoot(DynamicPropertyRegistry registry) {
    registry.add("carwash.photo.root", root::toString);
  }

  @BeforeEach
  void setUp() throws Exception {
    cleanDatabase();
    Member owner = memberRepository.saveAndFlush(Member.create("photo@example.com", "encoded", null, null, null, NOW));
    Member other = memberRepository.saveAndFlush(Member.create("other@example.com", "encoded", null, null, null, NOW));
    memberId = owner.getId();
    token = tokenProvider.create(memberId);
    otherToken = tokenProvider.create(other.getId());
  }

  @AfterEach
  void cleanUp() throws IOException {
    cleanDatabase();
    try (var files = Files.list(root)) {
      for (Path file : files.toList()) {
        Files.deleteIfExists(file);
      }
    }
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @DisplayName("JPEG나 PNG를 업로드하면 원본 파일명 대신 생성한 저장 키로 실제 이미지 파일을 저장한다.")
  @ValueSource(strings = {"png", "jpeg"})
  void uploadsDecodeImagesAndStoreOnlyGeneratedKeys(String format) throws Exception {
    byte[] bytes = image(format);
    mockMvc.perform(multipart("/car-wash-photos").file(new MockMultipartFile("file", "../../private\\photo.exe", "image/" + format, bytes))
                                                 .header("Authorization", "Bearer " + token))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.photoId").isNumber())
           .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("/car-wash-photos/")))
           .andExpect(jsonPath("$.storageKey").doesNotExist()).andExpect(jsonPath("$.ownerId").doesNotExist());

    CarWashPhoto photo = photoRepository.findAll().getFirst();
    assertThat(photo.isPending()).isTrue();
    assertThat(photo.getContentType()).isEqualTo("image/" + format);
    assertThat(photo.getSize()).isEqualTo(bytes.length);
    assertThat(photo.getStorageKey()).matches("[a-f0-9-]{36}");
    assertThat(Files.readAllBytes(root.resolve(photo.getStorageKey()))).isEqualTo(bytes);
  }

  @Test
  @DisplayName("저장된 사진 파일을 읽지 못하면 원인 예외를 보존하고 내부 저장 정보를 공개하지 않는다.")
  void storageReadFailuresPreserveTheirCauseWithoutExposingStorageDetails(CapturedOutput output) throws Exception {
    Long photoId = upload();
    String key = photoRepository.findById(photoId).orElseThrow().getStorageKey();
    Files.delete(root.resolve(key));

    MvcResult result = mockMvc.perform(get("/car-wash-photos/{id}", photoId).header("Authorization", "Bearer " + token))
                              .andExpect(status().isInternalServerError())
                              .andExpect(jsonPath("$.code").value("PHOTO_STORAGE_ERROR"))
                              .andExpect(jsonPath("$.cause").doesNotExist())
                              .andExpect(jsonPath("$.storageKey").doesNotExist()).andReturn();

    assertThat(result.getResolvedException()).isInstanceOf(ApplicationException.class).hasCauseInstanceOf(IOException.class);
    assertThat(result.getResponse().getContentAsString()).doesNotContain(key, root.toString());
    assertThat(photoRepository.count()).isEqualTo(1);
    assertThat(output).contains("서버 오류가 발생했습니다. code=PHOTO_STORAGE_ERROR", "java.nio.file.NoSuchFileException", root.toString(), key);
    assertThat(output.getAll().lines().filter(line -> line.contains("서버 오류가 발생했습니다. code=PHOTO_STORAGE_ERROR")).count()).isEqualTo(1);
  }

  @Test
  @DisplayName("업로드 파일을 읽지 못하면 500을 반환하고 원인을 기록하되 사진 정보나 파일을 저장하지 않는다.")
  void uploadReadFailuresPreserveTheirCauseWithoutSavingAPhoto(CapturedOutput output) throws Exception {
    IOException failure = new IOException("업로드 파일 읽기 실패");
    MockMultipartFile unreadable = new MockMultipartFile("file", "photo.png", "image/png", image("png")) {
      @Override
      public InputStream getInputStream() throws IOException {
        throw failure;
      }
    };

    MvcResult result = mockMvc.perform(multipart("/car-wash-photos").file(unreadable).header("Authorization", "Bearer " + token))
                              .andExpect(status().isInternalServerError())
                              .andExpect(jsonPath("$.code").value("PHOTO_STORAGE_ERROR"))
                              .andExpect(jsonPath("$.cause").doesNotExist()).andReturn();

    assertThat(result.getResolvedException()).isInstanceOf(ApplicationException.class).hasCause(failure);
    assertThat(result.getResponse().getContentAsString()).doesNotContain(failure.getMessage());
    assertThat(output).contains("서버 오류가 발생했습니다. code=PHOTO_STORAGE_ERROR", "java.io.IOException: " + failure.getMessage());
    assertThat(photoRepository.count()).isZero();
    try (var files = Files.list(root)) {
      assertThat(files.count()).isZero();
    }
  }

  @Test
  @DisplayName("미첨부 사진은 소유자에게만 공개하고 첨부한 사진과 상세 정보는 비회원도 조회할 수 있다.")
  void pendingPhotosArePrivateAndAttachedPhotosAndDetailsArePublic() throws Exception {
    Long photoId = upload();
    byte[] bytes = image("png");
    mockMvc.perform(get("/car-wash-photos/{id}", photoId).header("Authorization", "Bearer " + token))
           .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
           .andExpect(header().string("X-Content-Type-Options", "nosniff")).andExpect(content().bytes(bytes));
    mockMvc.perform(get("/car-wash-photos/{id}", photoId).header("Authorization", "Bearer " + otherToken))
           .andExpect(status().isNotFound());
    mockMvc.perform(get("/car-wash-photos/{id}", photoId))
           .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CAR_WASH_PHOTO_NOT_FOUND"));
    Long carWashId = register(List.of(photoId), 5);
    mockMvc.perform(get("/car-wash-photos/{id}", photoId))
           .andExpect(status().isOk()).andExpect(content().bytes(bytes));
    mockMvc.perform(get("/car-wash-photos/{id}", photoId).header("Authorization", "Bearer " + otherToken))
           .andExpect(status().isOk()).andExpect(content().bytes(bytes));
    Long historyId = carWashRepository.findById(carWashId).orElseThrow().getTargetHistory().getId();
    mockMvc.perform(get("/car-washes/{id}", carWashId))
           .andExpect(status().isOk()).andExpect(jsonPath("$.confidence").value(5))
           .andExpect(jsonPath("$.photos[0].photoId").value(photoId))
           .andExpect(jsonPath("$.photos[0].url").value("/car-wash-photos/" + photoId))
           .andExpect(jsonPath("$.photos[0].sourceHistoryId").value(historyId))
           .andExpect(jsonPath("$.photos[0].storageKey").doesNotExist());
  }

  @Test
  @DisplayName("새 사진만 추가하는 수정 요청도 저장하고 신뢰도 5점 이력을 대표로 반영한다.")
  void photosOnlyModificationIsAcceptedAndCanChangeTheRepresentativeToFivePoints() throws Exception {
    Long carWashId = register(List.of(), 4);
    Long baseHistoryId = carWashRepository.findById(carWashId).orElseThrow().getTargetHistory().getId();
    Long photoId = upload();
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId).header("Authorization", "Bearer " + token)
                                                                 .contentType(MediaType.APPLICATION_JSON)
                                                                 .content(modification(baseHistoryId, List.of(photoId))))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.confidence").value(5))
           .andExpect(jsonPath("$.changedFields[0]").value("PHOTOS"));

    CarWashHistory winner = historyRepository.findById(carWashRepository.findById(carWashId).orElseThrow().getTargetHistory().getId()).orElseThrow();
    assertThat(winner.getId()).isNotEqualTo(baseHistoryId);
    assertThat(winner.getPhotoIds()).containsExactly(photoId);
    assertThat(winner.getEvidencePhotoIds()).containsExactly(photoId);
    assertThat(historyRepository.findById(baseHistoryId).orElseThrow().getPhotoIds()).isEmpty();
  }

  @Test
  @DisplayName("상속한 사진은 수정 이력에 보존하되 새 사진 근거 점수를 가산하지 않는다.")
  void inheritedPhotosRemainVisibleButDoNotGrantTheNewHistoryAPhotoPoint() throws Exception {
    Long photoId = upload();
    Long carWashId = register(List.of(photoId), 5);
    Long baseHistoryId = carWashRepository.findById(carWashId).orElseThrow().getTargetHistory().getId();
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId).header("Authorization", "Bearer " + token)
                                                                 .contentType(MediaType.APPLICATION_JSON)
                                                                 .content(modification(baseHistoryId, List.of()).replace("세차생활", "새 이름")))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.confidence").value(4));

    CarWashHistory next = historyRepository.findAll().stream().filter(history -> !history.getId().equals(baseHistoryId)).findFirst().orElseThrow();
    assertThat(next.getPhotoIds()).containsExactly(photoId);
    assertThat(next.getEvidencePhotoIds()).isEmpty();
    assertThat(next.hasEvidencePhotos()).isFalse();
    assertThat(carWashRepository.findById(carWashId).orElseThrow().getTargetHistory().getId()).isEqualTo(baseHistoryId);
  }

  @Test
  @DisplayName("수정 요청에 새 사진을 첨부하면 기존 사진과 각각의 원래 출처를 함께 보존한다.")
  void modificationAddsNewPhotosWithoutLosingExistingPhotosOrTheirOriginalSources() throws Exception {
    Long oldPhotoId = upload();
    Long carWashId = register(List.of(oldPhotoId), 5);
    Long baseHistoryId = carWashRepository.findById(carWashId).orElseThrow().getTargetHistory().getId();
    Long newPhotoId = upload();
    mockMvc.perform(post("/car-washes/{id}/histories", carWashId).header("Authorization", "Bearer " + token)
                                                                 .contentType(MediaType.APPLICATION_JSON)
                                                                 .content(modification(baseHistoryId, List.of(newPhotoId))))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.confidence").value(5));
    Long historyId = carWashRepository.findById(carWashId).orElseThrow().getTargetHistory().getId();
    mockMvc.perform(get("/car-washes/{id}", carWashId).header("Authorization", "Bearer " + token))
           .andExpect(status().isOk()).andExpect(jsonPath("$.photos.length()").value(2))
           .andExpect(jsonPath("$.photos[0].sourceHistoryId").value(baseHistoryId))
           .andExpect(jsonPath("$.photos[1].sourceHistoryId").value(historyId));
  }

  @Test
  @DisplayName("등록 요청이 실패하면 선업로드한 사진을 미첨부 상태로 보존하고 재시도에서 사용한다.")
  void failedSubmissionKeepsThePreuploadedPhotoPendingAndAllowsRetry() throws Exception {
    Long photoId = upload();
    String bad = withPhotos(SNAPSHOT, List.of(photoId)).replace("\"name\":", "\"airGunAvailability\":\"UNAVAILABLE\",\"airGunPrice\":0,\"name\":");
    mockMvc.perform(post("/car-washes").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(bad))
           .andExpect(status().isBadRequest());

    assertThat(carWashRepository.count()).isZero();
    assertThat(historyRepository.count()).isZero();
    assertThat(photoRepository.findById(photoId).orElseThrow().isPending()).isTrue();
    try (var files = Files.list(root)) {
      assertThat(files.count()).isEqualTo(1);
    }
    register(List.of(photoId), 5);
  }

  @Test
  @DisplayName("중복되거나 존재하지 않거나 다른 회원 소유이거나 이미 첨부한 사진을 제출하면 등록을 거절한다.")
  void duplicateMissingForeignAndAlreadyUsedPhotosCannotBeSubmitted() throws Exception {
    Long photoId = upload();
    for (List<Long> ids : List.of(List.of(photoId, photoId), List.of(Long.MAX_VALUE))) {
      mockMvc.perform(post("/car-washes").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(withPhotos(SNAPSHOT, ids)))
             .andExpect(status().isBadRequest());
    }
    mockMvc.perform(post("/car-washes").header("Authorization", "Bearer " + otherToken).contentType(MediaType.APPLICATION_JSON).content(withPhotos(SNAPSHOT, List.of(photoId))))
           .andExpect(status().isBadRequest());
    register(List.of(photoId), 5);
    mockMvc.perform(post("/car-washes").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(withPhotos(SNAPSHOT, List.of(photoId))))
           .andExpect(status().isBadRequest());

    assertThat(carWashRepository.count()).isEqualTo(1);
    assertThat(historyRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("한 요청에는 신규 사진을 최대 5개 첨부하고 초과한 요청은 거절한다.")
  void atMostFiveNewPhotosCanBeAttachedToOneRequest() throws Exception {
    List<Long> ids = new ArrayList<>();
    for (int count = 0; count < 6; count++) {
      ids.add(upload());
    }
    mockMvc.perform(post("/car-washes").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(withPhotos(SNAPSHOT, ids)))
           .andExpect(status().isBadRequest());
    Long carWashId = register(ids.subList(0, 5), 5);
    assertThat(historyRepository.findById(carWashRepository.findById(carWashId).orElseThrow().getTargetHistory().getId()).orElseThrow().getPhotoIds()).hasSize(5);
    assertThat(photoRepository.findById(ids.get(5)).orElseThrow().isPending()).isTrue();
  }

  @Test
  @DisplayName("비어 있거나 이미지가 아니거나 손상되거나 선언한 형식과 다른 파일은 저장하지 않고 거절한다.")
  void emptyNonImageCorruptAndMismatchedMimeUploadsAreRejectedWithoutFiles() throws Exception {
    List<MockMultipartFile> invalid = List.of(
            new MockMultipartFile("file", "empty.png", "image/png", new byte[0]),
            new MockMultipartFile("file", "text.png", "image/png", "text".getBytes()),
            new MockMultipartFile("file", "corrupt.png", "image/png", java.util.Arrays.copyOf(image("png"), 40)),
            new MockMultipartFile("file", "fake.jpeg", "image/jpeg", image("png")));
    for (MockMultipartFile file : invalid) {
      mockMvc.perform(multipart("/car-wash-photos").file(file).header("Authorization", "Bearer " + token))
             .andExpect(status().isBadRequest());
    }
    assertThat(photoRepository.count()).isZero();
    try (var files = Files.list(root)) {
      assertThat(files.count()).isZero();
    }
  }

  @Test
  @DisplayName("사진이 5MiB를 초과하면 사진 정보를 저장하지 않고 413 오류를 반환한다.")
  void oversizedUploadsReturnPayloadTooLarge() throws Exception {
    mockMvc.perform(multipart("/car-wash-photos").file(new MockMultipartFile("file", "large.png", "image/png", new byte[5 * 1024 * 1024 + 1]))
                                                 .header("Authorization", "Bearer " + token))
           .andExpect(status().is(413)).andExpect(jsonPath("$.code").value("PHOTO_TOO_LARGE"));
    assertThat(photoRepository.count()).isZero();
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @DisplayName("사진의 변 길이나 전체 픽셀 수가 제한을 초과하면 사진을 저장하지 않고 거절한다.")
  @CsvSource({"6000, 6000", "10001, 1"})
  void excessivePixelDimensionsAreRejectedBeforeImageDecodingAllocatesTheImage(int width, int height) throws Exception {
    byte[] png = image("png");
    ByteBuffer.wrap(png, 16, 8).putInt(width).putInt(height);
    CRC32 crc = new CRC32();
    crc.update(png, 12, 17);
    ByteBuffer.wrap(png, 29, 4).putInt((int) crc.getValue());
    mockMvc.perform(multipart("/car-wash-photos").file(new MockMultipartFile("file", "large-dimensions.png", "image/png", png))
                                                 .header("Authorization", "Bearer " + token))
           .andExpect(status().isBadRequest());
    assertThat(photoRepository.count()).isZero();
  }

  @Test
  @DisplayName("인증하지 않았거나 활성 회원이 아니면 사진 업로드를 거절한다.")
  void authenticationAndActiveAccountsAreRequiredForUploads() throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", image("png"));
    mockMvc.perform(multipart("/car-wash-photos").file(file)).andExpect(status().isUnauthorized());
    mockMvc.perform(multipart("/car-wash-photos").file(file).header("Authorization", "Bearer invalid-token"))
           .andExpect(status().isUnauthorized());
    jdbcTemplate.update("update members set status = 'SUSPENDED' where id = ?", memberId);
    mockMvc.perform(multipart("/car-wash-photos").file(file).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    memberRepository.deleteById(memberId);
    mockMvc.perform(multipart("/car-wash-photos").file(file).header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    assertThat(photoRepository.count()).isZero();
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @DisplayName("첨부 사진은 잘못된 토큰이나 정지·삭제된 회원의 토큰으로도 조회할 수 있다.")
  @ValueSource(strings = {"INVALID_TOKEN", "SUSPENDED", "DELETED"})
  void attachedPhotosArePublicRegardlessOfTokenOrMemberState(String state) throws Exception {
    Long photoId = upload();
    register(List.of(photoId), 5);
    String readToken = token;
    if (state.equals("INVALID_TOKEN")) {
      readToken = "invalid-token";
    } else if (state.equals("SUSPENDED")) {
      jdbcTemplate.update("update members set status = 'SUSPENDED' where id = ?", memberId);
    } else {
      memberRepository.deleteById(memberId);
    }

    mockMvc.perform(get("/car-wash-photos/{id}", photoId).header("Authorization", "Bearer " + readToken))
           .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
           .andExpect(content().bytes(image("png")));
  }

  @Test
  @DisplayName("잘못된 토큰은 익명으로 취급하여 미첨부 사진 조회에 404를 반환한다.")
  void invalidTokensCannotReadPendingPhotos() throws Exception {
    Long photoId = upload();

    mockMvc.perform(get("/car-wash-photos/{id}", photoId).header("Authorization", "Bearer invalid-token"))
           .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CAR_WASH_PHOTO_NOT_FOUND"));
  }

  @Test
  @DisplayName("정지된 소유자는 미첨부 사진 조회에 비활성 계정 오류를 받는다.")
  void inactiveOwnersCannotReadPendingPhotos() throws Exception {
    Long photoId = upload();
    jdbcTemplate.update("update members set status = 'SUSPENDED' where id = ?", memberId);

    mockMvc.perform(get("/car-wash-photos/{id}", photoId).header("Authorization", "Bearer " + token))
           .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
  }

  @Test
  @DisplayName("삭제된 소유자의 토큰으로 미첨부 사진을 조회하면 인증 오류를 반환한다.")
  void deletedOwnersCannotReadPendingPhotos() throws Exception {
    Long photoId = upload();
    memberRepository.deleteById(memberId);

    mockMvc.perform(get("/car-wash-photos/{id}", photoId).header("Authorization", "Bearer " + token))
           .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
  }

  @Test
  @DisplayName("사진이 없으면 조회에 404 오류를 반환하고 업로드 파일이 없으면 400 오류를 반환한다.")
  void missingPhotoAndMissingUploadPartAreReported(CapturedOutput output) throws Exception {
    mockMvc.perform(get("/car-wash-photos/{id}", Long.MAX_VALUE).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
    mockMvc.perform(multipart("/car-wash-photos").header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
    assertThat(output).doesNotContain("서버 오류가 발생했습니다. code=");
  }

  @Test
  @DisplayName("업로드를 포함한 트랜잭션을 롤백하면 사진 정보와 실제 파일을 삭제한다.")
  void outerTransactionRollbackRemovesUploadedMetadataAndItsActualFile() throws Exception {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      photoService.upload(memberId, new MockMultipartFile("file", "photo.png", "image/png", imageUnchecked()));
      assertThat(photoRepository.count()).isEqualTo(1);
      status.setRollbackOnly();
    });

    assertThat(photoRepository.count()).isZero();
    try (var files = Files.list(root)) {
      assertThat(files.count()).isZero();
    }
  }

  @Test
  @DisplayName("업로드 완료 상태가 불명이면 사진 파일을 보존하고 저장 키와 완료 상태를 기록한다.")
  void unknownUploadCompletionPreservesTheFileAndLogsItsKeyAndStatus(CapturedOutput output) {
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      List<TransactionSynchronization> existing = TransactionSynchronizationManager.getSynchronizations();
      var uploaded = photoService.upload(memberId, new MockMultipartFile("file", "photo.png", "image/png", imageUnchecked()));
      CarWashPhoto photo = photoRepository.findById(uploaded.photoId()).orElseThrow();
      List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations().stream()
                                                                                    .filter(callback -> !existing.contains(callback)).toList();
      assertThat(callbacks).hasSize(1);

      callbacks.getFirst().afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);

      assertThat(root.resolve(photo.getStorageKey())).hasBinaryContent(imageUnchecked());
      assertThat(output).contains("파일을 보존합니다.", "storageKey=" + photo.getStorageKey(), "status=" + TransactionSynchronization.STATUS_UNKNOWN);
    });

    assertThat(photoRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("업로드 트랜잭션의 커밋 과정이 실패하면 사진 정보와 실제 파일을 삭제한다.")
  void failureWhileCommittingAnUploadAlsoRemovesTheFile() throws Exception {
    assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      photoService.upload(memberId, new MockMultipartFile("file", "photo.png", "image/png", imageUnchecked()));
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void beforeCommit(boolean readOnly) {
          throw new IllegalStateException("검증용 커밋 실패");
        }
      });
    })).isInstanceOf(IllegalStateException.class);

    assertThat(photoRepository.count()).isZero();
    try (var files = Files.list(root)) {
      assertThat(files.count()).isZero();
    }
  }

  @Test
  @DisplayName("사진 잠금 조회는 해당 사진에 비관적 쓰기 잠금을 적용하고 없는 사진은 반환하지 않는다.")
  void photoLockQueryRequestsAPessimisticWriteLock() throws Exception {
    Long photoId = upload();
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      CarWashPhoto locked = photoRepository.findByIdForUpdate(photoId).orElseThrow();
      assertThat(locked.getId()).isEqualTo(photoId);
      assertThat(entityManager.getLockMode(locked)).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
      assertThat(photoRepository.findByIdForUpdate(Long.MAX_VALUE)).isEmpty();
    });
  }

  @Test
  @DisplayName("사진 근거 이력의 최근성 점수가 만료되면 최신 관찰 이력으로 대표 정보를 교체한다.")
  void recencyExpirationCanReplaceAnOlderPhotoBackedRepresentative() throws Exception {
    Long photoId = upload();
    mockMvc.perform(post("/car-washes").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                                       .content(withPhotos(SNAPSHOT, List.of(photoId)).replace("2026-10-06T12:00:00+09:00", NOW.minusDays(90).toString())))
           .andExpect(status().isCreated()).andExpect(jsonPath("$.confidence").value(5));
    Long carWashId = carWashRepository.findAll().getFirst().getId();
    Long baseHistoryId = historyRepository.findAll().getFirst().getId();
    MvcResult modification = mockMvc.perform(post("/car-washes/{id}/histories", carWashId).header("Authorization", "Bearer " + token)
                                                                                          .contentType(MediaType.APPLICATION_JSON)
                                                                                          .content(modification(baseHistoryId, List.of()).replace("세차생활", "최근 이름")))
                                    .andExpect(status().isCreated()).andExpect(jsonPath("$.confidence").value(4)).andReturn();
    Long nextId = ((Number) JsonPath.read(modification.getResponse().getContentAsString(), "$.historyId")).longValue();
    assertThat(carWashRepository.findById(carWashId).orElseThrow().getTargetHistory().getId()).isEqualTo(baseHistoryId);

    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      var master = carWashRepository.findByIdForUpdate(carWashId).orElseThrow();
      var selected = representativeService.selectAndReflect(master, NOW.plusDays(1));
      assertThat(selected.history().getId()).isEqualTo(nextId);
      assertThat(selected.confidence()).isEqualTo(4);
    });

    var master = carWashRepository.findById(carWashId).orElseThrow();
    assertThat(master.getTargetHistory().getId()).isEqualTo(nextId);
    assertThat(master.getName()).isEqualTo("최근 이름");
    assertThat(historyRepository.findById(baseHistoryId).orElseThrow().getEvidencePhotoIds()).containsExactly(photoId);
  }

  @Test
  @DisplayName("기존 이력의 사진 컬럼이 null이면 사진 없는 정보로 조회한다.")
  void legacyNullPhotoColumnsAreReturnedAsAnEmptyPhotoSnapshot() throws Exception {
    Long carWashId = register(List.of(), 4);
    Long historyId = carWashRepository.findById(carWashId).orElseThrow().getTargetHistory().getId();
    jdbcTemplate.update("update car_wash_history set photo_id_values = null, evidence_photo_id_values = null where id = ?", historyId);

    mockMvc.perform(get("/car-washes/{id}", carWashId).header("Authorization", "Bearer " + token))
           .andExpect(status().isOk()).andExpect(jsonPath("$.confidence").value(4)).andExpect(jsonPath("$.photos").isEmpty());
  }

  @Test
  @DisplayName("동시에 등록 요청을 제출해도 하나의 미첨부 사진은 한 이력에만 연결한다.")
  void concurrentRequestsCanConsumeOnePendingPhotoOnlyOnce() throws Exception {
    Long photoId = upload();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      for (int attempt = 0; attempt < 2; attempt++) {
        results.add(executor.submit(() -> {
          ready.countDown();
          if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("사진 소비 시작 대기 시간 초과");
          }
          try {
            registrationService.register(memberId, new RegisterCarWashDto.Request(VisitExperience.USED, NOW.minusDays(1),
                                                                                  new CarWashInformationDto.Request("세차생활", null, 37.5, 127.0, null, null, null, null, null, null, null, null, null),
                                                                                  List.of(photoId)));
            return true;
          } catch (ApplicationException exception) {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
            return false;
          }
        }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(List.of(results.get(0).get(20, TimeUnit.SECONDS), results.get(1).get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
      assertThat(carWashRepository.count()).isEqualTo(1);
      assertThat(historyRepository.count()).isEqualTo(1);
      assertThat(photoRepository.findById(photoId).orElseThrow().isPending()).isFalse();
    } finally {
      start.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  private Long upload() throws Exception {
    MvcResult result = mockMvc.perform(multipart("/car-wash-photos").file(new MockMultipartFile("file", "photo.png", "image/png", image("png")))
                                                                    .header("Authorization", "Bearer " + token))
                              .andExpect(status().isCreated()).andReturn();
    return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.photoId")).longValue();
  }

  private Long register(List<Long> photos, int confidence) throws Exception {
    MvcResult result = mockMvc.perform(post("/car-washes").header("Authorization", "Bearer " + token)
                                                          .contentType(MediaType.APPLICATION_JSON).content(withPhotos(SNAPSHOT, photos)))
                              .andExpect(status().isCreated()).andExpect(jsonPath("$.confidence").value(confidence)).andReturn();
    return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.carWashId")).longValue();
  }

  private String modification(Long baseHistoryId, List<Long> photos) {
    return withPhotos(SNAPSHOT.replaceFirst("\\{", "{\"baseHistoryId\":" + baseHistoryId + ","), photos);
  }

  private static String withPhotos(String snapshot, List<Long> photos) {
    return snapshot.replaceFirst("\\{", "{\"photoIds\":" + photos + ",");
  }

  private void cleanDatabase() {
    photoRepository.deleteAllInBatch();
    jdbcTemplate.update("update car_wash set target_history_id = null");
    historyRepository.deleteAllInBatch();
    carWashRepository.deleteAllInBatch();
    memberRepository.deleteAllInBatch();
  }

  private static byte[] image(String format) throws IOException {
    BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, format, output);
    return output.toByteArray();
  }

  private static byte[] imageUnchecked() {
    try {
      return image("png");
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }


}
