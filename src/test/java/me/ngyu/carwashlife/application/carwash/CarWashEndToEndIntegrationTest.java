package me.ngyu.carwashlife.application.carwash;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import javax.imageio.ImageIO;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistoryField;
import me.ngyu.carwashlife.application.carwash.domain.VisitExperience;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashHistoryRepository;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashPhotoRepository;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashRepository;
import me.ngyu.carwashlife.infrastructure.persistence.MemberRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(CarWashRegistrationApiIntegrationTest.FixedClockConfig.class)
@TestPropertySource(locations = "file:src/main/resources/application.properties", properties = {
        "spring.datasource.url=jdbc:h2:mem:car-wash-life-e2e;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "auth.jwt.secret=car-wash-life-real-http-e2e-test-secret-key",
        "server.address=127.0.0.1"
})
class CarWashEndToEndIntegrationTest {

  private static final Path ROOT = temporaryRoot();
  private static final String BOUNDARY = "car-wash-e2e-multipart-boundary";
  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-07T12:00:00+09:00");
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  @LocalServerPort
  private int port;
  @Autowired
  private CarWashPhotoRepository photoRepository;
  @Autowired
  private CarWashHistoryRepository historyRepository;
  @Autowired
  private CarWashRepository carWashRepository;
  @Autowired
  private MemberRepository memberRepository;
  @Autowired
  private JdbcTemplate jdbcTemplate;
  @Autowired
  private PlatformTransactionManager transactionManager;

  @DynamicPropertySource
  static void storageRoot(DynamicPropertyRegistry registry) {
    registry.add("carwash.photo.root", ROOT::toString);
  }

  @BeforeEach
  void setUp() {
    cleanDatabase();
  }

  @AfterEach
  void cleanUp() throws Exception {
    client.close();
    cleanDatabase();
    try (var files = Files.list(ROOT)) {
      for (Path file : files.toList()) {
        Files.deleteIfExists(file);
      }
    }
  }

  @AfterAll
  static void removeTemporaryRoot() throws Exception {
    Files.deleteIfExists(ROOT);
  }

  @Test
  @DisplayName("실제 HTTP로 등록과 수정을 이어서 요청하면 최고점 이력과 기존 정보 및 사진 출처를 보존한다.")
  void realHttpAuthenticationUploadRegistrationModificationAndDetailPreserveTheWinningSnapshot() throws Exception {
    String token = signupAndLogin();
    byte[] png = image();
    HttpResponse<String> firstUpload = upload(token, png);
    assertThat(firstUpload.statusCode()).isEqualTo(201);
    Long firstPhotoId = id(firstUpload.body(), "$.photoId");
    assertThat(JsonPath.<String>read(firstUpload.body(), "$.url")).isEqualTo("/car-wash-photos/" + firstPhotoId);
    assertThat(firstUpload.body()).doesNotContain("storageKey", "ownerId", ROOT.toString());

    HttpResponse<String> registration = postJson("/car-washes", token, snapshot("최초 세차생활", "NOT_USED", 37.5, 127.0, null, firstPhotoId));
    assertThat(registration.statusCode()).isEqualTo(201);
    assertThat(JsonPath.<Number>read(registration.body(), "$.confidence").intValue()).isEqualTo(3);
    Long carWashId = id(registration.body(), "$.carWashId");
    Long originalHistoryId = id(registration.body(), "$.historyId");

    HttpResponse<String> secondUpload = upload(token, png);
    assertThat(secondUpload.statusCode()).isEqualTo(201);
    Long secondPhotoId = id(secondUpload.body(), "$.photoId");
    HttpResponse<String> modification = postJson("/car-washes/" + carWashId + "/histories", token,
                                                 snapshot("이용한 세차생활", "USED", 37.6, 127.1, originalHistoryId, secondPhotoId));
    assertThat(modification.statusCode()).isEqualTo(201);
    assertThat(JsonPath.<Number>read(modification.body(), "$.confidence").intValue()).isEqualTo(5);
    Long winningHistoryId = id(modification.body(), "$.historyId");

    HttpResponse<String> lower = postJson("/car-washes/" + carWashId + "/histories", token,
                                          snapshot("낮은 점수 제보", "NOT_USED", 37.7, 127.2, winningHistoryId, null));
    assertThat(lower.statusCode()).isEqualTo(201);
    assertThat(JsonPath.<Number>read(lower.body(), "$.confidence").intValue()).isEqualTo(2);
    Long lowerHistoryId = id(lower.body(), "$.historyId");

    HttpResponse<String> detail = client.send(authenticated("/car-washes/" + carWashId, token).GET().build(), HttpResponse.BodyHandlers.ofString());
    assertThat(detail.statusCode()).isEqualTo(200);
    assertThat(id(detail.body(), "$.historyId")).isEqualTo(winningHistoryId);
    assertThat(JsonPath.<String>read(detail.body(), "$.name")).isEqualTo("이용한 세차생활");
    assertThat(JsonPath.<Number>read(detail.body(), "$.latitude").doubleValue()).isEqualTo(37.6);
    assertThat(JsonPath.<Number>read(detail.body(), "$.longitude").doubleValue()).isEqualTo(127.1);
    assertThat(JsonPath.<Number>read(detail.body(), "$.confidence").intValue()).isEqualTo(5);
    assertThat(id(detail.body(), "$.photos[0].photoId")).isEqualTo(firstPhotoId);
    assertThat(id(detail.body(), "$.photos[0].sourceHistoryId")).isEqualTo(originalHistoryId);
    assertThat(id(detail.body(), "$.photos[1].photoId")).isEqualTo(secondPhotoId);
    assertThat(id(detail.body(), "$.photos[1].sourceHistoryId")).isEqualTo(winningHistoryId);
    assertThat(detail.body()).doesNotContain("memberId", "ownerId", "storageKey", ROOT.toString());

    CarWash master = carWashRepository.findById(carWashId).orElseThrow();
    assertThat(master.getTargetHistory().getId()).isEqualTo(winningHistoryId);
    assertThat(master.getName()).isEqualTo("이용한 세차생활");
    assertThat(master.getLatitude()).isEqualTo(37.6);
    assertThat(master.getLongitude()).isEqualTo(127.1);
    assertThat(historyRepository.count()).isEqualTo(3);
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      CarWashHistory original = historyRepository.findById(originalHistoryId).orElseThrow();
      assertThat(original.getName()).isEqualTo("최초 세차생활");
      assertThat(original.getLatitude()).isEqualTo(37.5);
      assertThat(original.getLongitude()).isEqualTo(127.0);
      assertThat(original.getVisitExperience()).isEqualTo(VisitExperience.NOT_USED);
      assertThat(original.getPhotoIds()).containsExactly(firstPhotoId);
      CarWashHistory winning = historyRepository.findById(winningHistoryId).orElseThrow();
      assertThat(winning.getSourceHistory(CarWashHistoryField.NAME).getId()).isEqualTo(winningHistoryId);
      assertThat(winning.getSourceHistory(CarWashHistoryField.HIGH_PRESSURE_WATER_PRICE).getId()).isEqualTo(originalHistoryId);
      assertThat(winning.getPhotoIds()).containsExactly(firstPhotoId, secondPhotoId);
      CarWashHistory lowerHistory = historyRepository.findById(lowerHistoryId).orElseThrow();
      assertThat(lowerHistory.getEvidencePhotoIds()).isEmpty();
      assertThat(lowerHistory.getPhotoIds()).containsExactly(firstPhotoId, secondPhotoId);
    });
    HttpResponse<byte[]> photo = client.send(authenticated("/car-wash-photos/" + secondPhotoId, token).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    assertThat(photo.statusCode()).isEqualTo(200);
    assertThat(photo.headers().firstValue("Content-Type")).contains("image/png");
    assertThat(photo.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
    assertThat(photo.body()).isEqualTo(png);
  }

  @Test
  @DisplayName("인증 없이 실제 HTTP로 업로드하거나 등록하면 데이터와 파일을 만들지 않고 거절한다.")
  void unauthenticatedRealHttpUploadsAndRegistrationsHaveNoSideEffects() throws Exception {
    assertThat(upload(null, image()).statusCode()).isEqualTo(401);
    assertThat(postJson("/car-washes", null, snapshot("세차생활", "USED", 37.5, 127.0, null, null)).statusCode()).isEqualTo(401);

    assertNoPhotoOrCarWashSideEffects();
  }

  @Test
  @DisplayName("실제 업로드 파일이 5MiB를 초과하면 데이터와 파일을 만들지 않고 JSON 413 오류를 반환한다.")
  void theProductionSingleFileLimitReturnsAJson413ThroughTheRealServletContainer() throws Exception {
    String token = signupAndLogin();

    HttpResponse<String> response = upload(token, new byte[5 * 1024 * 1024 + 1]);

    assertThat(response.statusCode()).isEqualTo(413);
    assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("PHOTO_TOO_LARGE");
    assertNoPhotoOrCarWashSideEffects();
  }

  @Test
  @DisplayName("각 파일이 5MiB 이하라도 실제 업로드 요청 합계가 6MiB를 초과하면 부작용 없이 JSON 413 오류를 반환한다.")
  void theProductionTotalRequestLimitRejectsMultipleIndividuallySmallFilesWithoutSideEffects() throws Exception {
    String token = signupAndLogin();

    HttpResponse<String> response = upload(token, new byte[4 * 1024 * 1024], new byte[4 * 1024 * 1024]);

    assertThat(response.statusCode()).isEqualTo(413);
    assertThat(JsonPath.<String>read(response.body(), "$.code")).isEqualTo("PHOTO_TOO_LARGE");
    assertNoPhotoOrCarWashSideEffects();
  }

  private String signupAndLogin() throws Exception {
    String credentials = "{\"email\":\"http@example.com\",\"password\":\"WashLife!123\"}";
    assertThat(postJson("/auth/signup", null, credentials).statusCode()).isEqualTo(201);
    HttpResponse<String> login = postJson("/auth/login", null, credentials);
    assertThat(login.statusCode()).isEqualTo(200);
    return JsonPath.read(login.body(), "$.accessToken");
  }

  private HttpResponse<String> postJson(String path, String token, String json) throws Exception {
    return client.send(authenticated(path, token).header("Content-Type", "application/json")
                                                 .POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> upload(String token, byte[]... files) throws Exception {
    ByteArrayOutputStream multipart = new ByteArrayOutputStream();
    for (byte[] file : files) {
      multipart.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
      multipart.write(file);
      multipart.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }
    multipart.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return client.send(authenticated("/car-wash-photos", token).header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                                                               .POST(HttpRequest.BodyPublishers.ofByteArray(multipart.toByteArray())).build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpRequest.Builder authenticated(String path, String token) {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(20));
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    return request;
  }

  private String snapshot(String name, String experience, double latitude, double longitude, Long baseId, Long photoId) {
    return """
            {"name":"%s","address":"서울시","latitude":%s,"longitude":%s,"visitExperience":"%s",
             "observedAt":"%s","highPressureWaterPrice":3000,"foamLanceAvailability":"AVAILABLE","foamGunPrice":4000,
             "airGunAvailability":"AVAILABLE","airGunPrice":0,"vacuumAvailability":"AVAILABLE","vacuumPrice":1000,
             "washBayCount":6,"dryingBayCount":10,"photoIds":%s%s}
            """.formatted(name, latitude, longitude, experience, NOW.minusDays(1), photoId == null ? "[]" : "[" + photoId + "]",
                          baseId == null ? "" : ",\"baseHistoryId\":" + baseId);
  }

  private static Long id(String json, String path) {
    return ((Number) JsonPath.read(json, path)).longValue();
  }

  private void assertNoPhotoOrCarWashSideEffects() throws Exception {
    assertThat(photoRepository.count()).isZero();
    assertThat(historyRepository.count()).isZero();
    assertThat(carWashRepository.count()).isZero();
    try (var files = Files.list(ROOT)) {
      assertThat(files.count()).isZero();
    }
  }

  private void cleanDatabase() {
    photoRepository.deleteAllInBatch();
    jdbcTemplate.update("update car_wash set target_history_id = null");
    historyRepository.deleteAllInBatch();
    carWashRepository.deleteAllInBatch();
    memberRepository.deleteAllInBatch();
  }

  private static byte[] image() throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
    return output.toByteArray();
  }

  private static Path temporaryRoot() {
    try {
      return Files.createTempDirectory("car-wash-http-e2e-");
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
