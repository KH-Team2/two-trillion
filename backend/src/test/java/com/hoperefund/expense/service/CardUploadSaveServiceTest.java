package com.hoperefund.expense.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hoperefund.expense.dto.CardUploadDTO;
import com.hoperefund.expense.dto.ExpenseDTO;
import com.hoperefund.expense.dto.ParsedExpenseDTO;
import com.hoperefund.expense.mapper.CardUploadMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * CardUploadSaveService 테스트 (DB 없이)
 *
 * <p>확인하는 것: 업로드 1행 → 지출은 처음에 전부 일반으로 INSERT → 일반이 아닌 행만 UPDATE
 *
 * <p>doAnswer: 가짜 mapper가 불렸을 때 할 일을 정함 (여기서는 진짜 DB처럼 새 번호를 채워 줌)
 *
 * <p>ArgumentCaptor: 가짜에게 넘어간 값을 붙잡아서 나중에 확인
 */
@ExtendWith(MockitoExtension.class)
class CardUploadSaveServiceTest {

    @Mock private CardUploadMapper cardUploadMapper;

    @InjectMocks private CardUploadSaveService cardUploadSaveService;

    // 분류 코드 → 번호
    private static final Map<String, Long> CATEGORY_IDS =
            Map.of("GENERAL", 1L, "TRANSIT", 2L, "MARKET", 3L, "CULTURE", 4L, "EXCLUDED", 5L);

    // 가짜 mapper에 INSERT된 지출을 순서대로 모아 둠
    private final List<ExpenseDTO> inserted = new ArrayList<>();

    @BeforeEach // 각 테스트 전에 실행
    void setUp() {
        // insertUpload가 불리면 업로드번호 100을 채워 줌 (selectKey 흉내)
        doAnswer(
                        call -> {
                            CardUploadDTO upload = call.getArgument(0);
                            upload.setUploadId(100L);
                            return null;
                        })
                .when(cardUploadMapper)
                .insertUpload(any());
        // insertExpense가 불리면 지출번호를 1000, 1001, ... 순서로 채워 줌
        doAnswer(
                        call -> {
                            ExpenseDTO expense = call.getArgument(0);
                            expense.setExpenseId(1000L + inserted.size());
                            inserted.add(expense);
                            return null;
                        })
                .when(cardUploadMapper)
                .insertExpense(any());
    }

    private static ParsedExpenseDTO row(String code, Long listId) {
        return ParsedExpenseDTO.builder()
                .usedDate(LocalDate.of(2026, 4, 20))
                .merchantName("가게")
                .payMethod("CREDIT")
                .amount(1000L)
                .categoryCode(code)
                .listId(listId)
                .build();
    }

    @Test
    void 업로드_1행을_파일명_건수와_함께_저장하고_번호를_돌려준다() {
        long uploadId =
                cardUploadSaveService.save(
                        7L,
                        "a.csv",
                        List.of(row("GENERAL", null), row("GENERAL", null)),
                        CATEGORY_IDS);

        ArgumentCaptor<CardUploadDTO> captor = ArgumentCaptor.forClass(CardUploadDTO.class);
        verify(cardUploadMapper).insertUpload(captor.capture());
        assertThat(captor.getValue().getMemberId()).isEqualTo(7L);
        assertThat(captor.getValue().getFileName()).isEqualTo("a.csv");
        assertThat(captor.getValue().getRowCount()).isEqualTo(2);
        assertThat(uploadId).isEqualTo(100L);
    }

    @Test
    void 지출은_처음에_전부_일반으로_넣는다() {
        cardUploadSaveService.save(7L, "a.csv", List.of(row("TRANSIT", 9L)), CATEGORY_IDS);

        assertThat(inserted).hasSize(1);
        assertThat(inserted.get(0).getCategoryId()).isEqualTo(1L); // GENERAL
        assertThat(inserted.get(0).getListId()).isNull();
        assertThat(inserted.get(0).getUploadId()).isEqualTo(100L);
        assertThat(inserted.get(0).getMemberId()).isEqualTo(7L);
    }

    @Test
    void 일반이_아닌_행만_분류_결과로_UPDATE한다() {
        cardUploadSaveService.save(
                7L,
                "a.csv",
                List.of(row("GENERAL", null), row("TRANSIT", 9L), row("CULTURE", 11L)),
                CATEGORY_IDS);

        verify(cardUploadMapper, times(2)).updateCategory(anyLong(), anyLong(), any());
        verify(cardUploadMapper).updateCategory(1001L, 2L, 9L); // 두 번째 행 → 대중교통
        verify(cardUploadMapper).updateCategory(1002L, 4L, 11L); // 세 번째 행 → 문화체육
        verify(cardUploadMapper, never()).updateCategory(1000L, 1L, null); // 일반은 UPDATE 안 함
    }

    @Test
    void 모르는_분류_코드는_일반_그대로_둔다() {
        cardUploadSaveService.save(7L, "a.csv", List.of(row("UNKNOWN", 9L)), CATEGORY_IDS);

        verify(cardUploadMapper, never()).updateCategory(anyLong(), anyLong(), any());
    }
}
