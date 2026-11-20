package com.hoperefund.admin.service;

import com.hoperefund.admin.dto.AdminInquiryResponseDto;
import com.hoperefund.admin.dto.InquiryAnswerRequestDto;
import com.hoperefund.admin.dto.InquiryCreateRequestDto;
import com.hoperefund.admin.mapper.AdminInquiryMapper;
import com.hoperefund.common.exception.BadRequestException;
import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.common.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * F-17, F-18 비즈니스 로직 구현체
 *
 * <p>[수정 11/20] 예외를 IllegalArgumentException → 팀 공통 예외(BadRequestException · NotFoundException ·
 * BusinessException)로 바꿈 · 이유: 공통 GlobalExceptionHandler는 IllegalArgumentException을 따로 처리하지 않아서 「모든 오류
 * → 500 서버 오류」로 나갔음. 공통 예외를 던지면 400 · 404 · 409와 화면 문구가 알맞게 나감 (backend/AGENTS.md API 규칙).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminInquiryServiceImpl implements AdminInquiryService {
    /* F-17, F-18 비즈니스 로직 구현체 */
    private final AdminInquiryMapper adminInquiryMapper;

    // DB INQUIRY.STATUS 값 (CK_INQUIRY_STATUS)
    private static final String STATUS_ANSWERED = "ANSWERED";

    // [추가 11/20] F-17: 문의 작성
    //   유형 · 제목 · 내용 검사는 DTO의 @Valid가 이미 끝냄 → 여기서는 저장만
    @Override
    @Transactional // 저장은 읽기 전용(readOnly)이 아니라서 따로 붙임
    public void createInquiry(Long memberId, InquiryCreateRequestDto dto) {
        adminInquiryMapper.insertInquiry(memberId, dto);
    }

    @Override
    public List<AdminInquiryResponseDto> getInquiryList(Long memberId) {
        return adminInquiryMapper.selectInquiryList(memberId);
    }

    // [수정 11/20] memberId 추가 + 예외 종류 변경
    //   이유(PR #44 리뷰): 본인 문의만 보이게 · 남의 문의는 「없는 문의」와 같은 404로 숨김
    //   (403으로 보내면 「그 번호의 문의가 있긴 하다」는 걸 알려 주게 됨 · 팀 규칙 F-09-02)
    @Override
    public AdminInquiryResponseDto getInquiryDetail(Long memberId, Long inquiryId) {
        AdminInquiryResponseDto detail = adminInquiryMapper.selectInquiryById(inquiryId, memberId);
        if (detail == null) {
            // [수정 11/20] IllegalArgumentException("존재하지 않는 문의 내역입니다.") → NotFoundException (500 → 404)
            throw new NotFoundException("문의를 찾을 수 없어요");
        }
        return detail;
    }

    // [추가 11/20] F-18: 관리자용 전체 문의 목록
    @Override
    public List<AdminInquiryResponseDto> getAdminInquiryList(String status) {
        return adminInquiryMapper.selectAdminInquiryList(status);
    }

    @Override
    @Transactional
    public void updateInquiryAnswer(Long inquiryId, InquiryAnswerRequestDto dto, Long adminId) {
        // [삭제 11/20] 상태 빈칸 검사(if dto.getStatus() == null ...) · 이유: DTO의 @NotBlank · @Pattern이 같은 검사를 먼저 함

        // [추가 11/20] 답변 완료로 바꿀 때는 답변 내용이 꼭 있어야 함
        //   이유: DB CK_INQUIRY_ANSWER 규칙(ANSWERED면 답변 필수)에 걸려 DB 오류(500)가 나기 전에 400으로 안내 (기능명세서 F-07-02 문구)
        if (STATUS_ANSWERED.equals(dto.getStatus())
                && (dto.getAnswer() == null || dto.getAnswer().isBlank())) {
            throw new BadRequestException("답변을 입력해 주세요");
        }

        // [추가 11/20] 지금 상태 확인 · 없는 문의 → 404 · 이미 답변 완료 → 409
        //   이유: 답변 완료된 문의를 확인 중으로 되돌리면 답변이 지워지므로 막음
        String currentStatus = adminInquiryMapper.selectInquiryStatus(inquiryId);
        if (currentStatus == null) {
            throw new NotFoundException("문의를 찾을 수 없어요");
        }
        if (STATUS_ANSWERED.equals(currentStatus)) {
            throw alreadyAnswered();
        }

        // [수정 11/20] adminId(로그인한 관리자 번호)를 같이 넘김 · 이유: ANSWERED_BY 저장 (PR #44 리뷰)
        int result = adminInquiryMapper.updateInquiryAnswer(inquiryId, dto, adminId);
        if (result == 0) {
            // [수정 11/20] 위에서 확인한 뒤 다른 관리자가 먼저 답변을 끝낸 경우 (SQL의 AND STATUS != 'ANSWERED'에 걸림)
            //   원래: IllegalArgumentException("답변 등록에 실패했습니다. 존재하지 않는 문의 ID입니다.") → 500
            throw alreadyAnswered();
        }
    }

    // [추가 11/20] 409 「이미 답변 완료」 예외를 두 곳에서 같이 쓰려고 메서드로 뺌
    //   BusinessException(상태 코드, 오류 코드, 화면 문구): 팀 공통 예외 · GlobalExceptionHandler가 그대로 응답으로 바꿈
    private static BusinessException alreadyAnswered() {
        return new BusinessException(HttpStatus.CONFLICT, "ALREADY_ANSWERED", "이미 답변이 완료된 문의예요");
    }
}
