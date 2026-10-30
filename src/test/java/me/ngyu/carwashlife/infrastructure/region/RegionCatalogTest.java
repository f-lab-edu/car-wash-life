package me.ngyu.carwashlife.infrastructure.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import me.ngyu.carwashlife.application.region.domain.Region;
import me.ngyu.carwashlife.application.region.domain.RegionLevel;
import me.ngyu.carwashlife.common.exception.ApplicationException;
import me.ngyu.carwashlife.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

class RegionCatalogTest {

  @TempDir
  Path temporaryDirectory;

  @Test
  @DisplayName("스냅샷은 기준 버전의 전국 시도·시군구·행정 읍면동을 중복 없이 제공한다.")
  void loadsAllSnapshotRegions() {
    List<Region> regions = new RegionCatalog(new DefaultResourceLoader()).findAll();

    assertThat(regions).hasSize(3843);
    assertThat(regions.stream().map(Region::regionId)).doesNotHaveDuplicates();
    assertThat(regions.stream().filter(region -> region.level() == RegionLevel.PROVINCE)).hasSize(16);
    assertThat(regions.stream().filter(region -> region.level() == RegionLevel.CITY_COUNTY_DISTRICT)).hasSize(268);
    assertThat(regions.stream().filter(region -> region.level() == RegionLevel.ADMINISTRATIVE_DISTRICT)).hasSize(3559);
    assertThat(regions.stream().filter(region -> region.fullName().equals("세종특별자치시"))).hasSize(1);
    assertThat(regions.stream().map(Region::fullName)).doesNotContain("세종특별자치시 세종시");
  }

  @Test
  @DisplayName("일반구가 있는 수원시 전체 범위는 하위 행정동의 모든 범위를 합친다.")
  void wholeCityBoundsIncludeEveryGeneralDistrict() {
    List<Region> regions = new RegionCatalog(new DefaultResourceLoader()).findAll();
    Region city = regions.stream().filter(region -> region.fullName().equals("경기도 수원시")).findFirst().orElseThrow();
    List<Region> towns = regions.stream().filter(region -> region.level() == RegionLevel.ADMINISTRATIVE_DISTRICT)
                               .filter(region -> region.parentName().startsWith("경기도 수원시 ")).toList();

    assertThat(city.bounds().southwest().getLatitude()).isEqualTo(towns.stream().mapToDouble(region -> region.bounds().southwest().getLatitude()).min().orElseThrow());
    assertThat(city.bounds().southwest().getLongitude()).isEqualTo(towns.stream().mapToDouble(region -> region.bounds().southwest().getLongitude()).min().orElseThrow());
    assertThat(city.bounds().northeast().getLatitude()).isEqualTo(towns.stream().mapToDouble(region -> region.bounds().northeast().getLatitude()).max().orElseThrow());
    assertThat(city.bounds().northeast().getLongitude()).isEqualTo(towns.stream().mapToDouble(region -> region.bounds().northeast().getLongitude()).max().orElseThrow());
    assertThat(regions.stream().map(Region::fullName)).contains("경기도 수원시 장안구", "경기도 수원시 권선구", "경기도 수원시 팔달구", "경기도 수원시 영통구");
  }

  @Test
  @DisplayName("스냅샷을 읽지 못하면 빈 목록 대신 원인을 보존한 장애를 반환하고 파일 복구 후 재시도한다.")
  void retriesAfterMissingSnapshotIsRestored() throws Exception {
    Path snapshot = temporaryDirectory.resolve("regions.tsv");
    RegionCatalog catalog = new RegionCatalog(new DefaultResourceLoader() {
      @Override
      public Resource getResource(String location) {
        return new FileSystemResource(snapshot);
      }
    });

    assertThatThrownBy(catalog::findAll).isInstanceOfSatisfying(ApplicationException.class, exception -> {
      assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REGION_SEARCH_UNAVAILABLE);
      assertThat(exception.getCause()).isNotNull();
    });
    try (var source = new DefaultResourceLoader().getResource("classpath:regions/administrative-regions-20261001.tsv").getInputStream()) {
      Files.copy(source, snapshot);
    }

    assertThat(catalog.findAll()).hasSize(3843);
    Files.delete(snapshot);
    assertThat(catalog.findAll()).hasSize(3843);
  }

  @Test
  @DisplayName("손상되거나 비어 있는 스냅샷도 정상적인 검색 결과 없음으로 처리하지 않는다.")
  void rejectsMalformedSnapshot() throws Exception {
    Path snapshot = temporaryDirectory.resolve("broken.tsv");
    Files.writeString(snapshot, "broken header\n");
    RegionCatalog catalog = new RegionCatalog(new DefaultResourceLoader() {
      @Override
      public Resource getResource(String location) {
        return new FileSystemResource(snapshot);
      }
    });

    assertThatThrownBy(catalog::findAll).isInstanceOfSatisfying(ApplicationException.class,
                                                            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REGION_SEARCH_UNAVAILABLE));
  }

  @Test
  @DisplayName("유효한 행 중간에서 잘린 스냅샷도 부분 결과를 캐시하지 않고 장애로 처리한다.")
  void rejectsTruncatedSnapshot() throws Exception {
    Path snapshot = temporaryDirectory.resolve("truncated.tsv");
    try (var source = new DefaultResourceLoader().getResource("classpath:regions/administrative-regions-20261001.tsv").getInputStream()) {
      String complete = new String(source.readAllBytes(), StandardCharsets.UTF_8);
      Files.writeString(snapshot, complete.lines().limit(2).collect(Collectors.joining("\n")) + "\n");
    }
    RegionCatalog catalog = new RegionCatalog(new DefaultResourceLoader() {
      @Override
      public Resource getResource(String location) {
        return new FileSystemResource(snapshot);
      }
    });

    assertThatThrownBy(catalog::findAll).isInstanceOfSatisfying(ApplicationException.class,
                                                            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REGION_SEARCH_UNAVAILABLE));
  }
}
