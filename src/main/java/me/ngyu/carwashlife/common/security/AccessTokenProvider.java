package me.ngyu.carwashlife.common.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AccessTokenProvider {

  public static final long EXPIRES_IN_SECONDS = 24 * 60 * 60;

  private static final String HMAC_SHA_256 = "HmacSHA256";
  private static final String HEADER = encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
  private static final Pattern PAYLOAD_PATTERN = Pattern.compile(
      "\\{\"userId\":([1-9]\\d*),\"exp\":(\\d+)\\}"
  );

  private final byte[] secret;

  public AccessTokenProvider(@Value("${auth.jwt.secret}") String secret) {
    if (secret == null || secret.length() < 32) {
      throw new IllegalArgumentException("JWT secret must be at least 32 characters.");
    }
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  public String create(Long memberId) {
    long expiresAt = Instant.now().getEpochSecond() + EXPIRES_IN_SECONDS;
    String payload = encode("{\"userId\":" + memberId + ",\"exp\":" + expiresAt + "}");
    String unsignedToken = HEADER + "." + payload;
    return unsignedToken + "." + sign(unsignedToken);
  }

  public Long getMemberId(String token) {
    String[] parts = token.split("\\.", -1);
    if (parts.length != 3 || !HEADER.equals(parts[0])) {
      throw new InvalidAccessTokenException();
    }

    String unsignedToken = parts[0] + "." + parts[1];
    byte[] expectedSignature = decode(parts[2]);
    byte[] actualSignature = decode(sign(unsignedToken));
    if (!MessageDigest.isEqual(actualSignature, expectedSignature)) {
      throw new InvalidAccessTokenException();
    }

    String payload = new String(decode(parts[1]), StandardCharsets.UTF_8);
    Matcher matcher = PAYLOAD_PATTERN.matcher(payload);
    if (!matcher.matches()) {
      throw new InvalidAccessTokenException();
    }

    try {
      long memberId = Long.parseLong(matcher.group(1));
      long expiresAt = Long.parseLong(matcher.group(2));
      if (expiresAt <= Instant.now().getEpochSecond()) {
        throw new InvalidAccessTokenException();
      }
      return memberId;
    } catch (NumberFormatException exception) {
      throw new InvalidAccessTokenException();
    }
  }

  private String sign(String value) {
    try {
      Mac mac = Mac.getInstance(HMAC_SHA_256);
      mac.init(new SecretKeySpec(secret, HMAC_SHA_256));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(
          mac.doFinal(value.getBytes(StandardCharsets.UTF_8))
      );
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Could not sign access token.", exception);
    }
  }

  private static String encode(String value) {
    return Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] decode(String value) {
    try {
      return Base64.getUrlDecoder().decode(value);
    } catch (IllegalArgumentException exception) {
      throw new InvalidAccessTokenException();
    }
  }
}
