package com.hoperefund.admin.service;

import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hoperefund.admin.dto.MaskedMemberResponseDto;
import com.hoperefund.admin.mapper.AdminMemberMapper;
import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.common.exception.NotFoundException;
import com.hoperefund.common.mapper.AccessLogMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j // [추가 11/23 고은] log.error(...)로 서버 기록을 남기려고 (F-21 · LoginService와 같은 방식)
@Service
@RequiredArgsConstructor
public class AdminMemberService {
    /* F-20 비즈니스 로직 및 예외 처리 */

    // [추가 11/23 고은] ACCESS_LOG.SCREEN에 남길 값 · 회원 목록 조회
    static final String LIST_SCREEN = "/api/admin/members";
    static final String RESULT_SUCCESS = "SUCCESS";

    // [추가 11/23 고은] DB 값 그대로 (CK_MEMBER_ROLE · CK_MEMBER_STATUS)
    static final String ROLE_ADMIN = "ADMIN";
    static final String ROLE_USER = "USER";
    static final String STATUS_WITHDRAWN = "WITHDRAWN";

    private final AdminMemberMapper adminMemberMapper;

    // [추가 11/23 고은] 공통 접근 기록 저장 매퍼 (로그인 · F-21과 같은 것)
    private final AccessLogMapper accessLogMapper;

    // 회원 목록 조회 (마스킹 적용 및 검색/필터 지원)
    // [수정 11/23 고은] ① page를 0부터 → 1부터 (팀 공통 PageMeta) · offset = (page - 1) * size
    //                   ② status(계정 상태) 조건 추가
    //                   ③ 읽기만 하니까 @Transactional(readOnly = true)
    
    // [추가 11/23 고은] 관리자가 회원 목록을 본 것을 ACCESS_LOG에 남겨요 (명세 F-08-02 비고)
    @Transactional
    public void recordView(Long adminId, String ipAddress) {
        writeAccessLog(adminId, LIST_SCREEN, ipAddress);
    }

    // 회원 권한 변경 비즈니스 로직 및 예외 검증
    // [수정 11/23 고은] 예외를 팀 공통 예외로 바꾸고, 변경 기록(ACCESS_LOG)을 남겨요
    //   - IllegalArgumentException / IllegalStateException → NotFoundException(404) ·
    // BusinessException(409)
    //     이유: 공통 처리기(GlobalExceptionHandler)는 IllegalArgumentException을 500으로 보내요
    //           → 팀 공통 예외를 던져야 화면이 { success:false, error:{code, message} } 모양으로 받아요
    //   - ipAddress 매개변수 추가 · 이유: 명세 F-01-06 ④ 「변경 작업을 ACCESS_LOG에 기록」
    @Transactional
     /**
     * [추가 11/23 고은] ACCESS_LOG 한 줄 저장 (LoginService.writeAccessLog · F-21 recordView와 같은 규칙)
     *
     * <ul>
     *   <li>번호(MAX + 1)가 다른 요청과 겹치면(DuplicateKeyException) 한 번 더 시도해요
     *   <li>그래도 실패하면 서버 로그만 남겨요 → 기록 실패 때문에 관리자 기능이 막히지 않게
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
