package me.ngyu.carwashlife.application.region;

import static org.assertj.core.api.Assertions.assertThat;

import me.ngyu.carwashlife.application.region.api.dto.RegionSearchDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegionSearchRequestTest {

  @Test
  @DisplayName("지역 검색어의 유니코드 양끝 공백을 제거하고 내부 공백은 보존한다.")
  void normalizesOnlySurroundingWhitespace() {
    String keyword = "서울" + " ".repeat(100_000) + "강남구";

    RegionSearchDto.Request request = new RegionSearchDto.Request("\u00a0" + keyword + "\u202f", null, null);

    assertThat(request.keyword()).isEqualTo(keyword);
  }
}
