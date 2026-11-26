package com.hoperefund.admin.service;

import com.hoperefund.admin.dto.AccessLogResponseDTO;
import com.hoperefund.admin.mapper.AdminAccessLogMapper;
import com.hoperefund.common.mapper.AccessLogMapper;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j // [추가 11/23] log.error(...)로 서버 기록을 남기려고 (Lombok이 log 변수를 만들어 줌 · LoginService와 같은 방식)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // [추가 11/20] 조회만 하는 서비스라 읽기 전용 · 이유: AdminInquiryServiceImpl과 같은 방식
public class AdminAccessLogService {
    /* F-21 비즈니스 로직 */

    // [추가 11/23] ACCESS_LOG.SCREEN에 남길 값 (어느 API를 썼는지) · 로그인은 "/api/auth/login"으로 남아요
    static final String VIEW_SCREEN = "/api/admin/access-logs";
    static final String RESULT_SUCCESS =
            "SUCCESS"; // DB CK_ACCESS_LOG_RESULT: SUCCESS / FAIL / DENIED

    private final AdminAccessLogMapper adminAccessLogMapper;

    // [추가 11/23] 공통 접근 기록 저장 매퍼 (로그인 기록과 같은 것을 씀)
    private final AccessLogMapper accessLogMapper;

    //  접근 기록 목록 조회 비즈니스 로직
    // [수정 11/20] PR #45 리뷰 반영
    //   ① String startDate · endDate → LocalDate (형식이 틀린 날짜는 컨트롤러에서 400으로 막힘)
    //   ② page를 0부터 → 1부터 (팀 공통 PageMeta 규칙) · 그래서 offset = (page - 1) * size
    //      예: page=1, size=10 → 0건 건너뛰고 10건 / page=2 → 10건 건너뛰고 10건
    public List<AccessLogResponseDTO> getAccessLogs(
            LocalDate startDate, LocalDate endDate, String ipAddress, int page, int size) {
        int offset = (page - 1) * size;
        return adminAccessLogMapper.selectAccessLogs(
                startDate, nextDay(endDate), ipAddress, offset, size);
    }

    // [추가 11/20] 검색 조건에 맞는 전체 기록 수 · 이유(PR #45 리뷰 ③): 화면이 총 페이지 수를 그리려면 필요
    public int countAccessLogs(LocalDate startDate, LocalDate endDate, String ipAddress) {
        return adminAccessLogMapper.countAccessLogs(startDate, nextDay(endDate), ipAddress);
    }

    /**
     * [추가 11/23] 관리자가 접근 기록을 조회한 사실을 ACCESS_LOG에 남겨요.
     *
     * <p>이유: 기능명세서 F-08-02 비고 「관리자가 목록을 조회한 것도 ACCESS_LOG에 기록해요」 · F-09-01 (관리자 동작 기록)
     *
     * <p>LoginService.writeAccessLog와 같은 규칙이에요.
     *
     * <ul>
     *   <li>번호(MAX + 1)가 다른 요청과 겹치면(DuplicateKeyException) 한 번 더 시도해요
     *   <li>그래도 실패하면 서버 로그만 남겨요 → 기록 실패 때문에 관리자 조회 화면이 막히지 않게
     * </ul>
     *
     * <p>@Transactional: 클래스가 읽기 전용이라, 저장하는 이 메서드만 쓰기 허용으로 따로 붙여요.
     */
    @Transactional
    public void recordView(Long adminId, String ipAddress) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                accessLogMapper.insertAccessLog(adminId, VIEW_SCREEN, ipAddress, RESULT_SUCCESS);
                return;
            } catch (DuplicateKeyException e) {
                if (attempt == 2) {
                    log.error("관리자 조회 기록 저장 실패 (번호 겹침)", e);
                }
            } catch (DataAccessException e) {
                log.error("관리자 조회 기록 저장 실패", e);
                return;
            }
        }
    }

    // [추가 11/20] 끝 날짜의 다음 날 (없으면 null)
    //   이유: ACCESSED_AT은 시각까지 있어서 「끝 날짜 <= 」로 비교하면 그 날 0시까지만 포함됨
    //   → 「다음 날 0시 미만(<)」으로 비교해야 끝 날짜 하루 전체가 들어감 (원래 SQL의 TO_DATE(...) + 1과 같은 뜻)
    private static LocalDate nextDay(LocalDate endDate) {
        return endDate == null ? null : endDate.plusDays(1);
    }
}
