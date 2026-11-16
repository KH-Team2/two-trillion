package com.hoperefund.common.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 로그인 토큰(JWT) Service (F-01-02 · decisions/0004)
 * 발급(로그인) · 검사(JWT 필터) · 무효 처리(로그아웃)를 한 곳에서 맡는다.
 *
 * <p>토큰은 DB에 저장하지 않는다: 서버는 비밀키(JWT_SECRET)로 서명만 하고, 다음 요청에서 서명이 맞는지만 확인한다.
 * 토큰 안에는 회원번호 · 아이디 · 역할 · 토큰 번호(jti) · 만료 시각이 들어 있다 (비밀번호 등 민감정보는 넣지 않음).
 *
 * <p>로그아웃 목록: ERD에 저장 테이블이 없어 서버 메모리에 토큰 번호를 만료 시각까지 보관한다.
 * 서버를 다시 켜면 목록이 사라져, 로그아웃한 토큰이 남은 유효 시간 동안 다시 통할 수 있다 (화면은 이미 토큰을 지움).
 */
@Service
public class TokenService {

    public static final String TOKEN_TYPE = "Bearer";

    private static final int MIN_SECRET_BYTES = 32; // HS256 최소 키 길이 (256비트)
    private static final String CLAIM_LOGIN_ID = "loginId";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final Duration validity;
    private final Clock clock;

    // 로그아웃한 토큰 번호(jti) → 토큰 만료 시각 (만료가 지나면 목록에서 지움)
    private final Map<String, Instant> loggedOut = new ConcurrentHashMap<>();

    @Autowired
    public TokenService(
            @Value("${jwt.secret:}") String secret,
            @Value("${jwt.expiration:2h}") Duration validity) {
        this(secret, validity, Clock.systemUTC());
    }

    // 테스트에서 시각을 바꿔 만료를 확인할 수 있도록 Clock을 받는 생성자
    TokenService(String secret, Duration validity, Clock clock) {
        byte[] secretBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            // 비밀키 없이 켜지면 아무나 토큰을 만들 수 있으므로 서버를 켜지 않는다 (값은 메시지에 넣지 않음)
            throw new IllegalStateException(
                    "JWT_SECRET 환경변수를 " + MIN_SECRET_BYTES + "바이트 이상으로 설정해 주세요 (.env.example 참고)");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.validity = validity;
        this.clock = clock;
    }

    /** 로그인 성공 시 토큰 발급 */
    public IssuedToken issue(long memberId, String loginId, String role) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(validity);

        String token =
                Jwts.builder()
                        .subject(String.valueOf(memberId))
                        .claim(CLAIM_LOGIN_ID, loginId)
                        .claim(CLAIM_ROLE, role)
                        .id(UUID.randomUUID().toString())
                        .issuedAt(Date.from(now))
                        .expiration(Date.from(expiresAt))
                        .signWith(key)
                        .compact();
        return new IssuedToken(token, expiresAt);
    }

    /** 토큰 검사: 서명 · 만료 · 로그아웃 여부를 확인하고, 통과하면 로그인 정보를 돌려준다 */
    public Optional<TokenInfo> verify(String token) {
        try {
            Claims claims =
                    Jwts.parser()
                            .verifyWith(key)
                            .clock(() -> Date.from(clock.instant()))
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();

            String tokenId = claims.getId();
            if (tokenId == null || loggedOut.containsKey(tokenId)) {
                return Optional.empty();
            }
            return Optional.of(
                    new TokenInfo(
                            Long.parseLong(claims.getSubject()),
                            claims.get(CLAIM_LOGIN_ID, String.class),
                            claims.get(CLAIM_ROLE, String.class),
                            tokenId,
                            claims.getExpiration().toInstant()));
        } catch (JwtException | IllegalArgumentException e) {
            // 위조 · 만료 · 형식 오류 → 로그인 안 한 것으로 처리 (토큰 값은 로그에 남기지 않음)
            return Optional.empty();
        }
    }

    /** 로그아웃: 그 토큰을 만료 시각까지 무효 목록에 둔다 */
    public void logout(TokenInfo tokenInfo) {
        removeExpired();
        loggedOut.put(tokenInfo.tokenId(), tokenInfo.expiresAt());
    }

    // 이미 만료된 토큰은 서명 검사에서 걸러지므로 목록에서 지운다 (메모리가 계속 늘지 않게)
    private void removeExpired() {
        Instant now = clock.instant();
        loggedOut.values().removeIf(expiresAt -> !expiresAt.isAfter(now));
    }

    /** 발급한 토큰과 만료 시각 */
    public record IssuedToken(String token, Instant expiresAt) {}

    /**
     * 토큰에서 꺼낸 로그인 정보. JWT 필터가 Spring Security 로그인 정보의 details에 넣는다.
     * Controller는 회원번호만 필요하면 {@code @AuthenticationPrincipal Long memberId}로 받는다.
     */
    public record TokenInfo(
            long memberId, String loginId, String role, String tokenId, Instant expiresAt) {}
}
