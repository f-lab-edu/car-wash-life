package me.ngyu.carwashlife.application.carwash.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import me.ngyu.carwashlife.application.carwash.api.dto.ModifyCarWashDto;
import me.ngyu.carwashlife.application.carwash.api.dto.RegisterCarWashDto;
import me.ngyu.carwashlife.application.carwash.service.CarWashModificationService;
import me.ngyu.carwashlife.application.carwash.service.CarWashRegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

  private final CarWashRegistrationService registrationService;
  private final CarWashModificationService modificationService;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RegisterCarWashDto.Response register(@AuthenticationPrincipal Long memberId,
                                              @Valid @RequestBody RegisterCarWashDto.Request request) {
    return registrationService.register(memberId, request);
  }

  @PostMapping("/{carWashId}/histories")
  @ResponseStatus(HttpStatus.CREATED)
  public ModifyCarWashDto.Response modify(@PathVariable Long carWashId,
                                          @AuthenticationPrincipal Long memberId,
                                          @Valid @RequestBody ModifyCarWashDto.Request request) {
    return modificationService.modify(memberId, carWashId, request);
  }
}
