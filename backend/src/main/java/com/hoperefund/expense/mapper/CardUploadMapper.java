package com.hoperefund.expense.mapper;

import com.hoperefund.expense.dto.CardUploadDTO;
import com.hoperefund.expense.dto.ExpenseDTO;
import com.hoperefund.expense.dto.ExpenseGroupCountDTO;
import com.hoperefund.expense.dto.SpendCategoryDTO;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 카드 업로드 저장용 SQL 목록 (실제 SQL은 resources/mapper/expense/CardUploadMapper.xml)
 *
 * <p>@Mapper: MyBatis가 이 인터페이스의 구현을 자동으로 만들어 줌
 *
 * <p>@Param("이름"): XML에서 #{이름}으로 꺼내 쓸 값의 이름
 */
@Mapper
public interface CardUploadMapper {

    /** 지출 분류 5개 전체 (분류 코드 → 분류 번호로 바꿀 때 씀) */
    List<SpendCategoryDTO> findCategories();

    /** 중복 확인: 기간 안의 내 거래를 (사용일 · 가맹점명 · 금액 · 결제수단)으로 묶어 센 결과 */
    List<ExpenseGroupCountDTO> countExpenseGroups(
            @Param("memberId") long memberId,
            @Param("minDate") LocalDate minDate,
            @Param("maxDate") LocalDate maxDate);

    /** CARD_UPLOAD 1행 저장 · 저장 후 upload.uploadId에 새 번호가 채워짐 */
    void insertUpload(CardUploadDTO upload);

    /** EXPENSE 1행 저장 · 저장 후 expense.expenseId에 새 번호가 채워짐 */
    void insertExpense(ExpenseDTO expense);

    /** 저장한 지출 1행의 분류 결과 바꾸기 (처음엔 일반으로 저장 → 분류 결과로 UPDATE) */
    void updateCategory(
            @Param("expenseId") long expenseId,
            @Param("categoryId") long categoryId,
            @Param("listId") Long listId);

    /** 업로드 1행 다시 읽기 (DB가 채운 업로드일 확인용) · 본인 것만 */
    CardUploadDTO findUpload(@Param("uploadId") long uploadId, @Param("memberId") long memberId);
}
