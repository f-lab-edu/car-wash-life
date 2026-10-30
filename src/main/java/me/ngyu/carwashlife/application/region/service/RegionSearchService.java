package me.ngyu.carwashlife.application.region.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import me.ngyu.carwashlife.application.region.api.dto.RegionSearchDto;
import me.ngyu.carwashlife.application.region.domain.Region;
import me.ngyu.carwashlife.application.region.domain.RegionSearch;
import me.ngyu.carwashlife.infrastructure.region.RegionCatalog;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegionSearchService {

  private final RegionCatalog regionCatalog;

  public RegionSearchDto.Response search(RegionSearch search) {
    long offset = (long) search.page() * search.size();
    List<Region> matches = regionCatalog.findAll().stream()
                                      .filter(region -> region.matches(search.keyword()))
                                      .skip(offset).limit(search.size() + 1L).toList();
    List<RegionSearchDto.Summary> summaries = matches.stream().limit(search.size()).map(RegionSearchDto.Summary::from).toList();
    return new RegionSearchDto.Response(summaries, matches.size() > search.size());
  }
}
