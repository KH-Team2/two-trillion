package com.hoperefund.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hoperefund.admin.dto.AdminInquiryResponseDto;
import com.hoperefund.admin.dto.InquiryCreateRequestDto;
import com.hoperefund.admin.mapper.AdminInquiryMapper;
import com.hoperefund.common.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
        InquiryCreateRequestDto dto = new InquiryCreateRequestDto();
        dto.setInquiryType("QUESTION");
        dto.setTitle("환급액 질문");
        dto.setContent("내용");

        adminInquiryService.createInquiry(7L, dto);

        verify(adminInquiryMapper).insertInquiry(7L, dto);
    }

    @Test
    @DisplayName("상세: 본인 문의면 그대로 돌려준다")
    void detailReturnsOwnInquiry() {
        AdminInquiryResponseDto mine = new AdminInquiryResponseDto();
        mine.setInquiryId(10L);
        when(adminInquiryMapper.selectInquiryById(10L, 7L)).thenReturn(mine);

        AdminInquiryResponseDto result = adminInquiryService.getInquiryDetail(7L, 10L);

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
}
