package me.ngyu.carwashlife.application.region.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ngyu.carwashlife.application.region.api.dto.RegionSearchDto;
import me.ngyu.carwashlife.application.region.service.RegionSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/regions")
@RequiredArgsConstructor
public class RegionController {

  private final RegionSearchService regionSearchService;

  @GetMapping("/search")
  public RegionSearchDto.Response search(@Valid @ModelAttribute RegionSearchDto.Request request) {
    return regionSearchService.search(request.toDomain());
  }
}
