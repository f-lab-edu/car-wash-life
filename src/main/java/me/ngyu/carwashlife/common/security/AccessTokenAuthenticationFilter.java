package me.ngyu.carwashlife.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AccessTokenAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final AccessTokenProvider accessTokenProvider;

  public AccessTokenAuthenticationFilter(AccessTokenProvider accessTokenProvider) {
    this.accessTokenProvider = accessTokenProvider;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    String authorization = request.getHeader("Authorization");
    if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
      authenticate(authorization.substring(BEARER_PREFIX.length()));
    }
    filterChain.doFilter(request, response);
  }

  private void authenticate(String token) {
    try {
      Long memberId = accessTokenProvider.getMemberId(token);
      UsernamePasswordAuthenticationToken authentication =
          UsernamePasswordAuthenticationToken.authenticated(memberId, null, List.of());
      SecurityContextHolder.getContext().setAuthentication(authentication);
    } catch (InvalidAccessTokenException exception) {
      SecurityContextHolder.clearContext();
    }
  }
}
