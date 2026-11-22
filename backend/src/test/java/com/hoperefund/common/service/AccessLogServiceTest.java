package com.hoperefund.common.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hoperefund.common.mapper.AccessLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 접근 기록 저장 Service 테스트 (F-09-01)
 * DB 없이 Mapper를 대신해 저장 값 · 주소 길이 자르기 · 저장 실패 시 예외를 밖으로 던지지 않는지 확인한다.
 */
class AccessLogServiceTest {

    private static final String IP = "127.0.0.1";

    private AccessLogMapper accessLogMapper;
    private AccessLogService accessLogService;

    @BeforeEach
    void setUp() {
        accessLogMapper = mock(AccessLogMapper.class);
        accessLogService = new AccessLogService(accessLogMapper);
    }

    // 받은 값을 그대로 저장 · 로그인 전이면 회원번호 null
    @Test
    void recordSavesGivenValues() {

        accessLogService.record(3L, "/api/calculations/2026", IP, AccessLogService.SUCCESS);
        accessLogService.record(null, "/api/auth/login", IP, AccessLogService.FAIL);

        verify(accessLogMapper).insertAccessLog(3L, "/api/calculations/2026", IP, "SUCCESS");
        verify(accessLogMapper).insertAccessLog(null, "/api/auth/login", IP, "FAIL");
    }

    // SCREEN 칸(100자)을 넘는 주소는 100자로 잘라서 저장
    @Test
    void longScreenIsCutTo100Chars() {

        String longUri = "/api/admin/" + "a".repeat(200);

        accessLogService.record(1L, longUri, IP, AccessLogService.SUCCESS);

        verify(accessLogMapper).insertAccessLog(1L, longUri.substring(0, 100), IP, "SUCCESS");
    }

    // 저장이 실패해도 예외를 밖으로 던지지 않는다 (본래 동작을 막지 않게)
    @Test
    void saveFailureIsNotThrown() {

        given(accessLogMapper.insertAccessLog(any(), any(), any(), any()))
                .willThrow(new DataIntegrityViolationException("저장 실패"));

        assertThatCode(() -> accessLogService.record(3L, "/api/card-uploads", IP, "FAIL"))
                .doesNotThrowAnyException();
    }
}
