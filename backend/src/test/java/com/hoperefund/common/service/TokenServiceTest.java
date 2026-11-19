package com.hoperefund.common.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hoperefund.common.service.TokenService.IssuedToken;
import com.hoperefund.common.service.TokenService.TokenInfo;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 로그인 토큰 Service 테스트
 * 발급 → 검사(정보 꺼내기) · 위조 · 만료 · 로그아웃 · 비밀키 검사를 확인한다.
 */
class TokenServiceTest {

    private static final String SECRET = "test-jwt-secret-must-be-at-least-32-bytes";
    private static final Duration VALIDITY = Duration.ofHours(2);

    private final AtomicReference<Instant> now =
            new AtomicReference<>(Instant.parse("2026-11-16T00:00:00Z"));
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new TokenService(SECRET, VALIDITY, clockOf(now));
    }

    // 발급한 토큰을 검사하면 회원번호 · 아이디 · 역할 · 만료 시각을 그대로 꺼낸다
    @Test
    void issuedTokenCanBeVerified() {

        IssuedToken issued = tokenService.issue(3L, "hope_user", "USER");

        assertThat(issued.expiresAt()).isEqualTo(now.get().plus(VALIDITY));
        TokenInfo info = tokenService.verify(issued.token()).orElseThrow();
        assertThat(info.memberId()).isEqualTo(3L);
        assertThat(info.loginId()).isEqualTo("hope_user");
        assertThat(info.role()).isEqualTo("USER");
        assertThat(info.tokenId()).isNotBlank();
        assertThat(info.expiresAt()).isEqualTo(issued.expiresAt());
    }

    // 로그인할 때마다 다른 토큰 번호 (로그아웃이 다른 기기 토큰까지 끊지 않게)
    @Test
    void eachTokenHasDifferentId() {

        String first =
                tokenService
                        .verify(tokenService.issue(3L, "hope_user", "USER").token())
                        .orElseThrow()
                        .tokenId();
        String second =
                tokenService
                        .verify(tokenService.issue(3L, "hope_user", "USER").token())
                        .orElseThrow()
                        .tokenId();

        assertThat(first).isNotEqualTo(second);
    }

    // 만료 시각이 지나면 통과하지 않는다
    @Test
    void expiredTokenIsRejected() {

        String token = tokenService.issue(3L, "hope_user", "USER").token();

        now.set(now.get().plus(VALIDITY).plusSeconds(1));

        assertThat(tokenService.verify(token)).isEmpty();
    }

    // 다른 비밀키로 만든 토큰(위조) · 내용을 고친 토큰 · 형식이 틀린 값은 통과하지 않는다
    @Test
    void forgedOrBrokenTokenIsRejected() {

        TokenService otherServer =
                new TokenService("another-secret-value-also-32-bytes-long", VALIDITY, clockOf(now));
        String forged = otherServer.issue(1L, "admin", "ADMIN").token();
        String valid = tokenService.issue(3L, "hope_user", "USER").token();
        String tampered = valid.substring(0, valid.length() - 2) + "xx";

        assertThat(tokenService.verify(forged)).isEmpty();
        assertThat(tokenService.verify(tampered)).isEmpty();
        assertThat(tokenService.verify("not-a-token")).isEmpty();
        assertThat(tokenService.verify("")).isEmpty();
    }

    // 로그아웃한 토큰은 만료 전이라도 통과하지 않고, 같은 회원의 다른 토큰은 그대로 통과한다
    @Test
    void loggedOutTokenIsRejected() {

        String loggedOut = tokenService.issue(3L, "hope_user", "USER").token();
        String other = tokenService.issue(3L, "hope_user", "USER").token();

        tokenService.logout(tokenService.verify(loggedOut).orElseThrow());

        assertThat(tokenService.verify(loggedOut)).isEmpty();
        assertThat(tokenService.verify(other)).isPresent();
    }

    // 비밀키가 없거나 32바이트보다 짧으면 서버가 켜지지 않는다
    @Test
    void shortOrMissingSecretFailsToStart() {

        assertThatThrownBy(() -> new TokenService("", VALIDITY, clockOf(now)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new TokenService(null, VALIDITY, clockOf(now)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new TokenService("short-secret", VALIDITY, clockOf(now)))
                .isInstanceOf(IllegalStateException.class);
    }

    // 테스트 중 시각을 바꿀 수 있는 Clock
    private static Clock clockOf(AtomicReference<Instant> now) {
        return new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
    }
}
