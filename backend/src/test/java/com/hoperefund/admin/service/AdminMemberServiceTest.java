package com.hoperefund.admin.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hoperefund.admin.dto.AdminMemberRoleTargetDto;
import com.hoperefund.admin.mapper.AdminMemberMapper;
import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.common.exception.NotFoundException;
import com.hoperefund.common.mapper.AccessLogMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/**
 * AdminMemberService 테스트 (DB 없이 가짜 매퍼로) · [신규 11/23 고은]
 *
 * <p>쪽 계산 · 권한 변경 규칙(없는 회원 · 탈퇴 · 같은 권한 · 자기 해제 · 성별 없음) · 변경 기록 저장을 확인해요.
 *
 * <p>- assertThatThrownBy(() -> 실행).isInstanceOf(예외).hasFieldOrPropertyWithValue("code", 값) : 그 예외 · 그 코드가 나오는지
 *
 * <p>- verify(가짜, never()).메서드(...) : 한 번도 안 불렸는지 (= DB를 안 바꿨는지)
 */
@ExtendWith(MockitoExtension.class)
class AdminMemberServiceTest {

    private static final Long ADMIN_ID = 1001L;
    private static final String IP = "127.0.0.1";

    @Mock private AdminMemberMapper adminMemberMapper;
    @Mock private AccessLogMapper accessLogMapper;
    @InjectMocks private AdminMemberService adminMemberService;

    // 검사용 대상 회원 한 명 만들기
    private static AdminMemberRoleTargetDto target(
            Long id, String role, String status, String gender) {
        AdminMemberRoleTargetDto t = new AdminMemberRoleTargetDto();
        t.setMemberId(id);
        t.setRole(role);
        t.setStatus(status);
        t.setGender(gender);
        return t;
    }

    @Test
    @DisplayName("목록: 2쪽(20건씩)은 20건 건너뛴다")
    void secondPageSkipsTwentyRows() {
        adminMemberService.getMaskedMemberList("naver", "ADMIN", "ACTIVE", 2, 20);

        verify(adminMemberMapper).selectMaskedMemberList("naver", "ADMIN", "ACTIVE", 20, 20);
    }

    @Test
    @DisplayName("승격: USER → ADMIN 이면 바꾸고 기록을 1줄 남긴다")
    void promoteUserAndWriteLog() {
        when(adminMemberMapper.selectRoleTarget(1002L))
                .thenReturn(target(1002L, "USER", "ACTIVE", "F"));

        adminMemberService.updateMemberRole(1002L, "ADMIN", ADMIN_ID, IP);

        verify(adminMemberMapper).updateMemberRole(1002L, "ADMIN");
        verify(accessLogMapper)
                .insertAccessLog(ADMIN_ID, "/api/admin/members/1002/role", IP, "SUCCESS");
    }

    @Test
    @DisplayName("없는 회원이면 404 · DB를 바꾸지 않는다")
    void missingMemberIsNotFound() {
        when(adminMemberMapper.selectRoleTarget(9999L)).thenReturn(null);

        assertThatThrownBy(() -> adminMemberService.updateMemberRole(9999L, "ADMIN", ADMIN_ID, IP))
                .isInstanceOf(NotFoundException.class);
        verify(adminMemberMapper, never()).updateMemberRole(anyLong(), anyString());
    }

    @Test
    @DisplayName("탈퇴 회원은 409 WITHDRAWN_MEMBER")
    void withdrawnMemberIsRejected() {
        when(adminMemberMapper.selectRoleTarget(1003L))
                .thenReturn(target(1003L, "USER", "WITHDRAWN", "M"));

        assertThatThrownBy(() -> adminMemberService.updateMemberRole(1003L, "ADMIN", ADMIN_ID, IP))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "WITHDRAWN_MEMBER");
        verify(adminMemberMapper, never()).updateMemberRole(anyLong(), anyString());
    }

    @Test
    @DisplayName("이미 그 권한이면 409 SAME_ROLE · 기록도 안 남긴다")
    void sameRoleIsRejected() {
        when(adminMemberMapper.selectRoleTarget(1002L))
                .thenReturn(target(1002L, "ADMIN", "ACTIVE", "F"));

        assertThatThrownBy(() -> adminMemberService.updateMemberRole(1002L, "ADMIN", ADMIN_ID, IP))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SAME_ROLE");
        verify(accessLogMapper, never()).insertAccessLog(any(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("자기 자신을 USER로 내리면 409 SELF_DEMOTE (D-06)")
    void selfDemoteIsRejected() {
        when(adminMemberMapper.selectRoleTarget(ADMIN_ID))
                .thenReturn(target(ADMIN_ID, "ADMIN", "ACTIVE", "M"));

        assertThatThrownBy(
                        () -> adminMemberService.updateMemberRole(ADMIN_ID, "USER", ADMIN_ID, IP))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "SELF_DEMOTE");
        verify(adminMemberMapper, never()).updateMemberRole(anyLong(), anyString());
    }

    @Test
    @DisplayName("성별 없는 관리자를 USER로 내리면 409 GENDER_REQUIRED (10/28)")
    void genderlessAdminCannotBecomeUser() {
        when(adminMemberMapper.selectRoleTarget(1004L))
                .thenReturn(target(1004L, "ADMIN", "ACTIVE", null));

        assertThatThrownBy(() -> adminMemberService.updateMemberRole(1004L, "USER", ADMIN_ID, IP))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "GENDER_REQUIRED")
                .hasMessage("성별이 없는 관리자는 일반 회원으로 바꿀 수 없어요");
        verify(adminMemberMapper, never()).updateMemberRole(anyLong(), anyString());
    }

    @Test
    @DisplayName("성별 있는 다른 관리자는 USER로 내릴 수 있다")
    void otherAdminWithGenderCanBecomeUser() {
        when(adminMemberMapper.selectRoleTarget(1005L))
                .thenReturn(target(1005L, "ADMIN", "ACTIVE", "F"));

        adminMemberService.updateMemberRole(1005L, "USER", ADMIN_ID, IP);

        verify(adminMemberMapper).updateMemberRole(1005L, "USER");
    }

    @Test
    @DisplayName("조회 기록: 번호가 겹치면 한 번 더 시도하고, 그래도 실패해도 예외를 밖으로 안 낸다")
    void recordViewRetriesOnceOnDuplicateKey() {
        when(accessLogMapper.insertAccessLog(ADMIN_ID, "/api/admin/members", IP, "SUCCESS"))
                .thenThrow(new DuplicateKeyException("겹침"));

        assertThatCode(() -> adminMemberService.recordView(ADMIN_ID, IP))
                .doesNotThrowAnyException();
        verify(accessLogMapper, times(2))
                .insertAccessLog(ADMIN_ID, "/api/admin/members", IP, "SUCCESS");
    }
}
