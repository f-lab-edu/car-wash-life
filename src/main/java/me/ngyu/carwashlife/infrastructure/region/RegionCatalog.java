package me.ngyu.carwashlife.infrastructure.region;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import me.ngyu.carwashlife.application.carwash.domain.Coordinates;
import me.ngyu.carwashlife.application.carwash.domain.MapBounds;
import me.ngyu.carwashlife.application.region.domain.Region;
import me.ngyu.carwashlife.application.region.domain.RegionLevel;
import me.ngyu.carwashlife.common.exception.ApplicationException;
import me.ngyu.carwashlife.common.exception.ErrorCode;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegionCatalog {

  private static final String SNAPSHOT = "classpath:regions/administrative-regions-20261001.tsv";

  private static final int EXPECTED_REGION_COUNT = 3843;

  private static final String HEADER = "regionId\tlevel\tname\tparentName\tsouthwestLatitude\tsouthwestLongitude\tnortheastLatitude\tnortheastLongitude";

  private final ResourceLoader resourceLoader;

  private List<Region> regions;

  /**
   * 정상 스냅샷만 캐시한다. 로드 실패는 다음 요청에서 다시 시도하며 원인은 5xx 예외 처리기가 기록한다.
   */
  public synchronized List<Region> findAll() {
    if (regions == null) {
      regions = load();
    }
    return regions;
  }

  private List<Region> load() {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceLoader.getResource(SNAPSHOT).getInputStream(), StandardCharsets.UTF_8))) {
      if (!HEADER.equals(reader.readLine())) {
        throw new IOException("지역 스냅샷의 헤더가 올바르지 않습니다.");
      }
      List<Region> loaded = new ArrayList<>();
      Set<String> identifiers = new HashSet<>();
      String line;
      while ((line = reader.readLine()) != null) {
        String[] fields = line.split("\t", -1);
        if (fields.length != 8 || !identifiers.add(fields[0])) {
          throw new IOException("지역 스냅샷의 행 형식 또는 식별자가 올바르지 않습니다.");
        }
        Coordinates southwest = new Coordinates(Double.valueOf(fields[4]), Double.valueOf(fields[5]));
        Coordinates northeast = new Coordinates(Double.valueOf(fields[6]), Double.valueOf(fields[7]));
        loaded.add(new Region(fields[0], RegionLevel.valueOf(fields[1]), fields[2], fields[3], new MapBounds(southwest, northeast)));
      }
      if (loaded.size() != EXPECTED_REGION_COUNT) {
        throw new IOException("지역 스냅샷의 항목 수가 기준 버전과 일치하지 않습니다.");
      }
      loaded.sort(Comparator.comparing(Region::fullName).thenComparing(Region::regionId));
      return List.copyOf(loaded);
    } catch (IOException | IllegalArgumentException exception) {
      throw new ApplicationException(ErrorCode.REGION_SEARCH_UNAVAILABLE, exception);
    }
  }
}
