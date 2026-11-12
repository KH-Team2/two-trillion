package com.hoperefund.admin.service;

import com.hoperefund.admin.dto.MaskedMemberResponseDto;
import com.hoperefund.admin.mapper.AdminMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminMemberService {
	/* F-20 비즈니스 로직 및 예외 처리 */
    private final AdminMemberMapper adminMemberMapper;

    // 회원 목록 조회 (마스킹 적용 및 검색/필터 지원)
    public List<MaskedMemberResponseDto> getMaskedMemberList(String keyword, String role, int page, int size) {
        int offset = page * size;
        return adminMemberMapper.selectMaskedMemberList(keyword, role, offset, size);
    }

    // 회원 권한 변경 비즈니스 로직 및 예외 검증
    @Transactional
    public void updateMemberRole(Long targetMemberId, String newRole, Long currentAdminId) {
        MaskedMemberResponseDto targetUser = adminMemberMapper.selectMemberById(targetMemberId);
        
        if (targetUser == null) {
            throw new IllegalArgumentException("존재하지 않는 회원입니다.");
        }

        // 예외 1: 자기 자신의 관리자 권한 해제 시도 방지
        if (targetMemberId.equals(currentAdminId) && "ADMIN".equals(targetUser.getRole()) && "USER".equals(newRole)) {
            throw new IllegalStateException("자기 자신의 관리자 권한은 해제할 수 없습니다.");
        }

        // 예외 2: 성별 정보가 없는 관리자는 일반 회원(USER)으로 변경 불가
        if ("ADMIN".equals(targetUser.getRole()) && "USER".equals(newRole) && (targetUser.getGender() == null || targetUser.getGender().trim().isEmpty())) {
            throw new IllegalStateException("성별 정보가 없는 관리자 계정은 일반 회원으로 변경할 수 없습니다.");
        }

        adminMemberMapper.updateMemberRole(targetMemberId, newRole);
    }
}