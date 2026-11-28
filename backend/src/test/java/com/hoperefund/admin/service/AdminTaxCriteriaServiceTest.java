package com.hoperefund.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hoperefund.admin.dto.TaxCriteriaRequestDTO;
import com.hoperefund.admin.dto.TaxCriteriaResponseDTO;
import com.hoperefund.admin.dto.TaxCriteriaYearCopyRequestDTO;
import com.hoperefund.admin.mapper.AdminTaxCriteriaMapper;
import com.hoperefund.common.exception.BadRequestException;
import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.common.exception.NotFoundException;
import com.hoperefund.common.mapper.AccessLogMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/**
 * AdminTaxCriteriaService 테스트 (DB 없이 가짜 매퍼로) · [신규 11/26 고은]
 *
 * <p>확인하는 것: 최근 연도 고르기 · 수정할 때 관리자 번호를 넘기는지(트리거 이력용) · 없는 번호 404 · DB에 가기 전 값 검사 ·
 * DB 오류를 400/409로 바꾸기 · 연도 복사 규칙 · ACCESS_LOG 기록
 */
@ExtendWith(MockitoExtension.class)
class AdminTaxCriteriaServiceTest {

    private static final Long ADMIN_ID = 1001L;
    private static final String IP = "127.0.0.1";

    @Mock private AdminTaxCriteriaMapper mapper;
    @Mock private AccessLogMapper accessLogMapper;
    @InjectMocks private AdminTaxCriteriaService service;

    // 공제 항목 수정 요청 하나 만들기 (총급여 0 초과 ~ 7천만 이하 · 공제율 40%)
    private static TaxCriteriaRequestDTO deduction(Long id) {
        TaxCriteriaRequestDTO dto = new TaxCriteriaRequestDTO();
        dto.setTargetType("DEDUCTION_TYPE");
        dto.setId(id);
        dto.setName("전통시장 사용분");
        dto.setRate(new BigDecimal("40"));
        dto.setSalaryMin(0L);
        dto.setSalaryMax(70_000_000L);
        dto.setSourceNote("조특법 §126의2");
        return dto;
    }

    @Test
    @DisplayName("조회: 연도를 비우면 기준값이 있는 가장 최근 연도를 보여 준다")
    void emptyYearUsesLatestYear() {
        when(mapper.selectYears()).thenReturn(List.of(2026, 2025));

        TaxCriteriaResponseDTO res = service.getTaxCriteria(null);

        assertThat(res.getTaxYear()).isEqualTo(2026);
        assertThat(res.getYears()).containsExactly(2026, 2025);
        verify(mapper).selectDeductionTypes(2026);
    }

    @Test
    @DisplayName("수정: 관리자 번호를 같이 넘기고(UPDATED_BY → 트리거 이력) 접근 기록을 남긴다")
    void updatePassesAdminAndWritesLog() {
        TaxCriteriaRequestDTO dto = deduction(12L);
        when(mapper.updateDeductionType(dto, ADMIN_ID)).thenReturn(1);

        service.updateTaxCriteria(dto, ADMIN_ID, IP);

        verify(mapper).updateDeductionType(dto, ADMIN_ID);
        verify(accessLogMapper)
                .insertAccessLog(
                        ADMIN_ID, "/api/admin/tax-criteria/DEDUCTION_TYPE/12", IP, "SUCCESS");
    }

    @Test
    @DisplayName("수정: 없는 번호면 404 · 기록 안 남김")
    void updateMissingRowIsNotFound() {
        TaxCriteriaRequestDTO dto = deduction(999L);
        when(mapper.updateDeductionType(dto, ADMIN_ID)).thenReturn(0);

        assertThatThrownBy(() -> service.updateTaxCriteria(dto, ADMIN_ID, IP))
                .isInstanceOf(NotFoundException.class);
        verify(accessLogMapper, never()).insertAccessLog(any(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("수정: 총급여 구간 끝 ≤ 시작이면 DB에 가기 전에 400")
    void salaryRangeCheckedBeforeDb() {
        TaxCriteriaRequestDTO dto = deduction(12L);
        dto.setSalaryMin(70_000_000L);
        dto.setSalaryMax(70_000_000L);

        assertThatThrownBy(() -> service.updateTaxCriteria(dto, ADMIN_ID, IP))
                .isInstanceOf(BadRequestException.class);
        verify(mapper, never()).updateDeductionType(any(), anyLong());
    }

    @Test
    @DisplayName("수정: DB 값 제한 위반(ORA-02290)은 400 「입력값을 확인해 주세요」")
    void checkConstraintBecomesBadRequest() {
        TaxCriteriaRequestDTO dto = deduction(12L);
        when(mapper.updateDeductionType(dto, ADMIN_ID))
                .thenThrow(new DataIntegrityViolationException("ORA-02290"));

        assertThatThrownBy(() -> service.updateTaxCriteria(dto, ADMIN_ID, IP))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("입력값을 확인해 주세요");
    }

    @Test
    @DisplayName("혜택 조건: 최소 나이 > 최대 나이면 400")
    void benefitAgeRangeChecked() {
        TaxCriteriaRequestDTO dto = new TaxCriteriaRequestDTO();
        dto.setTargetType("BENEFIT_CONDITION");
        dto.setId(3L);
        dto.setName("중소기업 취업자 소득세 감면");
        dto.setHomelessHeadReqYn("N");
        dto.setMinAge(34);
        dto.setMaxAge(15);
        dto.setSourceNote("조특법 §30");

        assertThatThrownBy(() -> service.updateTaxCriteria(dto, ADMIN_ID, IP))
                .isInstanceOf(BadRequestException.class);
        verify(mapper, never()).updateBenefitCondition(any(), anyLong());
    }

    @Test
    @DisplayName("행 추가: 같은 연도 · 코드 · 구간 시작이 있으면(ORA-00001) 409 DUPLICATE_CRITERIA")
    void duplicateInsertIsConflict() {
        TaxCriteriaRequestDTO dto = deduction(null);
        dto.setTaxYear(2026);
        dto.setCode("PENSION");
        dto.setDeductionKind("TAX_CREDIT");
        when(mapper.insertDeductionType(dto, ADMIN_ID))
                .thenThrow(new DuplicateKeyException("ORA-00001"));

        assertThatThrownBy(() -> service.createTaxCriteria(dto, ADMIN_ID, IP))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "DUPLICATE_CRITERIA");
    }

    @Test
    @DisplayName("행 추가: 카드 공제율(CARD) 행에 분류가 없으면 400")
    void cardRowNeedsCategory() {
        TaxCriteriaRequestDTO dto = deduction(null);
        dto.setTaxYear(2026);
        dto.setCode("CARD_MARKET");
        dto.setDeductionKind("CARD");

        assertThatThrownBy(() -> service.createTaxCriteria(dto, ADMIN_ID, IP))
                .isInstanceOf(BadRequestException.class);
        verify(mapper, never()).insertDeductionType(any(), anyLong());
    }

    @Test
    @DisplayName("연도 복사: 새 연도에 기준값이 이미 있으면 409 · 복사 안 함")
    void copyYearRejectsExistingTarget() {
        TaxCriteriaYearCopyRequestDTO dto = new TaxCriteriaYearCopyRequestDTO();
        dto.setSourceYear(2026);
        dto.setTargetYear(2025);
        when(mapper.countRowsByYear(2026)).thenReturn(40);
        when(mapper.countRowsByYear(2025)).thenReturn(38);

        assertThatThrownBy(() -> service.copyYear(dto, ADMIN_ID, IP))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "YEAR_ALREADY_EXISTS");
        verify(mapper, never()).copyDeductionTypesByYear(anyInt(), anyInt(), anyLong());
    }

    @Test
    @DisplayName("연도 복사: 세 테이블을 모두 복사하고 기록을 남긴다")
    void copyYearCopiesThreeTables() {
        TaxCriteriaYearCopyRequestDTO dto = new TaxCriteriaYearCopyRequestDTO();
        dto.setSourceYear(2026);
        dto.setTargetYear(2027);
        when(mapper.countRowsByYear(2026)).thenReturn(40);
        when(mapper.countRowsByYear(2027)).thenReturn(0);

        service.copyYear(dto, ADMIN_ID, IP);

        verify(mapper).copyDeductionTypesByYear(2026, 2027, ADMIN_ID);
        verify(mapper).copyTaxBracketsByYear(2026, 2027, ADMIN_ID);
        verify(mapper).copyBenefitConditionsByYear(2026, 2027, ADMIN_ID);
        verify(accessLogMapper)
                .insertAccessLog(ADMIN_ID, "/api/admin/tax-criteria/years/2027", IP, "SUCCESS");
    }
}
