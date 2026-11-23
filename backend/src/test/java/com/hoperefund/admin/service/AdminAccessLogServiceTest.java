package com.hoperefund.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hoperefund.admin.mapper.AdminAccessLogMapper;
import com.hoperefund.common.mapper.AccessLogMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;

/**
 * AdminAccessLogService 테스트 (DB 없이) · [신규 11/20] · [수정 11/23] 조회 기록 저장 테스트 3개 추가
 *
 * <p>페이지 번호(1부터) → 건너뛸 건수(offset) 계산과 끝 날짜 + 1일 처리, 관리자 조회 기록 저장을 확인한다.
 *
 * <p>- when(가짜.메서드(...)).thenThrow(예외) : 가짜가 그 예외를 던지게 정함 (DB 오류 흉내)
 *
 * <p>- verify(가짜, times(2)) : 정확히 2번 불렸는지 확인
 *
 * <p>- assertThatCode(() -> 실행).doesNotThrowAnyException() : 실행해도 예외가 밖으로 나오지 않는지 확인
 */
@ExtendWith(MockitoExtension.class)
class AdminAccessLogServiceTest {

    @Mock private AdminAccessLogMapper adminAccessLogMapper;

    // [추가 11/23] 공통 접근 기록 저장 매퍼도 가짜로 (Service 생성자에 같이 들어가요)
    @Mock private AccessLogMapper accessLogMapper;

    @InjectMocks private AdminAccessLogService adminAccessLogService;

    @Test
    @DisplayName("목록: 1페이지는 0건 건너뛰고, 끝 날짜는 다음 날 0시 미만으로 넘긴다")
    void firstPageStartsAtZeroAndEndDateIsNextDay() {
        LocalDate start = LocalDate.of(2026, 11, 1);
        LocalDate end = LocalDate.of(2026, 11, 20);

        adminAccessLogService.getAccessLogs(start, end, null, 1, 10);

        verify(adminAccessLogMapper)
                .selectAccessLogs(start, LocalDate.of(2026, 11, 21), null, 0, 10);
    }

    @Test
    @DisplayName("목록: 3페이지(10건씩)는 20건 건너뛴다 · 날짜가 없으면 null 그대로")
    void thirdPageSkipsTwentyRows() {
        adminAccessLogService.getAccessLogs(null, null, "127.0.0.1", 3, 10);

        verify(adminAccessLogMapper).selectAccessLogs(null, null, "127.0.0.1", 20, 10);
    }

    @Test
    @DisplayName("개수: 같은 조건(끝 날짜 + 1일)으로 센다")
    void countUsesSameCondition() {
        LocalDate end = LocalDate.of(2026, 11, 20);
        when(adminAccessLogMapper.countAccessLogs(null, LocalDate.of(2026, 11, 21), null))
                .thenReturn(35);

        assertThat(adminAccessLogService.countAccessLogs(null, end, null)).isEqualTo(35);
    }

    // ── [추가 11/23] 관리자 조회 기록 ──

    @Test
    @DisplayName("조회 기록: 관리자 번호 · 화면 주소 · IP · SUCCESS로 한 번 저장한다")
    void recordViewSavesOnce() {
        adminAccessLogService.recordView(1001L, "127.0.0.1");

        verify(accessLogMapper)
                .insertAccessLog(1001L, "/api/admin/access-logs", "127.0.0.1", "SUCCESS");
    }

    @Test
    @DisplayName("조회 기록: 번호가 겹치면 한 번 더 시도하고, 두 번 다 실패해도 예외를 밖으로 내지 않는다")
    void recordViewRetriesOnceOnDuplicateKey() {
        when(accessLogMapper.insertAccessLog(
                        1001L, "/api/admin/access-logs", "127.0.0.1", "SUCCESS"))
                .thenThrow(new DuplicateKeyException("겹침"));

        assertThatCode(() -> adminAccessLogService.recordView(1001L, "127.0.0.1"))
                .doesNotThrowAnyException();
        verify(accessLogMapper, times(2))
                .insertAccessLog(1001L, "/api/admin/access-logs", "127.0.0.1", "SUCCESS");
    }

    @Test
    @DisplayName("조회 기록: 다른 DB 오류는 다시 시도하지 않고, 조회 화면을 막지 않는다")
    void recordViewDoesNotRetryOnOtherDbError() {
        when(accessLogMapper.insertAccessLog(
                        1001L, "/api/admin/access-logs", "127.0.0.1", "SUCCESS"))
                .thenThrow(new DataAccessResourceFailureException("DB 연결 끊김"));

        assertThatCode(() -> adminAccessLogService.recordView(1001L, "127.0.0.1"))
                .doesNotThrowAnyException();
        verify(accessLogMapper, times(1))
                .insertAccessLog(1001L, "/api/admin/access-logs", "127.0.0.1", "SUCCESS");
    }
}
