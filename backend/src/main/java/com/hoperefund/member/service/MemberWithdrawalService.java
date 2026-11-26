package com.hoperefund.member.service;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.common.service.TokenService;
import com.hoperefund.common.service.TokenService.TokenInfo;
import com.hoperefund.member.dto.MemberWithdrawalRequestDTO;
import com.hoperefund.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 (F-01-05 · D-05 · D-21).
 *
 * <pre>
 * ① 로그인한 회원의 비밀번호 확인 ── 없거나 이미 탈퇴 → 404 · 틀림 → 400 CURRENT_PASSWORD_MISMATCH
 * ② MEMBER UPDATE: STATUS = WITHDRAWN · WITHDRAWN_AT = SYSDATE · ROLE = USER (아이디 · 이메일은 유지)
 * ③ 지금 쓰는 토큰을 무효로 (로그아웃)
 * </pre>
 *
 * 30일 유예기간 동안 아이디 · 이메일을 그대로 두므로 같은 아이디 · 이메일로 다시 가입할 수 없다 (중복확인이 탈퇴 회원도 센다).
 * 30일이 지난 회원의 식별 불가 처리는 여기서 하지 않는다 (97_데이터정리.sql 2-2 · 하루 한 번).
 * 문의 · 접근 기록(INQUIRY · ACCESS_LOG)은 지우지 않는다 (10/26 확정).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberWithdrawalService {

    static final String NOT_FOUND_MESSAGE = "회원 정보를 찾을 수 없어요";
    static final String PASSWORD_MISMATCH_MESSAGE = "비밀번호가 맞지 않아요";

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @Transactional
    public void withdraw(long memberId, MemberWithdrawalRequestDTO request, TokenInfo tokenInfo) {
        // ① 비밀번호 확인 (탈퇴하지 않은 본인만 조회됨)
        String passwordHash = memberMapper.selectPassword(memberId);
        if (passwordHash == null) {
            throw notFound();
        }
        if (!passwordMatches(request.getPassword(), passwordHash)) {
            log.warn("회원 탈퇴 비밀번호 불일치: memberId={}", memberId);
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "CURRENT_PASSWORD_MISMATCH", PASSWORD_MISMATCH_MESSAGE);
        }

        // ② 상태 · 탈퇴일을 한 문장에서 같이 바꾼다 → CK_MEMBER_WITHDRAWN(ORA-02290) 위반이 나지 않음
        // 0행 = 그사이 다른 요청으로 이미 탈퇴됨
        if (memberMapper.withdrawMember(memberId) == 0) {
            throw notFound();
        }

        // ③ 로그아웃 (토큰 값은 로그에 남기지 않는다)
        if (tokenInfo != null) {
            tokenService.logout(tokenInfo);
        }
        log.info("회원 탈퇴: memberId={}", memberId);
    }

    // BCrypt 는 72바이트가 넘는 비밀번호에 오류를 내므로, 그 경우도 "틀린 비밀번호"로 처리한다 (LoginService 와 같음)
    private boolean passwordMatches(String rawPassword, String passwordHash) {
        try {
            return passwordEncoder.matches(rawPassword, passwordHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static BusinessException notFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", NOT_FOUND_MESSAGE);
    }
}
