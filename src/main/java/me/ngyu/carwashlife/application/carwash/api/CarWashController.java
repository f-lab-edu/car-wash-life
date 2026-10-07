package me.ngyu.carwashlife.application.carwash.api;

import jakarta.validation.Valid;
import me.ngyu.carwashlife.application.carwash.api.dto.RegisterCarWashDto;
import me.ngyu.carwashlife.application.carwash.service.CarWashRegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/car-washes")
public class CarWashController {

  private final CarWashRegistrationService registrationService;

  public CarWashController(CarWashRegistrationService registrationService) {
    this.registrationService = registrationService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RegisterCarWashDto.Response register(
          @AuthenticationPrincipal Long memberId,
          @Valid @RequestBody RegisterCarWashDto.Request request
  ) {
    return registrationService.register(memberId, request);
  }
}
