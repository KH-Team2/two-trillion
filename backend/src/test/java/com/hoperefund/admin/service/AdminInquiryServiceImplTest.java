package com.hoperefund.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hoperefund.admin.dto.AdminInquiryResponseDTO;
import com.hoperefund.admin.dto.InquiryAnswerRequestDTO;
import com.hoperefund.admin.dto.InquiryCreateRequestDTO;
import com.hoperefund.admin.mapper.AdminInquiryMapper;
import com.hoperefund.common.exception.BadRequestException;
import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.common.exception.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * AdminInquiryServiceImpl 테스트 (DB 없이) · [신규 11/20]
 *
 * <p>Mockito: 진짜 AdminInquiryMapper 대신 "가짜(mock)"를 넣어서, DB 없이 Service의 검사 · 흐름만 확인한다.
 *
 * <p>- when(가짜.메서드(...)).thenReturn(값) : 가짜가 이 값을 돌려주게 정함
 *
 * <p>- verify(가짜).메서드(...) : 그 메서드가 불렸는지 확인 · never() : 한 번도 안 불렸는지 확인
 *
 * <p>- assertThatThrownBy(() -> 실행).isInstanceOf(예외.class) : 실행하면 그 예외가 나는지 확인
 */
@ExtendWith(MockitoExtension.class) // JUnit에서 Mockito를 쓰겠다는 표시
class AdminInquiryServiceImplTest {

    @Mock // 가짜 매퍼
    private AdminInquiryMapper adminInquiryMapper;

    @InjectMocks // 위 가짜를 넣어서 진짜 Service를 만듦
    private AdminInquiryServiceImpl adminInquiryService;

    // ── F-17 작성 · 조회 ──

    @Test
    @DisplayName("작성: 로그인한 회원번호로 저장한다")
    void createSavesWithLoginMemberId() {
        InquiryCreateRequestDTO dto = new InquiryCreateRequestDTO();
        dto.setInquiryType("QUESTION");
        dto.setTitle("환급액 질문");
        dto.setContent("내용");

        adminInquiryService.createInquiry(7L, dto);

        verify(adminInquiryMapper).insertInquiry(7L, dto);
    }

    @Test
    @DisplayName("상세: 본인 문의면 그대로 돌려준다")
    void detailReturnsOwnInquiry() {
        AdminInquiryResponseDTO mine = new AdminInquiryResponseDTO();
        mine.setInquiryId(10L);
        when(adminInquiryMapper.selectInquiryById(10L, 7L)).thenReturn(mine);

        AdminInquiryResponseDTO result = adminInquiryService.getInquiryDetail(7L, 10L);

        assertThat(result.getInquiryId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("상세: 남의 문의(또는 없는 문의)면 404")
    void detailThrowsNotFoundForOthersInquiry() {
        // 매퍼 SQL이 MEMBER_ID 조건으로 거르므로 남의 문의는 null이 옴
        when(adminInquiryMapper.selectInquiryById(10L, 8L)).thenReturn(null);

        assertThatThrownBy(() -> adminInquiryService.getInquiryDetail(8L, 10L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("관리자 목록: 상태 조건을 매퍼에 그대로 넘긴다")
    void adminListPassesStatus() {
        when(adminInquiryMapper.selectAdminInquiryList("WAITING"))
                .thenReturn(List.of(new AdminInquiryResponseDTO()));

        assertThat(adminInquiryService.getAdminInquiryList("WAITING")).hasSize(1);
    }

    // ── F-18 확인 중 · 답변 등록 ──

    @Test
    @DisplayName("확인 중: 답변 없이도 상태를 바꾸고 로그인한 관리자 번호를 넘긴다")
    void checkingUpdatesWithoutAnswer() {
        InquiryAnswerRequestDTO dto = new InquiryAnswerRequestDTO("CHECKING", null);
        when(adminInquiryMapper.selectInquiryStatus(10L)).thenReturn("WAITING");
        when(adminInquiryMapper.updateInquiryAnswer(10L, dto, 1L)).thenReturn(1);

        adminInquiryService.updateInquiryAnswer(10L, dto, 1L);

        verify(adminInquiryMapper).updateInquiryAnswer(10L, dto, 1L);
    }

    @Test
    @DisplayName("답변 완료: 답변이 비어 있으면 400이고 DB를 건드리지 않는다")
    void answeredWithoutAnswerThrowsBadRequest() {
        InquiryAnswerRequestDTO dto = new InquiryAnswerRequestDTO("ANSWERED", "  ");

        assertThatThrownBy(() -> adminInquiryService.updateInquiryAnswer(10L, dto, 1L))
                .isInstanceOf(BadRequestException.class);
        verify(adminInquiryMapper, never()).updateInquiryAnswer(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("답변: 없는 문의면 404")
    void answerThrowsNotFoundWhenMissing() {
        InquiryAnswerRequestDTO dto = new InquiryAnswerRequestDTO("ANSWERED", "답변");
        when(adminInquiryMapper.selectInquiryStatus(99L)).thenReturn(null);

        assertThatThrownBy(() -> adminInquiryService.updateInquiryAnswer(99L, dto, 1L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("답변: 이미 답변 완료된 문의면 409")
    void answerThrowsConflictWhenAlreadyAnswered() {
        InquiryAnswerRequestDTO dto = new InquiryAnswerRequestDTO("CHECKING", null);
        when(adminInquiryMapper.selectInquiryStatus(10L)).thenReturn("ANSWERED");

        assertThatThrownBy(() -> adminInquiryService.updateInquiryAnswer(10L, dto, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(adminInquiryMapper, never()).updateInquiryAnswer(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("답변: 확인 뒤 다른 관리자가 먼저 답변해 바뀐 행이 0이면 409")
    void answerThrowsConflictWhenNoRowUpdated() {
        InquiryAnswerRequestDTO dto = new InquiryAnswerRequestDTO("ANSWERED", "답변");
        when(adminInquiryMapper.selectInquiryStatus(10L)).thenReturn("CHECKING");
        when(adminInquiryMapper.updateInquiryAnswer(10L, dto, 1L)).thenReturn(0);

        assertThatThrownBy(() -> adminInquiryService.updateInquiryAnswer(10L, dto, 1L))
                .isInstanceOf(BusinessException.class);
    }
}
