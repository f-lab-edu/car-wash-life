package me.ngyu.carwashlife.application.auth.domain;

public record LoginCredentials(String email, String password) {

  /**
   * 문자열 표현에 비밀번호를 노출하지 않는다.
   */
  @Override
  public String toString() {
    return "LoginCredentials[password=***]";
  }
}
