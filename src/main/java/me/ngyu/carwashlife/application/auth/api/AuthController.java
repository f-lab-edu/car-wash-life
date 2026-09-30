package me.ngyu.carwashlife.application.auth.api;

import jakarta.validation.Valid;
import me.ngyu.carwashlife.application.auth.api.dto.LoginDto;
import me.ngyu.carwashlife.application.auth.api.dto.SignupDto;
import me.ngyu.carwashlife.application.auth.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/signup")
  @ResponseStatus(HttpStatus.CREATED)
  public SignupDto.Response signup(@Valid @RequestBody SignupDto.Request request) {
    return authService.signup(request);
  }

  @PostMapping("/login")
  public LoginDto.Response login(@Valid @RequestBody LoginDto.Request request) {
    return authService.login(request);
  }
}
