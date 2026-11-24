package com.hoperefund.admin.service;

import com.hoperefund.admin.dto.MaskedMemberResponseDto;
import com.hoperefund.admin.mapper.AdminMemberMapper;
import com.hoperefund.common.dto.ApiResponse;
import com.hoperefund.common.dto.PageMeta;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * F-20: 관리자 회원 목록 조회 및 권한 변경 비즈니스 로직[cite: 3]
 */
@Service
@RequiredArgsConstructor
public class AdminMemberService {

    private final AdminMemberMapper adminMemberMapper;

    /**
     * 1-based page를 오프셋으로 변환해 마스킹 회원 목록과 전체 건수를 조회하고
     * 공통 응답 봉투(ApiResponse) 및 페이지 정보(PageMeta)로 반환[cite: 3]
     */
    public ApiResponse<List<MaskedMemberResponseDto>> getMaskedMemberList(
            String keyword, String role, int page, int size) {
        // 1-based 페이지 번호 기준 오프셋 계산[cite: 3]
        int offset = (page - 1) * size;
        List<MaskedMemberResponseDto> members =
                adminMemberMapper.selectMaskedMemberList(keyword, role, offset, size);
        // 전체 건수를 조회해 PageMeta에 반영 (매퍼 쿼리 존재 여부 확인 필요)[cite: 3]
        long total = adminMemberMapper.countMaskedMembers(keyword, role);
        return ApiResponse.ok(members, PageMeta.of(page, size, (int) total));
    }

    /**
     * 회원 권한(USER <-> ADMIN) 변경 및 예외 검증[cite: 3]
     * - 대상 회원 존재 여부 확인
     * - 자기 자신의 관리자 권한 해제 방지
     * - 성별(GENDER) 미입력 관리자의 일반 회원(USER) 전환 방지
     */
    @Transactional
    public void updateMemberRole(Long targetMemberId, String newRole, Long currentAdminId) {
        MaskedMemberResponseDto targetUser = adminMemberMapper.selectMemberById(targetMemberId);

        // 대상 회원이 없는 경우 예외 발생[cite: 3]
        if (targetUser == null) {
            throw new IllegalArgumentException("존재하지 않는 회원입니다.");
        }

        // 예외 1: 로그인한 관리자 본인의 ADMIN 권한을 USER로 내리는 행위 차단[cite: 3]
        if (currentAdminId != null
                && targetMemberId.equals(currentAdminId)
                && "ADMIN".equals(targetUser.getRole())
                && "USER".equals(newRole)) {
            throw new IllegalStateException("자기 자신의 관리자 권한은 해제할 수 없습니다.");
        }

        // 예외 2: 성별 정보가 없는 관리자는 일반 회원(USER)으로 변경 불가 (ERD MEMBER.GENDER 확인)[cite: 3]
        if ("ADMIN".equals(targetUser.getRole())
                && "USER".equals(newRole)
                && (targetUser.getGender() == null || targetUser.getGender().trim().isEmpty())) {
            throw new IllegalStateException("성별 정보가 없는 관리자 계정은 일반 회원으로 변경할 수 없습니다.");
        }

        // 검증 통과 시 권한 및 수정일시(UPDATED_AT) 업데이트[cite: 3]
        adminMemberMapper.updateMemberRole(targetMemberId, newRole);
    }
}