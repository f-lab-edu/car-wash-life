package me.ngyu.carwashlife.application.region.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import me.ngyu.carwashlife.application.region.domain.Region;
import me.ngyu.carwashlife.application.region.domain.RegionLevel;
import me.ngyu.carwashlife.application.region.domain.RegionSearch;

public class RegionSearchDto {

  private RegionSearchDto() {
  }

  public record Request(@NotBlank @Size(max = 100) @Pattern(regexp = "[^\\x00]*") String keyword,
                        @Min(0) Integer page,
                        @Min(1) @Max(RegionSearch.MAX_PAGE_SIZE) Integer size) {

    public Request {
      keyword = normalizeKeyword(keyword);
      page = page == null ? 0 : page;
      size = size == null ? RegionSearch.DEFAULT_PAGE_SIZE : size;
    }

    public RegionSearch toDomain() {
      return new RegionSearch(keyword, page, size);
    }

    private static String normalizeKeyword(String keyword) {
      if (keyword == null) {
        return null;
      }
      int start = 0;
      int end = keyword.length();
      while (start < end && isSearchWhitespace(keyword.codePointAt(start))) {
        start += Character.charCount(keyword.codePointAt(start));
      }
      while (start < end && isSearchWhitespace(keyword.codePointBefore(end))) {
        end -= Character.charCount(keyword.codePointBefore(end));
      }
      return keyword.substring(start, end);
    }

    private static boolean isSearchWhitespace(int codePoint) {
      // Unicode White_Space: 단어 분리의 (?U)\\s와 같은 범위다.
      return Character.isSpaceChar(codePoint) || codePoint >= '\t' && codePoint <= '\r' || codePoint == 0x85;
    }
  }

  public record Response(List<Summary> regions, boolean hasMore) {

    public Response {
      regions = List.copyOf(regions);
    }
  }

  public record Summary(String regionId, RegionLevel level, String name, String parentName, String fullName, Point center, Bounds bounds) {

    public static Summary from(Region region) {
      return new Summary(region.regionId(), region.level(), region.name(), region.parentName(), region.fullName(),
                         new Point(region.center().getLatitude(), region.center().getLongitude()),
                         new Bounds(region.bounds().southwest().getLatitude(), region.bounds().southwest().getLongitude(),
                                    region.bounds().northeast().getLatitude(), region.bounds().northeast().getLongitude()));
    }
  }

  public record Point(double latitude, double longitude) {

  }

  public record Bounds(double southwestLatitude, double southwestLongitude, double northeastLatitude, double northeastLongitude) {

  }
}
