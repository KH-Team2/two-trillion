package com.hoperefund.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hoperefund.admin.mapper.AdminPredictionMapper;
import com.hoperefund.common.mapper.AccessLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;

/**
 * AdminPredictionService 테스트 (DB 없이 가짜 매퍼로) · [신규 11/26 고은]
 *
 * <p>쪽 계산 · 검색 조건 전달 · 조회 기록 저장(번호 겹침 재시도 · DB 오류여도 화면은 그대로)을 확인해요.
 *
 * <p>- @Mock: 진짜 DB 대신 「불린 것만 기억하는」 가짜 매퍼 · @InjectMocks: 가짜 매퍼를 넣어 서비스를 만들어요
 *
 * <p>- verify(가짜).메서드(값) : 그 값으로 불렸는지 · times(n): n번 불렸는지
 */
@ExtendWith(MockitoExtension.class)
class AdminPredictionServiceTest {

    private static final Long ADMIN_ID = 1001L;
    private static final String IP = "127.0.0.1";
    private static final String SCREEN = "/api/admin/predictions";

    @Mock private AdminPredictionMapper adminPredictionMapper;
    @Mock private AccessLogMapper accessLogMapper;
    @InjectMocks private AdminPredictionService adminPredictionService;

    @Test
    @DisplayName("목록: 1쪽은 건너뛰지 않는다")
    void firstPageSkipsNothing() {
        adminPredictionService.getPredictions(null, null, null, 1, 10);

        verify(adminPredictionMapper).selectPredictions(null, null, null, 0, 10);
    }

    @Test
    @DisplayName("목록: 3쪽(20건씩)은 40건 건너뛰고, 검색 조건을 그대로 넘긴다")
    void thirdPageSkipsFortyRowsWithFilters() {
        adminPredictionService.getPredictions("naver", 2026, "SCENARIO", 3, 20);

        verify(adminPredictionMapper).selectPredictions("naver", 2026, "SCENARIO", 40, 20);
    }

    @Test
    @DisplayName("전체 건수: 매퍼가 센 숫자를 그대로 돌려준다")
    void countReturnsMapperValue() {
        when(adminPredictionMapper.countPredictions("naver", 2026, "CURRENT")).thenReturn(7);

        assertThat(adminPredictionService.countPredictions("naver", 2026, "CURRENT")).isEqualTo(7);
    }

    @Test
    @DisplayName("조회 기록: 관리자 번호 · 화면 · IP · SUCCESS로 1줄 남긴다")
    void recordViewWritesOneLog() {
        adminPredictionService.recordView(ADMIN_ID, IP);

        verify(accessLogMapper).insertAccessLog(ADMIN_ID, SCREEN, IP, "SUCCESS");
    }

    @Test
    @DisplayName("조회 기록: 번호가 겹치면 한 번 더 시도하고, 두 번 다 실패해도 예외를 밖으로 내지 않는다")
    void recordViewRetriesOnceOnDuplicateKey() {
        when(accessLogMapper.insertAccessLog(ADMIN_ID, SCREEN, IP, "SUCCESS"))
                .thenThrow(new DuplicateKeyException("겹침"));

        assertThatCode(() -> adminPredictionService.recordView(ADMIN_ID, IP))
                .doesNotThrowAnyException();
        verify(accessLogMapper, times(2)).insertAccessLog(ADMIN_ID, SCREEN, IP, "SUCCESS");
    }

    @Test
    @DisplayName("조회 기록: 다른 DB 오류는 다시 시도하지 않고, 조회 화면을 막지 않는다")
    void recordViewDoesNotRetryOnOtherDbError() {
        when(accessLogMapper.insertAccessLog(ADMIN_ID, SCREEN, IP, "SUCCESS"))
                .thenThrow(new DataAccessResourceFailureException("DB 연결 끊김"));

        assertThatCode(() -> adminPredictionService.recordView(ADMIN_ID, IP))
                .doesNotThrowAnyException();
        verify(accessLogMapper, times(1)).insertAccessLog(ADMIN_ID, SCREEN, IP, "SUCCESS");
    }
}
