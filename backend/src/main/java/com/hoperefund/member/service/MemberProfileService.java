package com.hoperefund.member.service;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.member.dto.MemberProfileDTO;
import com.hoperefund.member.dto.MemberProfileRequestDTO;
import com.hoperefund.member.dto.MemberProfileResponseDTO;
import com.hoperefund.member.dto.PasswordChangeRequestDTO;
import com.hoperefund.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원정보 · 자격 정보 조회 · 수정 · 비밀번호 변경 (F-01-04).
 *
 * <pre>
 * 조회: 로그인한 회원 → MEMBER 1행 → 이메일 일부를 가려(ho***@example.com) 응답
 * 수정: 성별 · 군 복무 · 입사일 · 회사 규모 · 무주택 세대주 → UPDATE (UPDATED_AT = SYSDATE) → 다시 조회해 응답
 * 비밀번호: 현재 비밀번호 확인 → 새 비밀번호 규칙 확인 → BCrypt 저장
 * </pre>
 *
 * 권한(ROLE) · 계정 상태(STATUS) · 아이디 · 이메일 · 생년월일은 바꾸지 않는다 (SQL 의 SET 에 없음).
 * 여기서 저장한 자격 정보는 세액 계산(F-03-01) · 청년 혜택 진단(F-03-02)이 MEMBER 에서 읽어 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberProfileService {

    static final String NOT_FOUND_MESSAGE = "회원 정보를 찾을 수 없어요";
    static final String MILITARY_MESSAGE = "0 이상으로 입력해 주세요";
    // ORA-02290(값 제한 위반) 오류 문장에 찍히는 제약조건 이름
    private static final String MILITARY_CONSTRAINT = "CK_MEMBER_MILITARY";

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;

    // 내 회원정보 조회
    public MemberProfileResponseDTO getProfile(long memberId) {
        MemberProfileDTO member = memberMapper.selectProfile(memberId);
        if (member == null) {
            throw notFound();
        }
        return MemberProfileResponseDTO.from(member, maskEmail(member.getEmail()));
    }

    // 회원정보 · 자격 정보 수정 → 저장된 값을 다시 읽어 돌려준다 (수정일 포함)
    @Transactional
    public MemberProfileResponseDTO updateProfile(long memberId, MemberProfileRequestDTO request) {
        int updated;
        try {
            updated = memberMapper.updateProfile(memberId, request);
        } catch (DataIntegrityViolationException e) {
            // DB 값 제한(CHECK) 위반 · ORA 코드는 화면에 보내지 않고 서버 로그에만
            log.warn("회원정보 수정 값 제한 위반: memberId={}", memberId);
            throw badRequest("VALIDATION", constraintMessage(e));
        }
        if (updated == 0) {
            throw notFound();
        }
        log.info("회원정보 수정: memberId={}", memberId);
        return getProfile(memberId);
    }

    // 비밀번호 변경 (로그인한 상태에서 현재 비밀번호를 알 때 · 잊었으면 로그인 화면의 재설정 F-01-03)
    @Transactional
    public void changePassword(long memberId, PasswordChangeRequestDTO request) {
        String passwordHash = memberMapper.selectPassword(memberId);
        if (passwordHash == null) {
            throw notFound();
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), passwordHash)) {
            throw badRequest("CURRENT_PASSWORD_MISMATCH", "현재 비밀번호가 맞지 않아요");
        }
        MemberService.validatePassword(request.getNewPassword(), request.getNewPasswordConfirm());
        if (request.getNewPassword().equals(request.getCurrentPassword())) {
            throw badRequest("VALIDATION", "현재 비밀번호와 다른 비밀번호를 입력해 주세요");
        }

        // 원래 비밀번호는 저장하지 않는다
        String newPasswordHash = passwordEncoder.encode(request.getNewPassword());
        memberMapper.updatePassword(memberId, newPasswordHash);
        log.info("비밀번호 변경: memberId={}", memberId);
    }

    // 이메일 앞 2글자만 보이고 나머지는 *** · 예: hope@example.com → ho***@example.com
    static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.substring(0, Math.min(2, at)) + "***" + email.substring(at);
    }

    // 군 복무 개월 수 제한 위반이면 명세 문구 · 그 밖의 제한 위반은 일반 문구
    private static String constraintMessage(DataIntegrityViolationException e) {
        String cause = String.valueOf(e.getMostSpecificCause().getMessage());
        return cause.contains(MILITARY_CONSTRAINT) ? MILITARY_MESSAGE : "입력값을 확인해 주세요";
    }

    private static BusinessException badRequest(String code, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static BusinessException notFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", NOT_FOUND_MESSAGE);
    }
}
