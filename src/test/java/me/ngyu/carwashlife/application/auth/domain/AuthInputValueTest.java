package me.ngyu.carwashlife.application.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthInputValueTest {

  @Test
  @DisplayName("로그인 정보의 문자열 표현은 비밀번호를 노출하지 않는다.")
  void loginCredentialsDoNotExposeThePasswordInTheirStringRepresentation() {
    LoginCredentials credentials = new LoginCredentials("member@example.com", "raw-password");

    assertThat(credentials.toString()).doesNotContain("raw-password");
  }

  @Test
  @DisplayName("회원가입 정보의 문자열 표현은 비밀번호를 노출하지 않는다.")
  void signupInformationDoesNotExposeThePasswordInItsStringRepresentation() {
    SignupInformation information = new SignupInformation("member@example.com", "raw-password", null, null, null);

    assertThat(information.toString()).doesNotContain("raw-password");
  }
}
