package me.ngyu.carwashlife.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import me.ngyu.carwashlife.application.member.domain.Member;
import me.ngyu.carwashlife.application.member.domain.VehicleType;
import me.ngyu.carwashlife.application.member.domain.WashExperience;
import me.ngyu.carwashlife.infrastructure.persistence.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthApiIntegrationTest.CurrentMemberController.class)
class AuthApiIntegrationTest {

  private static final String SIGNUP_REQUEST = """
          {
            "email": "User@Example.com",
            "password": "WashLife!123",
            "residenceRegionCode": "11680",
            "vehicleType": "SUV",
            "washExperience": "OCCASIONAL"
          }
          """;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private MemberRepository memberRepository;

  @Autowired
  private PasswordEncoder passwordEncoder;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    memberRepository.deleteAll();
  }

  @DisplayName("회원가입하면 이메일을 정규화하고 비밀번호 해시와 회원 정보를 저장한다.")
  @Test
  void signupStoresRequiredAndOptionalMemberInformation() throws Exception {
    mockMvc.perform(post("/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIGNUP_REQUEST))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.memberId").isNumber())
           .andExpect(jsonPath("$.email").value("user@example.com"))
           .andExpect(jsonPath("$.createdAt", endsWith("+09:00")));

    Member member = memberRepository.findByEmail("user@example.com").orElseThrow();
    assertThat(passwordEncoder.matches("WashLife!123", member.getPassword())).isTrue();
    assertThat(member.getPassword()).isNotEqualTo("WashLife!123");
    assertThat(member.getResidenceRegionCode()).isEqualTo("11680");
    assertThat(member.getVehicleType()).isEqualTo(VehicleType.SUV);
    assertThat(member.getWashExperience()).isEqualTo(WashExperience.OCCASIONAL);
    assertThat(member.isEmailVerified()).isTrue();
  }

  @DisplayName("이미 가입한 이메일로 회원가입하면 중복 오류를 반환한다.")
  @Test
  void signupRejectsDuplicateEmail() throws Exception {
    signup();

    mockMvc.perform(post("/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIGNUP_REQUEST))
           .andExpect(status().isConflict())
           .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
  }

  @DisplayName("이메일이나 비밀번호가 올바르지 않으면 회원가입을 거절한다.")
  @Test
  void signupRejectsInvalidRequest() throws Exception {
    mockMvc.perform(post("/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @DisplayName("로그인하면 회원 식별자와 만료 시각을 담은 24시간 액세스 토큰을 발급한다.")
  @Test
  void loginIssuesAccessTokenValidForTwentyFourHours() throws Exception {
    Member member = signup();
    long issuedAfter = Instant.now().getEpochSecond();

    MvcResult result = mockMvc.perform(post("/auth/login")
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .content(loginJson("WashLife!123")))
                              .andExpect(status().isOk())
                              .andExpect(jsonPath("$.accessToken").isString())
                              .andExpect(jsonPath("$.tokenType").value("Bearer"))
                              .andExpect(jsonPath("$.expiresIn").value(86400))
                              .andExpect(jsonPath("$.memberId").value(member.getId()))
                              .andReturn();

    String response = result.getResponse().getContentAsString();
    String token = response.substring(
            response.indexOf("\"accessToken\":\"") + 15,
            response.indexOf("\",\"tokenType\"")
    );
    String[] tokenParts = token.split("\\.");
    String payload = new String(
            Base64.getUrlDecoder().decode(tokenParts[1]),
            StandardCharsets.UTF_8
    );
    assertThat(payload).matches("\\{\"userId\":" + member.getId() + ",\"exp\":\\d+}");
    long expiresAt = Long.parseLong(
            payload.substring(payload.indexOf("\"exp\":") + 6, payload.length() - 1)
    );
    assertThat(expiresAt).isBetween(
            issuedAfter + 86_400,
            Instant.now().getEpochSecond() + 86_400
    );
  }

  @DisplayName("비밀번호가 일치하지 않으면 로그인을 거절한다.")
  @Test
  void loginRejectsInvalidCredentials() throws Exception {
    signup();

    mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("wrong-password")))
           .andExpect(status().isUnauthorized())
           .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  @DisplayName("정지된 회원이 로그인하면 비활성 계정 오류를 반환한다.")
  @Test
  void loginRejectsInactiveAccount() throws Exception {
    Member member = signup();
    jdbcTemplate.update("update members set status = 'SUSPENDED' where id = ?", member.getId());

    mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("WashLife!123")))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
  }

  @DisplayName("발급한 토큰으로 인증하면 요청에서 회원 식별자를 확인한다.")
  @Test
  void bearerTokenProvidesMemberIdToAuthenticatedRequests() throws Exception {
    signup();
    MvcResult loginResult = mockMvc.perform(post("/auth/login")
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .content(loginJson("WashLife!123")))
                                   .andReturn();
    String response = loginResult.getResponse().getContentAsString();
    String token = response.substring(
            response.indexOf("\"accessToken\":\"") + 15,
            response.indexOf("\",\"tokenType\"")
    );

    mockMvc.perform(get("/test/members/me")
                            .header("Authorization", "Bearer " + token))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.memberId", greaterThan(0)));
  }

  private Member signup() throws Exception {
    mockMvc.perform(post("/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(SIGNUP_REQUEST))
           .andExpect(status().isCreated());
    return memberRepository.findByEmail("user@example.com").orElseThrow();
  }

  private String loginJson(String password) {
    return """
            {
              "email": "user@example.com",
              "password": "%s"
            }
            """.formatted(password);
  }

  @RestController
  static class CurrentMemberController {

    @GetMapping("/test/members/me")
    Map<String, Long> currentMember(Authentication authentication) {
      return Map.of("memberId", (Long) authentication.getPrincipal());
    }
  }
}
