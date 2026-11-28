package com.hoperefund.admin.service;

import com.hoperefund.admin.dto.AdminPredictionResponseDto;
import com.hoperefund.admin.mapper.AdminPredictionMapper;
import com.hoperefund.common.mapper.AccessLogMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 예측 결과 조회 (F-08-02 ② · S-08) · [신규 11/26 고은]
 *
 * <p>전체 회원의 예상 세액 계산 결과(PREDICTION)를 검색 · 쪽 나눠서 보여 주고, 관리자가 본 것을 ACCESS_LOG에 남겨요.
 *
 * <p>- @Service: 스프링이 이 클래스를 하나 만들어 두고 Controller에 넣어 줘요.
 *
 * <p>- @RequiredArgsConstructor (롬복): final 칸(매퍼 2개)을 받는 생성자를 자동으로 만들어요 → 스프링이 매퍼를 넣어 줘요.
 *
 * <p>- @Slf4j (롬복): log.error(...)로 서버 기록을 남길 수 있게 해요.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPredictionService {

    // ACCESS_LOG.SCREEN에 남길 값 (회원 관리 · 접근 기록과 같은 규칙: API 주소)
    static final String LIST_SCREEN = "/api/admin/predictions";
    static final String RESULT_SUCCESS = "SUCCESS";

    private final AdminPredictionMapper adminPredictionMapper;
    private final AccessLogMapper accessLogMapper; // 공통 접근 기록 (로그인 · F-20 · F-21과 같은 것)

    /**
     * 예측 결과 한 쪽
     *
     * <p>- offset(건너뛸 건수) = (쪽 번호 - 1) × 한 쪽 건수 · 예) 2쪽 · 20건씩 → 20건 건너뜀
     *
     * <p>- @Transactional(readOnly = true): 읽기만 하는 작업이라고 알려 줘요 (실수로 바꾸는 것 방지)
     */
    @Transactional(readOnly = true)
    public List<AdminPredictionResponseDto> getPredictions(
            String keyword, Integer taxYear, String kind, int page, int size) {
        int offset = (page - 1) * size;
        return adminPredictionMapper.selectPredictions(keyword, taxYear, kind, offset, size);
    }

    /** 검색 조건에 맞는 전체 건수 */
    @Transactional(readOnly = true)
    public int countPredictions(String keyword, Integer taxYear, String kind) {
        return adminPredictionMapper.countPredictions(keyword, taxYear, kind);
    }

    /** 관리자가 예측 결과 목록을 본 것을 남겨요 (명세 F-08-02 비고 「목록을 조회한 것도 ACCESS_LOG에 기록」) */
    @Transactional
    public void recordView(Long adminId, String ipAddress) {
        writeAccessLog(adminId, LIST_SCREEN, ipAddress);
    }

    /**
     * ACCESS_LOG 한 줄 저장 (AdminMemberService · AdminAccessLogService와 같은 규칙)
     *
     * <ul>
     *   <li>번호(MAX + 1)가 다른 요청과 겹치면(DuplicateKeyException) 한 번 더 시도해요
     *   <li>그래도 실패하면 서버 로그만 남겨요 → 기록 실패 때문에 조회 화면이 막히지 않게
     * </ul>
     */
    private void writeAccessLog(Long adminId, String screen, String ipAddress) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                accessLogMapper.insertAccessLog(adminId, screen, ipAddress, RESULT_SUCCESS);
                return;
            } catch (DuplicateKeyException e) {
                if (attempt == 2) {
                    log.error("관리자 동작 기록 저장 실패 (번호 겹침) screen={}", screen, e);
                }
            } catch (DataAccessException e) {
                log.error("관리자 동작 기록 저장 실패 screen={}", screen, e);
                return;
            }
        }
    }
}
