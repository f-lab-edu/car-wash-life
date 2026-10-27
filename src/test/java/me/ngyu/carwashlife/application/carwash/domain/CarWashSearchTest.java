package me.ngyu.carwashlife.application.carwash.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CarWashSearchTest {

  @Test
  @DisplayName("검색어 앞뒤 공백은 제거하고 내부 공백은 보존한다.")
  void stripsSurroundingWhitespace() {
    assertThat(new CarWashSearch(" \t세차 생활\n", null).keyword()).isEqualTo("세차 생활");
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t\n", "\u2003"})
  @DisplayName("검색어가 없거나 공백뿐이면 거절한다.")
  void rejectsMissingOrBlankKeyword(String keyword) {
    assertThatThrownBy(() -> new CarWashSearch(keyword, null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("앞뒤 공백을 제외한 검색어가 100자이면 허용한다.")
  void acceptsMaximumLength() {
    String keyword = "가".repeat(100);
    assertThat(new CarWashSearch(" " + keyword + " ", null).keyword()).isEqualTo(keyword);
  }

  @Test
  @DisplayName("앞뒤 공백을 제외한 검색어가 100자를 넘으면 거절한다.")
  void rejectsExcessiveLength() {
    assertThatThrownBy(() -> new CarWashSearch("가".repeat(101), null)).isInstanceOf(IllegalArgumentException.class);
  }
}
