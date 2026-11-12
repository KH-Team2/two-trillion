package com.hoperefund.expense.service;

import com.hoperefund.expense.dto.CardUploadDTO;
import com.hoperefund.expense.dto.ExpenseDTO;
import com.hoperefund.expense.dto.ParsedExpenseDTO;
import com.hoperefund.expense.mapper.CardUploadMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 업로드 저장만 따로 맡는 Service (DB 저장을 한 트랜잭션으로 묶으려고 분리)
 *
 * <p>왜 따로 만들었나: 계산 서버 호출은 트랜잭션 「밖」에서, 저장만 트랜잭션 「안」에서 하라는 팀 규칙 때문.
 * 같은 클래스 안에서 @Transactional 메서드를 부르면 스프링이 트랜잭션을 걸어 주지 못해서, 저장 부분을 다른 클래스로 뺐다.
 *
 * <p>트랜잭션: 안의 DB 작업을 한 묶음으로 처리. 중간에 하나라도 실패하면 이미 넣은 행까지 전부 취소(롤백)
 * → 업로드 1행만 남고 지출은 반만 저장되는 일이 없음
 */
@Service
@RequiredArgsConstructor
public class CardUploadSaveService {

    static final String GENERAL = "GENERAL";

    private final CardUploadMapper cardUploadMapper;

    /**
     * CARD_UPLOAD 1행 + EXPENSE N행 저장 → 분류 결과로 UPDATE
     *
     * <p>순서 (기능명세서 F-02-02 ③④ · F-02-03 ③ · 팀 결정 11/09 「명세서대로 저장 후 UPDATE」)
     *
     * <ol>
     *   <li>업로드 1행 저장
     *   <li>지출 N행을 처음엔 전부 일반(GENERAL)으로 저장
     *   <li>일반이 아닌 행만 분류 결과(CATEGORY_ID · LIST_ID)로 UPDATE
     * </ol>
     *
     * @param categoryIds 분류 코드 → 분류 번호 (예: "TRANSIT" → 2)
     * @return 새 업로드번호
     */
    @Transactional
    public long save(
            long memberId,
            String fileName,
            List<ParsedExpenseDTO> rows,
            Map<String, Long> categoryIds) {
        // 1. 업로드 1행
        CardUploadDTO upload =
                CardUploadDTO.builder()
                        .memberId(memberId)
                        .fileName(fileName)
                        .rowCount(rows.size())
                        .build();
        cardUploadMapper.insertUpload(upload); // 저장 후 upload.uploadId에 새 번호가 들어 있음
        long uploadId = upload.getUploadId();
        long generalId = categoryIds.get(GENERAL);

        for (ParsedExpenseDTO row : rows) {
            // 2. 지출 1행 · 처음엔 일반 · LIST_ID 없음
            ExpenseDTO expense =
                    ExpenseDTO.builder()
                            .memberId(memberId)
                            .uploadId(uploadId)
                            .usedDate(row.getUsedDate())
                            .merchantName(row.getMerchantName()) // 정제 전 원본 그대로
                            .payMethod(row.getPayMethod())
                            .amount(row.getAmount())
                            .categoryId(generalId)
                            .build();
            cardUploadMapper.insertExpense(expense); // 저장 후 expense.expenseId에 새 번호

            // 3. 분류 결과가 일반이 아니면 UPDATE
            //    모르는 분류 코드(예: 사용 중지된 분류)면 일반 그대로 둠
            Long categoryId = categoryIds.get(row.getCategoryCode());
            if (categoryId != null && categoryId != generalId) {
                cardUploadMapper.updateCategory(
                        expense.getExpenseId(), categoryId, row.getListId());
            }
        }
        return uploadId;
    }
}
