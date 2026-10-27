package me.ngyu.carwashlife.application.auth.service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import me.ngyu.carwashlife.application.auth.api.dto.LoginDto;
import me.ngyu.carwashlife.application.auth.api.dto.SignupDto;
import me.ngyu.carwashlife.application.auth.domain.LoginCredentials;
import me.ngyu.carwashlife.application.auth.domain.SignupInformation;
import me.ngyu.carwashlife.application.member.domain.Member;
import me.ngyu.carwashlife.common.exception.ApplicationException;
import me.ngyu.carwashlife.common.exception.ErrorCode;
import me.ngyu.carwashlife.common.security.AccessTokenProvider;
import me.ngyu.carwashlife.infrastructure.persistence.MemberRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");

  private final MemberRepository memberRepository;
  private final PasswordEncoder passwordEncoder;
  private final AccessTokenProvider accessTokenProvider;

  /**
   * 중복 이메일을 확인하고 비밀번호를 암호화한 회원을 등록한다.
   */
  @Transactional
  public SignupDto.Response signup(SignupInformation information) {
    String email = normalizeEmail(information.email());
    if (memberRepository.existsByEmail(email)) {
      throw new ApplicationException(ErrorCode.DUPLICATE_EMAIL);
    }

    Member member = Member.create(
            email,
            passwordEncoder.encode(information.password()),
            information.residenceRegionCode(),
            information.vehicleType(),
            information.washExperience(),
            OffsetDateTime.now(SERVICE_ZONE_ID)
    );

    try {
      Member savedMember = memberRepository.saveAndFlush(member);
      return new SignupDto.Response(
              savedMember.getId(),
              savedMember.getEmail(),
              savedMember.getCreatedAt()
      );
    } catch (DataIntegrityViolationException exception) {
      throw new ApplicationException(ErrorCode.DUPLICATE_EMAIL);
    }
  }

  /**
   * 로그인 정보와 회원 상태를 확인하고 액세스 토큰을 발급한다.
   */
  @Transactional(readOnly = true)
  public LoginDto.Response login(LoginCredentials credentials) {
    Member member = memberRepository.findByEmail(normalizeEmail(credentials.email()))
                                    .orElseThrow(() -> new ApplicationException(ErrorCode.INVALID_CREDENTIALS));

    if (!passwordEncoder.matches(credentials.password(), member.getPassword())) {
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
