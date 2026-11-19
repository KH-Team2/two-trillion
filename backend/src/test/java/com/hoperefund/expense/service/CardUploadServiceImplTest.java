package com.hoperefund.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hoperefund.expense.client.CardParseClient;
import com.hoperefund.expense.dto.CardParseResultDTO;
import com.hoperefund.expense.dto.CardUploadResponseDTO;
import com.hoperefund.expense.dto.CategoryCountDTO;
import com.hoperefund.expense.dto.ExpenseGroupCountDTO;
import com.hoperefund.expense.dto.ParsedExpenseDTO;
import com.hoperefund.expense.dto.SpendCategoryDTO;
import com.hoperefund.expense.exception.CardUploadException;
import com.hoperefund.expense.mapper.CardUploadMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * CardUploadServiceImpl 테스트 (DB · 계산 서버 없이)
 *
 * <p>Mockito: 계산 서버 호출(CardParseClient) · 저장(CardUploadSaveService) · SQL(CardUploadMapper)을 전부
 * 가짜(mock)로 바꿔서, Service의 확인 · 중복 계산 · 응답 만들기만 본다.
 *
 * <p>MockMultipartFile: 테스트용 가짜 업로드 파일 (이름 · 파일명 · 형식 · 내용)
 */
@ExtendWith(MockitoExtension.class)
class CardUploadServiceImplTest {

    @Mock private CardParseClient cardParseClient;
    @Mock private CardUploadSaveService cardUploadSaveService;
    @Mock private CardUploadMapper cardUploadMapper;

    @InjectMocks private CardUploadServiceImpl cardUploadService;

    private static final long MEMBER = 1L;
    private static final LocalDate APR_20 = LocalDate.of(2026, 4, 20);

    /** 테스트용 업로드 파일 (내용은 아무거나 · 실제 해석은 가짜 계산 서버가 함) */
    private static MockMultipartFile file(String fileName) {
        return new MockMultipartFile("file", fileName, "text/csv", "아무 내용".getBytes());
    }

    /** 계산 서버가 돌려줄 거래 1건 */
    private static ParsedExpenseDTO row(
            LocalDate date, String merchant, long amount, String code, Long listId) {
        return ParsedExpenseDTO.builder()
                .usedDate(date)
                .merchantName(merchant)
                .payMethod("CREDIT")
                .amount(amount)
                .categoryCode(code)
                .listId(listId)
                .build();
    }

    /** 분류표 5개 (번호 1~5) */
    private static List<SpendCategoryDTO> categories() {
        return List.of(
                new SpendCategoryDTO(1L, "GENERAL", "일반", "Y"),
                new SpendCategoryDTO(2L, "TRANSIT", "대중교통", "Y"),
                new SpendCategoryDTO(3L, "MARKET", "전통시장", "Y"),
                new SpendCategoryDTO(4L, "CULTURE", "문화체육", "Y"),
                new SpendCategoryDTO(5L, "EXCLUDED", "공제 대상 아님", "Y"));
    }

    // ── 1. 파일 확인 ──

    @Test
    void 지원하지_않는_확장자면_UNSUPPORTED_FORMAT() {
        assertThatThrownBy(() -> cardUploadService.upload(MEMBER, file("내역.pdf"), null, false))
                .isInstanceOf(CardUploadException.class)
                .extracting("code")
                .isEqualTo("UNSUPPORTED_FORMAT");
        verify(cardParseClient, never()).parse(any(), anyString(), any()); // 계산 서버는 안 부름
    }

    @Test
    void 빈_파일이면_VALIDATION() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.csv", "text/csv", new byte[0]);
        assertThatThrownBy(() -> cardUploadService.upload(MEMBER, empty, null, false))
                .extracting("code")
                .isEqualTo("VALIDATION");
    }

    @Test
    void 결제수단으로_CASH를_고르면_VALIDATION() {
        // 사용자가 고를 수 있는 건 신용 · 체크만 (현금영수증은 홈택스 파일이면 자동)
        assertThatThrownBy(() -> cardUploadService.upload(MEMBER, file("a.csv"), "CASH", false))
                .extracting("code")
                .isEqualTo("VALIDATION");
    }

    @Test
    void 대문자_확장자도_받는다() {
        when(cardParseClient.parse(any(), eq("내역.XLSX"), any()))
                .thenReturn(
                        new CardParseResultDTO(
                                1, List.of(row(APR_20, "가게", 1000, "GENERAL", null))));
        when(cardUploadMapper.findCategories()).thenReturn(categories());
        when(cardUploadSaveService.save(anyLong(), anyString(), any(), anyMap())).thenReturn(10L);

        CardUploadResponseDTO result =
                cardUploadService.upload(MEMBER, file("내역.XLSX"), null, false);

        assertThat(result.getUploadId()).isEqualTo(10L);
    }

    @Test
    void 거래가_0건이면_VALIDATION() {
        when(cardParseClient.parse(any(), anyString(), any()))
                .thenReturn(new CardParseResultDTO(0, List.of()));
        assertThatThrownBy(() -> cardUploadService.upload(MEMBER, file("a.csv"), null, false))
                .extracting("code")
                .isEqualTo("VALIDATION");
    }

    // ── 2. 중복 확인 ──

    @Test
    void 겹치는_결제가_있으면_409_DUPLICATE_WARNING이고_저장하지_않는다() {
        when(cardParseClient.parse(any(), anyString(), any()))
                .thenReturn(
                        new CardParseResultDTO(
                                1, List.of(row(APR_20, "세븐일레븐", 3200, "GENERAL", null))));
        when(cardUploadMapper.countExpenseGroups(MEMBER, APR_20, APR_20))
                .thenReturn(List.of(new ExpenseGroupCountDTO(APR_20, "세븐일레븐", 3200L, "CREDIT", 1)));

        assertThatThrownBy(() -> cardUploadService.upload(MEMBER, file("a.csv"), null, false))
                .isInstanceOf(CardUploadException.class)
                .hasMessage("이미 올린 내역과 같은 결제가 1건 있어요")
                .extracting("status", "code")
                .containsExactly(409, "DUPLICATE_WARNING");
        verify(cardUploadSaveService, never()).save(anyLong(), anyString(), any(), anyMap());
    }

    @Test
    void 계속을_누르면_중복_확인을_건너뛰고_저장한다() {
        when(cardParseClient.parse(any(), anyString(), any()))
                .thenReturn(
                        new CardParseResultDTO(
                                1, List.of(row(APR_20, "세븐일레븐", 3200, "GENERAL", null))));
        when(cardUploadMapper.findCategories()).thenReturn(categories());
        when(cardUploadSaveService.save(anyLong(), anyString(), any(), anyMap())).thenReturn(10L);

        cardUploadService.upload(MEMBER, file("a.csv"), null, true);

        verify(cardUploadMapper, never()).countExpenseGroups(anyLong(), any(), any());
    }

    @Test
    void 겹침_건수는_같은_결제끼리_기존과_새_건수_중_작은_값의_합() {
        // 새 파일: 세븐일레븐 3,200원 2건 + 교보문고 1건
        // 기존: 세븐일레븐 3,200원 1건 + 교보문고 3건
        // → min(1, 2) + min(3, 1) = 2
        LocalDate apr21 = APR_20.plusDays(1);
        List<ParsedExpenseDTO> rows =
                List.of(
                        row(APR_20, "세븐일레븐", 3200, "GENERAL", null),
                        row(APR_20, "세븐일레븐", 3200, "GENERAL", null),
                        row(apr21, "교보문고", 18500, "CULTURE", 7L));
        when(cardUploadMapper.countExpenseGroups(MEMBER, APR_20, apr21))
                .thenReturn(
                        List.of(
                                new ExpenseGroupCountDTO(APR_20, "세븐일레븐", 3200L, "CREDIT", 1),
                                new ExpenseGroupCountDTO(apr21, "교보문고", 18500L, "CREDIT", 3)));

        assertThat(cardUploadService.countDuplicates(MEMBER, rows)).isEqualTo(2);
    }

    @Test
    void 금액이_다르면_겹침이_아니다() {
        List<ParsedExpenseDTO> rows = List.of(row(APR_20, "세븐일레븐", 3200, "GENERAL", null));
        when(cardUploadMapper.countExpenseGroups(MEMBER, APR_20, APR_20))
                .thenReturn(List.of(new ExpenseGroupCountDTO(APR_20, "세븐일레븐", 4800L, "CREDIT", 1)));

        assertThat(cardUploadService.countDuplicates(MEMBER, rows)).isZero();
    }

    // ── 3. 저장 · 응답 ──

    @Test
    void 정상_업로드면_분류별_건수를_분류표_순서로_돌려준다() {
        when(cardParseClient.parse(any(), anyString(), eq("CHECK")))
                .thenReturn(
                        new CardParseResultDTO(
                                3,
                                List.of(
                                        row(APR_20, "서울교통공사", 1550, "TRANSIT", 1L),
                                        row(APR_20, "GS25", 4800, "GENERAL", null),
                                        row(APR_20, "티머니", 1450, "TRANSIT", 2L))));
        when(cardUploadMapper.countExpenseGroups(anyLong(), any(), any())).thenReturn(List.of());
        when(cardUploadMapper.findCategories()).thenReturn(categories());
        when(cardUploadSaveService.save(eq(MEMBER), eq("a.csv"), any(), anyMap())).thenReturn(10L);

        CardUploadResponseDTO result =
                cardUploadService.upload(MEMBER, file("a.csv"), "CHECK", false);

        assertThat(result.getRowCount()).isEqualTo(3);
        assertThat(result.getCategoryCounts())
                .containsExactly(
                        new CategoryCountDTO("GENERAL", "일반", 1),
                        new CategoryCountDTO("TRANSIT", "대중교통", 2));
    }

    @Test
    void 분류표에_없는_코드는_일반_건수에_더한다() {
        when(cardParseClient.parse(any(), anyString(), any()))
                .thenReturn(
                        new CardParseResultDTO(
                                1, List.of(row(APR_20, "가게", 1000, "UNKNOWN", null))));
        when(cardUploadMapper.countExpenseGroups(anyLong(), any(), any())).thenReturn(List.of());
        when(cardUploadMapper.findCategories()).thenReturn(categories());
        when(cardUploadSaveService.save(anyLong(), anyString(), any(), anyMap())).thenReturn(10L);

        CardUploadResponseDTO result = cardUploadService.upload(MEMBER, file("a.csv"), null, false);

        assertThat(result.getCategoryCounts())
                .containsExactly(new CategoryCountDTO("GENERAL", "일반", 1));
    }
}
