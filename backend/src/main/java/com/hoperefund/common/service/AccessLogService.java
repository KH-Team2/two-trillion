package com.hoperefund.common.service;

import com.hoperefund.common.mapper.AccessLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * 접근 기록(ACCESS_LOG) 저장 (F-09-01).
 *
 * <pre>
 * 부르는 곳: LoginService (로그인 성공 · 실패) · AccessLogFilter (업로드 · 계산 · 관리자 동작 · 403)
 * 저장에 실패해도 예외를 밖으로 던지지 않는다 → 기록 때문에 로그인 · 계산 같은 본래 동작이 막히지 않게
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessLogService {

    // ACCESS_LOG.RESULT 에 넣을 수 있는 값 (CK_ACCESS_LOG_RESULT · 이 밖의 값은 ORA-02290)
    public static final String SUCCESS = "SUCCESS";
    public static final String FAIL = "FAIL";
    public static final String DENIED = "DENIED";

    static final int SCREEN_MAX_LENGTH = 100; // ACCESS_LOG.SCREEN VARCHAR2(100)

    private final AccessLogMapper accessLogMapper;

    // 접근 기록 1건 저장. memberId 는 로그인 전이면 null
    public void record(Long memberId, String screen, String ipAddress, String result) {
        try {
            accessLogMapper.insertAccessLog(memberId, cut(screen), ipAddress, result);
        } catch (DataAccessException e) {
            log.error("접근 기록 저장 실패 (회원={} · 화면={} · 결과={})", memberId, screen, result, e);
        }
    }

    // 칸 길이(100자)를 넘는 주소는 잘라서 넣는다 (넘으면 ORA-12899로 기록이 통째로 빠짐)
    private static String cut(String screen) {
        if (screen.length() <= SCREEN_MAX_LENGTH) {
            return screen;
        }
        return screen.substring(0, SCREEN_MAX_LENGTH);
    }
}
