package me.ngyu.carwashlife.application.auth.service;

import java.time.OffsetDateTime;
import java.util.Locale;
import me.ngyu.carwashlife.application.auth.api.dto.LoginDto;
import me.ngyu.carwashlife.application.auth.api.dto.SignupDto;
import me.ngyu.carwashlife.common.exception.ApplicationException;
import me.ngyu.carwashlife.common.exception.ErrorCode;
import me.ngyu.carwashlife.common.security.AccessTokenProvider;
import me.ngyu.carwashlife.infrastructure.persistence.MemberRepository;
import me.ngyu.carwashlife.infrastructure.persistence.entity.MemberEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final MemberRepository memberRepository;
  private final PasswordEncoder passwordEncoder;
  private final AccessTokenProvider accessTokenProvider;

  public AuthService(
      MemberRepository memberRepository,
      PasswordEncoder passwordEncoder,
      AccessTokenProvider accessTokenProvider
  ) {
    this.memberRepository = memberRepository;
    this.passwordEncoder = passwordEncoder;
    this.accessTokenProvider = accessTokenProvider;
  }

  @Transactional
  public SignupDto.Response signup(SignupDto.Request request) {
    String email = normalizeEmail(request.email());
    if (memberRepository.existsByEmail(email)) {
      throw new ApplicationException(ErrorCode.DUPLICATE_EMAIL);
    }

    MemberEntity member = MemberEntity.create(
        email,
        passwordEncoder.encode(request.password()),
        request.residenceRegionCode(),
        request.vehicleType(),
        request.washExperience(),
        OffsetDateTime.now()
    );

    try {
      MemberEntity savedMember = memberRepository.saveAndFlush(member);
      return new SignupDto.Response(
          savedMember.getId(),
          savedMember.getEmail(),
          savedMember.getCreatedAt()
      );
    } catch (DataIntegrityViolationException exception) {
      throw new ApplicationException(ErrorCode.DUPLICATE_EMAIL);
    }
  }

  @Transactional(readOnly = true)
  public LoginDto.Response login(LoginDto.Request request) {
    MemberEntity member = memberRepository.findByEmail(normalizeEmail(request.email()))
        .orElseThrow(() -> new ApplicationException(ErrorCode.INVALID_CREDENTIALS));

    if (!passwordEncoder.matches(request.password(), member.getPassword())) {
      throw new ApplicationException(ErrorCode.INVALID_CREDENTIALS);
    }
    if (!member.isActive()) {
      throw new ApplicationException(ErrorCode.ACCOUNT_NOT_ACTIVE);
    }

    String accessToken = accessTokenProvider.create(member.getId());
    return new LoginDto.Response(
        accessToken,
        "Bearer",
        AccessTokenProvider.EXPIRES_IN_SECONDS,
        member.getId()
    );
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
