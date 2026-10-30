package me.ngyu.carwashlife.application.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import me.ngyu.carwashlife.common.exception.ApplicationException;
import me.ngyu.carwashlife.common.exception.ErrorCode;
import me.ngyu.carwashlife.infrastructure.region.RegionCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RegionSearchFailureIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private RegionCatalog regionCatalog;

  @Test
  @DisplayName("지역 데이터 로드 실패는 검색 결과 없음과 다른 503으로 응답하고 내부 원인은 노출하지 않는다.")
  void unavailableCatalogReturnsServiceUnavailable() throws Exception {
    when(regionCatalog.findAll()).thenThrow(new ApplicationException(ErrorCode.REGION_SEARCH_UNAVAILABLE, new IOException("internal-resource-path")));

    mockMvc.perform(get("/regions/search").param("keyword", "서울"))
           .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("REGION_SEARCH_UNAVAILABLE"))
           .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("internal-resource-path"));
  }
}
