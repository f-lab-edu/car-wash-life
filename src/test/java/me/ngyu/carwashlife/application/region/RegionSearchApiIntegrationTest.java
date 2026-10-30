package me.ngyu.carwashlife.application.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import me.ngyu.carwashlife.application.carwash.domain.CarWash;
import me.ngyu.carwashlife.application.carwash.domain.CarWashHistory;
import me.ngyu.carwashlife.application.carwash.domain.CarWashInformation;
import me.ngyu.carwashlife.application.carwash.domain.Coordinates;
import me.ngyu.carwashlife.application.carwash.domain.VisitExperience;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashHistoryRepository;
import me.ngyu.carwashlife.infrastructure.persistence.CarWashRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.MethodArgumentNotValidException;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class RegionSearchApiIntegrationTest {

  private final MockMvc mockMvc;
  private final CarWashRepository carWashRepository;
  private final CarWashHistoryRepository historyRepository;

  @Test
  @DisplayName("비회원은 전국의 동명 지역을 검색하고 상위 지역명으로 구분한다.")
  void anonymousVisitorsCanDistinguishRegionsWithTheSameName() throws Exception {
    String body = mockMvc.perform(get("/regions/search").param("keyword", "중앙동").param("size", "100"))
                         .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

    List<String> names = JsonPath.read(body, "$.regions[*].fullName");
    assertThat(names).contains("서울특별시 관악구 중앙동", "부산광역시 중구 중앙동", "경기도 성남시 중원구 중앙동");
    List<String> parents = JsonPath.read(body, "$.regions[*].parentName");
    assertThat(parents).allSatisfy(parent -> assertThat(parent).isNotBlank());
  }

  @Test
  @DisplayName("검색어 앞뒤 공백을 제거하고 시 전체와 일반구를 구분해 반환한다.")
  void searchesCityAndReadableGeneralDistrictNames() throws Exception {
    String body = mockMvc.perform(get("/regions/search").param("keyword", "  경기도 수원시 \t").param("size", "100"))
                         .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

    List<String> names = JsonPath.read(body, "$.regions[*].fullName");
    assertThat(names).contains("경기도 수원시", "경기도 수원시 장안구", "경기도 수원시 영통구");
    assertThat(names).allSatisfy(name -> assertThat(name).startsWith("경기도 수원시"));
  }

  @Test
  @DisplayName("세종시 별칭으로 검색해도 중복 없는 세종특별자치시와 하위 지역을 반환한다.")
  void searchesSejongByItsShortName() throws Exception {
    String body = mockMvc.perform(get("/regions/search").param("keyword", "세종시").param("size", "100"))
                         .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

    List<String> names = JsonPath.read(body, "$.regions[*].fullName");
    assertThat(names).contains("세종특별자치시", "세종특별자치시 조치원읍");
    assertThat(names.stream().filter(name -> name.equals("세종특별자치시"))).hasSize(1);
    assertThat(names).doesNotContain("세종특별자치시 세종시");
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @CsvSource({"서울 강남구,서울특별시 강남구", "수원 장안구,경기도 수원시 장안구", "장안구 수원 수원,경기도 수원시 장안구"})
  @DisplayName("공백으로 구분한 검색어가 모두 포함되면 순서와 중복에 관계없이 지역을 찾는다.")
  void searchesAllWhitespaceSeparatedTerms(String keyword, String expected) throws Exception {
    String body = mockMvc.perform(get("/regions/search").param("keyword", keyword).param("size", "100"))
                         .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

    List<String> names = JsonPath.read(body, "$.regions[*].fullName");
    assertThat(names).contains(expected);
  }

  @Test
  @DisplayName("페이지 크기를 생략하면 20개를 반환하며 다음 페이지를 중복 없이 조회한다.")
  void pagesResultsInFixedNameOrder() throws Exception {
    String first = mockMvc.perform(get("/regions/search").param("keyword", "동"))
                          .andExpect(status().isOk()).andExpect(jsonPath("$.regions.length()").value(20))
                          .andExpect(jsonPath("$.hasMore").value(true)).andReturn().getResponse().getContentAsString();
    String second = mockMvc.perform(get("/regions/search").param("keyword", "동").param("page", "1"))
                           .andExpect(status().isOk()).andExpect(jsonPath("$.regions.length()").value(20))
                           .andReturn().getResponse().getContentAsString();
    List<String> firstIds = JsonPath.read(first, "$.regions[*].regionId");
    List<String> secondIds = JsonPath.read(second, "$.regions[*].regionId");

    assertThat(firstIds).doesNotContainAnyElementsOf(secondIds);
  }

  @Test
  @DisplayName("지역이 없거나 마지막 페이지를 넘으면 빈 목록과 추가 결과 없음 상태를 반환한다.")
  void missingRegionsAndPagesReturnEmptyResults() throws Exception {
    mockMvc.perform(get("/regions/search").param("keyword", "존재하지않는지역이름"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.regions").isEmpty()).andExpect(jsonPath("$.hasMore").value(false));
    mockMvc.perform(get("/regions/search").param("keyword", "동").param("page", "2147483647").param("size", "100"))
           .andExpect(status().isOk()).andExpect(jsonPath("$.regions").isEmpty()).andExpect(jsonPath("$.hasMore").value(false));
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @ValueSource(strings = {"", " ", "\t\n", "\u2003", "\u00a0", "\u202f", "\u0085", "\u0000"})
  @DisplayName("비어 있거나 NUL 문자를 포함한 지역 검색어는 요청 단계에서 거절한다.")
  void rejectsInvalidKeywords(String keyword) throws Exception {
    mockMvc.perform(get("/regions/search").param("keyword", keyword))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
           .andExpect(result -> assertThat(result.getResolvedException()).isInstanceOf(MethodArgumentNotValidException.class));
  }

  @Test
  @DisplayName("지역 검색어가 없거나 100자를 초과하면 검증 오류를 반환한다.")
  void rejectsMissingAndExcessiveKeywords() throws Exception {
    mockMvc.perform(get("/regions/search")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    mockMvc.perform(get("/regions/search").param("keyword", "가".repeat(101)))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @ParameterizedTest(name = "[{index}] {displayName}")
  @CsvSource({"page,-1", "page,text", "page,2147483648", "size,0", "size,101", "size,text"})
  @DisplayName("잘못된 지역 검색 페이지 번호와 크기는 요청 단계에서 거절한다.")
  void rejectsInvalidPagination(String parameter, String value) throws Exception {
    mockMvc.perform(get("/regions/search").param("keyword", "서울").param(parameter, value))
           .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("선택한 지역 범위로 지도 조회를 이어가며 세차장이 없는 경우와 있는 경우를 구분한다.")
  void selectedRegionBoundsContinueToMapExploration() throws Exception {
    String body = mockMvc.perform(get("/regions/search").param("keyword", "서울특별시 종로구 사직동"))
                         .andExpect(status().isOk()).andExpect(jsonPath("$.regions.length()").value(1))
                         .andReturn().getResponse().getContentAsString();
    Number south = JsonPath.read(body, "$.regions[0].bounds.southwestLatitude");
    Number west = JsonPath.read(body, "$.regions[0].bounds.southwestLongitude");
    Number north = JsonPath.read(body, "$.regions[0].bounds.northeastLatitude");
    Number east = JsonPath.read(body, "$.regions[0].bounds.northeastLongitude");
    Number latitude = JsonPath.read(body, "$.regions[0].center.latitude");
    Number longitude = JsonPath.read(body, "$.regions[0].center.longitude");
    assertThat(latitude.doubleValue()).isEqualTo((south.doubleValue() + north.doubleValue()) / 2);
    assertThat(longitude.doubleValue()).isEqualTo((west.doubleValue() + east.doubleValue()) / 2);
    MockHttpServletRequestBuilder bounds = get("/car-washes").param("southwestLatitude", south.toString()).param("southwestLongitude", west.toString())
                                                            .param("northeastLatitude", north.toString()).param("northeastLongitude", east.toString());
    mockMvc.perform(bounds).andExpect(status().isOk()).andExpect(jsonPath("$.carWashes").isEmpty());

    OffsetDateTime now = OffsetDateTime.parse("2026-10-30T12:00:00+09:00");
    Coordinates coordinates = new Coordinates(latitude.doubleValue(), longitude.doubleValue());
    CarWash carWash = carWashRepository.save(CarWash.create("선택 지역 세차장", coordinates, now));
    CarWashInformation information = CarWashInformation.builder().name("선택 지역 세차장").coordinates(coordinates).build();
    CarWashHistory history = historyRepository.save(CarWashHistory.register(carWash, 1L, VisitExperience.USED, now, now, information));
    carWash.selectRepresentativeHistory(history, now);

    mockMvc.perform(bounds).andExpect(status().isOk()).andExpect(jsonPath("$.carWashes.length()").value(1))
           .andExpect(jsonPath("$.carWashes[0].carWashId").value(carWash.getId()));
  }
}
