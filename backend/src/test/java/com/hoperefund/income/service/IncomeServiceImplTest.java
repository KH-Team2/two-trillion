package com.hoperefund.income.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hoperefund.common.exception.BadRequestException;
import com.hoperefund.common.exception.ConflictException;
import com.hoperefund.common.exception.NoCriteriaException;
import com.hoperefund.common.exception.NotFoundException;
import com.hoperefund.income.dto.IncomeRequestDTO;
import com.hoperefund.income.dto.IncomeResponseDTO;
import com.hoperefund.income.mapper.IncomeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/**
 * IncomeServiceImpl 테스트 (DB 없이)
 *
 * <p>Mockito: 진짜 IncomeMapper 대신 "가짜(mock)"를 넣어서, DB 없이 Service의 검사 · 흐름만 확인한다.
 *
 * <p>- when(가짜.메서드(...)).thenReturn(값) : 가짜가 이 값을 돌려주게 정함
 *
 * <p>- verify(가짜).메서드(...) : 그 메서드가 불렸는지 확인
 *
 * <p>[수정 11/12] 테스트 메서드 이름을 영문으로 · 이유: IncomeControllerTest와 같은 팀 테스트 이름 규칙에 맞춤
 * → 원래 한글 설명은 @DisplayName에 그대로 남김 (테스트 결과 화면에 이 글자가 보임)
 */
@ExtendWith(MockitoExtension.class) // JUnit에서 Mockito를 쓰겠다는 표시
class IncomeServiceImplTest {

    @Mock // 가짜 IncomeMapper
    private IncomeMapper incomeMapper;

    @InjectMocks // 위 가짜를 넣어서 진짜 IncomeServiceImpl을 만듦
    private IncomeServiceImpl incomeService;

    // [수정 11/12] 추가 · 이유: 연도 검사가 DB 기준값 확인(countCriteria)으로 바뀌어서
    //   기본은 「기준값 있음(1)」으로 정해 둠 · 기준값 없는 연도 테스트에서만 0으로 바꿈
    //   lenient(): 이 가짜 응답을 안 쓰는 테스트가 있어도 오류로 보지 않게 (Mockito는 안 쓴 설정을 오류로 봄)
    @BeforeEach // 각 테스트 전에 실행
    void setUpCriteriaExists() {
        lenient().when(incomeMapper.countCriteria(anyInt())).thenReturn(1);
    }

    // ── 조회 ──

    @Test
    @DisplayName("조회: 소득이 있으면 그대로 돌려준다")
    void getReturnsSavedIncome() {
        IncomeResponseDTO saved = new IncomeResponseDTO();
        saved.setTotalSalary(30_000_000L);
        when(incomeMapper.findByMemberAndYear(1L, 2026)).thenReturn(saved);

        IncomeResponseDTO result = incomeService.getIncome(1L, 2026);

        assertThat(result.getTotalSalary()).isEqualTo(30_000_000L);
    }

    @Test
    @DisplayName("조회: 소득이 없으면 404")
    void getThrowsNotFoundWhenIncomeMissing() {
        when(incomeMapper.findByMemberAndYear(1L, 2026)).thenReturn(null);

        assertThatThrownBy(() -> incomeService.getIncome(1L, 2026))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("조회: 기준값 없는 연도면 400이고 소득을 조회하지 않는다")
    void getThrowsNoCriteriaAndSkipsQueryWhenNoCriteria() {
        when(incomeMapper.countCriteria(2024)).thenReturn(0); // [수정 11/12] 2024 기준값 없음
        assertThatThrownBy(() -> incomeService.getIncome(1L, 2024))
                .isInstanceOf(NoCriteriaException.class);
        verify(incomeMapper, never()).findByMemberAndYear(anyLong(), anyInt());
    }

    // [수정 11/12] 추가: 말이 안 되는 연도는 DB(기준값)도 조회하지 않고 바로 400
    @Test
    @DisplayName("조회: 범위 밖 연도면 400이고 기준값도 조회하지 않는다")
    void getThrowsNoCriteriaWithoutDbWhenYearOutOfRange() {
        assertThatThrownBy(() -> incomeService.getIncome(1L, 99999))
                .isInstanceOf(NoCriteriaException.class);
        verify(incomeMapper, never()).countCriteria(anyInt());
    }

    // ── 저장 ──

    @Test
    @DisplayName("저장: 정상이면 MERGE 후 다시 읽어서 돌려준다")
    void saveUpsertsThenReturnsReloadedIncome() {
        IncomeRequestDTO request = request(2, 1);
        IncomeResponseDTO saved = new IncomeResponseDTO();
        when(incomeMapper.findByMemberAndYear(1L, 2026)).thenReturn(saved);

        IncomeResponseDTO result = incomeService.saveIncome(1L, 2026, request);

        verify(incomeMapper).upsert(1L, 2026, request);
        assertThat(result).isSameAs(saved);
    }

    @Test
    @DisplayName("저장: 자녀 수가 부양가족 수와 같으면 통과")
    void saveAllowsChildrenEqualToDependents() {
        incomeService.saveIncome(1L, 2025, request(2, 2));

        verify(incomeMapper).upsert(anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("저장: 자녀 수가 부양가족 수보다 많으면 400이고 저장하지 않는다")
    void saveThrowsBadRequestWhenChildrenExceedDependents() {
        assertThatThrownBy(() -> incomeService.saveIncome(1L, 2026, request(1, 2)))
                .isInstanceOf(BadRequestException.class);
        verify(incomeMapper, never()).upsert(anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("저장: 자녀 수만 있고 부양가족 수가 비어 있으면 400")
    void saveThrowsBadRequestWhenChildrenWithoutDependents() {
        // 비워 보낸 부양가족 수는 0으로 저장되니까, 자녀 1명은 0명보다 많음
        assertThatThrownBy(() -> incomeService.saveIncome(1L, 2026, request(null, 1)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("저장: 둘 다 비어 있으면 통과")
    void saveAllowsBothEmpty() {
        incomeService.saveIncome(1L, 2026, request(null, null));

        verify(incomeMapper).upsert(anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("저장: 기준값 없는 연도면 400이고 저장하지 않는다")
    void saveThrowsNoCriteriaAndSkipsUpsertWhenNoCriteria() {
        when(incomeMapper.countCriteria(2027)).thenReturn(0); // [수정 11/12] 2027 기준값 없음
        assertThatThrownBy(() -> incomeService.saveIncome(1L, 2027, request(0, 0)))
                .isInstanceOf(NoCriteriaException.class);
        verify(incomeMapper, never()).upsert(anyLong(), anyInt(), any());
    }

    @Test
    @DisplayName("저장: 동시에 첫 저장이 겹치면 ConflictException(409)")
    void saveThrowsConflictOnConcurrentFirstSave() {
        // 가짜 매퍼가 UNIQUE 위반(DuplicateKeyException)을 던지게 정함
        when(incomeMapper.upsert(anyLong(), anyInt(), any()))
                .thenThrow(new DuplicateKeyException("UQ_INCOME_MEMBER_YEAR"));

        assertThatThrownBy(() -> incomeService.saveIncome(1L, 2026, request(0, 0)))
                .isInstanceOf(ConflictException.class);
    }

    /** 테스트용 요청 (필수 칸 + 부양가족 · 자녀 수) */
    private IncomeRequestDTO request(Integer dependents, Integer children) {
        IncomeRequestDTO request = new IncomeRequestDTO();
        request.setTotalSalary(30_000_000L);
        request.setWithheldTax(1_000_000L);
        request.setDependents(dependents);
        request.setChildren(children);
        return request;
    }
}
