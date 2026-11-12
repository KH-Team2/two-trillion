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
    void 기준값이_있는_연도로_설정() {
        lenient().when(incomeMapper.countCriteria(anyInt())).thenReturn(1);
    }

    // ── 조회 ──

    @Test
    void 조회_소득이_있으면_그대로_돌려준다() {
        IncomeResponseDTO saved = new IncomeResponseDTO();
        saved.setTotalSalary(30_000_000L);
        when(incomeMapper.findByMemberAndYear(1L, 2026)).thenReturn(saved);

        IncomeResponseDTO result = incomeService.getIncome(1L, 2026);

        assertThat(result.getTotalSalary()).isEqualTo(30_000_000L);
    }

    @Test
    void 조회_소득이_없으면_404() {
        when(incomeMapper.findByMemberAndYear(1L, 2026)).thenReturn(null);

        assertThatThrownBy(() -> incomeService.getIncome(1L, 2026))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void 조회_받지_않는_연도면_400이고_DB를_조회하지_않는다() {
        when(incomeMapper.countCriteria(2024)).thenReturn(0); // [수정 11/12] 2024 기준값 없음
        assertThatThrownBy(() -> incomeService.getIncome(1L, 2024))
                .isInstanceOf(NoCriteriaException.class);
        verify(incomeMapper, never()).findByMemberAndYear(anyLong(), anyInt());
    }

    // [수정 11/12] 추가: 말이 안 되는 연도는 DB(기준값)도 조회하지 않고 바로 400
    @Test
    void 조회_범위_밖_연도면_400이고_기준값도_조회하지_않는다() {
        assertThatThrownBy(() -> incomeService.getIncome(1L, 99999))
                .isInstanceOf(NoCriteriaException.class);
        verify(incomeMapper, never()).countCriteria(anyInt());
    }

    // ── 저장 ──

    @Test
    void 저장_정상이면_MERGE_후_다시_읽어서_돌려준다() {
        IncomeRequestDTO request = request(2, 1);
        IncomeResponseDTO saved = new IncomeResponseDTO();
        when(incomeMapper.findByMemberAndYear(1L, 2026)).thenReturn(saved);

        IncomeResponseDTO result = incomeService.saveIncome(1L, 2026, request);

        verify(incomeMapper).upsert(1L, 2026, request);
        assertThat(result).isSameAs(saved);
    }

    @Test
    void 저장_자녀_수가_부양가족_수와_같으면_통과() {
        incomeService.saveIncome(1L, 2025, request(2, 2));

        verify(incomeMapper).upsert(anyLong(), anyInt(), any());
    }

    @Test
    void 저장_자녀_수가_부양가족_수보다_많으면_400이고_저장하지_않는다() {
        assertThatThrownBy(() -> incomeService.saveIncome(1L, 2026, request(1, 2)))
                .isInstanceOf(BadRequestException.class);
        verify(incomeMapper, never()).upsert(anyLong(), anyInt(), any());
    }

    @Test
    void 저장_자녀_수만_있고_부양가족_수가_비어_있으면_400() {
        // 비워 보낸 부양가족 수는 0으로 저장되니까, 자녀 1명은 0명보다 많음
        assertThatThrownBy(() -> incomeService.saveIncome(1L, 2026, request(null, 1)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void 저장_둘_다_비어_있으면_통과() {
        incomeService.saveIncome(1L, 2026, request(null, null));

        verify(incomeMapper).upsert(anyLong(), anyInt(), any());
    }

    @Test
    void 저장_받지_않는_연도면_400이고_저장하지_않는다() {
        when(incomeMapper.countCriteria(2027)).thenReturn(0); // [수정 11/12] 2027 기준값 없음
        assertThatThrownBy(() -> incomeService.saveIncome(1L, 2027, request(0, 0)))
                .isInstanceOf(NoCriteriaException.class);
        verify(incomeMapper, never()).upsert(anyLong(), anyInt(), any());
    }

    @Test
    void 저장_동시에_첫_저장이_겹치면_ConflictException() {
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
