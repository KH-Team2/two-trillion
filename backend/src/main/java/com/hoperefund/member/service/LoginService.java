package com.hoperefund.member.service;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.common.mapper.AccessLogMapper;
import com.hoperefund.common.service.TokenService;
import com.hoperefund.common.service.TokenService.IssuedToken;
import com.hoperefund.common.service.TokenService.TokenInfo;
import com.hoperefund.member.dto.LoginMemberDTO;
import com.hoperefund.member.dto.LoginRequestDTO;
import com.hoperefund.member.dto.LoginResponseDTO;
import com.hoperefund.member.mapper.MemberMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 로그인 · 로그아웃 처리 Service (F-01-02 · decisions/0004)
 *
 * <pre>
 * ① 아이디로 회원 조회 ── 없음 → 401 LOGIN_FAILED
 * ② 잠금 확인 (잠금 켰을 때) → 401 LOGIN_LOCKED   (비밀번호 비교 전 — 잠금 중에 비밀번호를 맞혀 보지 못하게)
 * ③ 비밀번호 비교 ── 틀림 → 실패 횟수 + 1 (5번째면 10분 잠금) → 401 LOGIN_FAILED
 * ④ 탈퇴 확인 → 401 WITHDRAWN_MEMBER          (비밀번호가 맞은 사람에게만 — 아이디 존재 여부를 알 수 없게)
 * ⑤ 성공 → 실패 횟수 초기화 · 토큰 발급
 * 모든 결과를 ACCESS_LOG에 SUCCESS / FAIL로 남긴다.
 * </pre>
 *
 * @Transactional을 붙이지 않는다: 실패할 때 예외를 던지므로, 트랜잭션 안이면 실패 기록(ACCESS_LOG · 실패 횟수)까지 같이 취소된다.
 * 각 SQL은 한 문장씩 바로 저장된다.
 */
@Service
public class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    static final int MAX_FAIL_COUNT = 5; // 연속 실패 허용 횟수 (5번째 실패 시 잠금)
    static final int LOCK_MINUTES = 10; // 잠금 시간
    static final String LOGIN_SCREEN = "/api/auth/login"; // ACCESS_LOG.SCREEN

    private static final String STATUS_WITHDRAWN = "WITHDRAWN";
    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_FAIL = "FAIL";
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final MemberMapper memberMapper;
    private final AccessLogMapper accessLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final boolean lockEnabled;

    // 없는 아이디일 때도 비밀번호 비교 시간을 똑같이 쓰기 위한 가짜 암호문 (응답 시간으로 아이디 존재를 알 수 없게)
    private final String dummyPasswordHash;

    public LoginService(
            MemberMapper memberMapper,
            AccessLogMapper accessLogMapper,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            @Value("${member.login-lock.enabled:true}") boolean lockEnabled) {
        this.memberMapper = memberMapper;
        this.accessLogMapper = accessLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.lockEnabled = lockEnabled;
        this.dummyPasswordHash = passwordEncoder.encode("hope-refund-dummy-password");
    }

    public LoginResponseDTO login(LoginRequestDTO request, String ipAddress) {

        String loginId = request.loginId().trim().toLowerCase(Locale.ROOT);
        LoginMemberDTO member = memberMapper.selectLoginMember(loginId);

        // ① 없는 아이디
        if (member == null) {
            passwordMatches(request.password(), dummyPasswordHash);
            writeAccessLog(null, ipAddress, RESULT_FAIL);
            throw loginFailed();
        }
        long memberId = member.getMemberId();

        // ② 잠금 중
        if (lockEnabled && member.isLocked()) {
            writeAccessLog(memberId, ipAddress, RESULT_FAIL);
            throw new BusinessException(
                    HttpStatus.UNAUTHORIZED, "LOGIN_LOCKED", "10분 뒤 다시 시도해 주세요");
        }

        // ③ 비밀번호 틀림
        if (!passwordMatches(request.password(), member.getPassword())) {
            if (lockEnabled) {
                memberMapper.increaseLoginFail(memberId, MAX_FAIL_COUNT, LOCK_MINUTES);
            }
            writeAccessLog(memberId, ipAddress, RESULT_FAIL);
            throw loginFailed();
        }

        // ④ 탈퇴 회원
        if (STATUS_WITHDRAWN.equals(member.getStatus())) {
            writeAccessLog(memberId, ipAddress, RESULT_FAIL);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "WITHDRAWN_MEMBER", "탈퇴한 계정이에요");
        }

        // ⑤ 성공
        if (member.getLoginFailCount() != null && member.getLoginFailCount() > 0) {
            memberMapper.resetLoginFail(memberId);
        }
        IssuedToken token = tokenService.issue(memberId, member.getLoginId(), member.getRole());
        writeAccessLog(memberId, ipAddress, RESULT_SUCCESS);

        return new LoginResponseDTO(
                token.token(),
                TokenService.TOKEN_TYPE,
                LocalDateTime.ofInstant(token.expiresAt(), KOREA),
                member.getLoginId(),
                member.getRole());
    }

    // 로그아웃: 그 토큰을 무효 처리 (다음 요청부터 401)
    public void logout(TokenInfo tokenInfo) {
        tokenService.logout(tokenInfo);
    }

    // BCrypt는 72바이트가 넘는 비밀번호에 오류를 내므로 틀린 비밀번호로 처리
    private boolean passwordMatches(String rawPassword, String passwordHash) {
        try {
            return passwordEncoder.matches(rawPassword, passwordHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // ACCESS_LOG 기록: 번호(MAX + 1)가 겹치면 한 번 더 시도 · 그래도 실패하면 서버 로그만 남김 (로그인을 막지 않음)
    private void writeAccessLog(Long memberId, String ipAddress, String result) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                accessLogMapper.insertAccessLog(memberId, LOGIN_SCREEN, ipAddress, result);
                return;
            } catch (DuplicateKeyException e) {
                if (attempt == 2) {
                    log.error("접속 기록 저장 실패 (번호 겹침 · {})", result, e);
                }
            } catch (DataAccessException e) {
                log.error("접속 기록 저장 실패 ({})", result, e);
                return;
            }
        }
    }

    private static BusinessException loginFailed() {
        return new BusinessException(
                HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", "아이디 또는 비밀번호가 맞지 않아요");
    }
}
