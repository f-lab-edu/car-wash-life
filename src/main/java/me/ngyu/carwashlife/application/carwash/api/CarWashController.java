package me.ngyu.carwashlife.application.carwash.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ngyu.carwashlife.application.carwash.api.dto.CarWashDetailDto;
import me.ngyu.carwashlife.application.carwash.api.dto.CarWashExplorationDto;
import me.ngyu.carwashlife.application.carwash.api.dto.ModifyCarWashDto;
import me.ngyu.carwashlife.application.carwash.api.dto.RegisterCarWashDto;
import me.ngyu.carwashlife.application.carwash.service.CarWashService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/car-washes")
@RequiredArgsConstructor
public class CarWashController {

  private final CarWashService carWashService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RegisterCarWashDto.Response register(@AuthenticationPrincipal Long memberId,
                                              @Valid @RequestBody RegisterCarWashDto.Request request) {
    return carWashService.register(memberId, request);
  }

  @PostMapping("/{carWashId}/histories")
  @ResponseStatus(HttpStatus.CREATED)
  public ModifyCarWashDto.Response modify(@PathVariable Long carWashId,
                                          @AuthenticationPrincipal Long memberId,
                                          @Valid @RequestBody ModifyCarWashDto.Request request) {
    return carWashService.modify(memberId, carWashId, request);
  }

  @GetMapping
  public CarWashExplorationDto.Response findWithinBounds(@Valid @ModelAttribute CarWashExplorationDto.Request request) {
    return carWashService.findWithinBounds(request.toDomain(), request.toCurrentCoordinates());
  }

  @GetMapping("/{carWashId}")
  public CarWashDetailDto.Response findDetail(@PathVariable Long carWashId) {
    return carWashService.findDetail(carWashId);
  }
}
