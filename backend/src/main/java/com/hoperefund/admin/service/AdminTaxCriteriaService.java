package com.hoperefund.admin.service;

import com.hoperefund.admin.dto.CriteriaHistoryResponseDTO;
import com.hoperefund.admin.dto.TaxCriteriaRequestDTO;
import com.hoperefund.admin.dto.TaxCriteriaResponseDTO;
import com.hoperefund.admin.dto.TaxCriteriaYearCopyRequestDTO;
import com.hoperefund.admin.mapper.AdminTaxCriteriaMapper;
import com.hoperefund.common.exception.BadRequestException;
import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.common.exception.NotFoundException;
import com.hoperefund.common.mapper.AccessLogMapper;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * F-19 (F-08-01): 세법 기준값 조회·추가·수정 비즈니스 로직
 * - DEDUCTION_TYPE / TAX_BRACKET / BENEFIT_CONDITION 연동
 *
 * <p>[수정 11/26 고은] 바꾼 것 요약
 * - IllegalArgumentException(공통 처리기에서 500) → 팀 공통 예외 (400 · 404 · 409)
 * - DB 값 제한 위반(ORA-02290) → 400 「입력값을 확인해 주세요」 · 중복(ORA-00001) → 409 (명세 F-08-01 예외 처리)
 * - DB에 가기 전에 같은 규칙을 먼저 검사 (구간 끝 > 시작 · 최소 나이 ≤ 최대 나이 · 카드 행은 분류 필수)
 * - 수정 · 추가 · 연도 복사를 ACCESS_LOG에 기록 (명세 ④)
 * - 수정할 번호가 없으면 404 (원래는 0행 수정인데도 성공으로 응답)
 * - 연도 복사: 새 연도에 이미 기준값이 있으면 409 · 복사할 연도에 없으면 404
 * - 변경 이력 조회 추가 (CRITERIA_HISTORY · 트리거가 남긴 것)
 * - RATE 0 안내 · 계산식 안내 · calculateBracketTax 삭제 → 안내는 화면 · 계산은 계산 서버가 담당
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminTaxCriteriaService {

    // [추가 11/26 고은] ACCESS_LOG.SCREEN에 남길 앞부분 · 예) /api/admin/tax-criteria/DEDUCTION_TYPE/12
    static final String SCREEN = "/api/admin/tax-criteria";
    static final String RESULT_SUCCESS = "SUCCESS";
    static final String INVALID_MESSAGE = "입력값을 확인해 주세요";

    private final AdminTaxCriteriaMapper adminTaxCriteriaMapper;
    private final AccessLogMapper accessLogMapper; // [추가 11/26 고은] 공통 접근 기록

    /*
     * 특정 귀속 연도(TAX_YEAR)의 공제항목·세율구간·혜택조건 조회
     * [수정 11/26 고은] taxYear가 비면 기준값이 있는 가장 최근 연도 · 연도 목록(years) 같이 보냄
     */
    @Transactional(readOnly = true)
    public TaxCriteriaResponseDTO getTaxCriteria(Integer taxYear) {
        List<Integer> years = adminTaxCriteriaMapper.selectYears();
        Integer year = taxYear != null ? taxYear : (years.isEmpty() ? null : years.get(0));
        if (year == null) {
            // 기준값이 하나도 없는 DB (02_기준데이터.sql을 아직 안 넣음)
            return TaxCriteriaResponseDTO.builder()
                    .years(years)
                    .deductionTypes(List.of())
                    .taxBrackets(List.of())
                    .benefitConditions(List.of())
                    .build();
        }
        return TaxCriteriaResponseDTO.builder()
                .taxYear(year)
                .years(years)
                .deductionTypes(adminTaxCriteriaMapper.selectDeductionTypes(year))
                .taxBrackets(adminTaxCriteriaMapper.selectTaxBrackets(year))
                .benefitConditions(adminTaxCriteriaMapper.selectBenefitConditions(year))
                .build();
    }

    /*
     * 기존 세법 기준값 수정 (대상 테이블별 업데이트 및 UPDATED_AT/UPDATED_BY 갱신)
     * - 이전 값 · 새 값은 DB 트리거가 CRITERIA_HISTORY에 자동으로 남겨요 (PR #57)
     */
    @Transactional
    public void updateTaxCriteria(TaxCriteriaRequestDTO dto, Long updatedBy, String ipAddress) {
        if (dto.getId() == null) {
            throw new BadRequestException("수정할 대상 번호가 필요해요");
        }
        checkValues(dto);
        int updated =
                runSql(
                        () ->
                                switch (dto.getTargetType()) {
                                    case "DEDUCTION_TYPE" ->
                                            adminTaxCriteriaMapper.updateDeductionType(
                                                    dto, updatedBy);
                                    case "TAX_BRACKET" ->
                                            adminTaxCriteriaMapper.updateTaxBracket(dto, updatedBy);
                                    default ->
                                            adminTaxCriteriaMapper.updateBenefitCondition(
                                                    dto, updatedBy);
                                });
        if (updated == 0) {
            throw new NotFoundException("기준값을 찾을 수 없어요");
        }
        writeAccessLog(
                updatedBy, SCREEN + "/" + dto.getTargetType() + "/" + dto.getId(), ipAddress);
    }

    /*
     * [수정 11/26 고은] 행 하나 추가 (같은 코드로 구간을 하나 더 넣을 때 · 명세 「구간 추가는 같은 코드로 행을 INSERT」)
     * - 원래 createTaxCriteria의 sourceYear(연도 복사)는 copyYear로 나눴어요
     */
    @Transactional
    public void createTaxCriteria(TaxCriteriaRequestDTO dto, Long updatedBy, String ipAddress) {
        if (dto.getTaxYear() == null || isBlank(dto.getCode())) {
            throw new BadRequestException("연도와 코드를 입력해 주세요");
        }
        checkValues(dto);
        checkInsertOnly(dto);
        runSql(
                () ->
                        switch (dto.getTargetType()) {
                            case "DEDUCTION_TYPE" ->
                                    adminTaxCriteriaMapper.insertDeductionType(dto, updatedBy);
                            case "TAX_BRACKET" ->
                                    adminTaxCriteriaMapper.insertTaxBracket(dto, updatedBy);
                            default ->
                                    adminTaxCriteriaMapper.insertBenefitCondition(dto, updatedBy);
                        });
        writeAccessLog(updatedBy, SCREEN + "/" + dto.getTargetType(), ipAddress);
    }

    /*
     * [추가 11/26 고은] 새 귀속 연도 추가: sourceYear 기준값을 targetYear로 통째로 복사 (원래 sourceYear 기능)
     * - 세 테이블을 한 트랜잭션으로 복사 → 중간에 실패하면 전부 취소
     */
    @Transactional
    public void copyYear(TaxCriteriaYearCopyRequestDTO dto, Long updatedBy, String ipAddress) {
        if (dto.getSourceYear().equals(dto.getTargetYear())) {
            throw new BadRequestException("복사할 연도와 새 연도가 같아요");
        }
        if (adminTaxCriteriaMapper.countRowsByYear(dto.getSourceYear()) == 0) {
            throw new NotFoundException(dto.getSourceYear() + "년 기준값이 없어요");
        }
        if (adminTaxCriteriaMapper.countRowsByYear(dto.getTargetYear()) > 0) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "YEAR_ALREADY_EXISTS",
                    dto.getTargetYear() + "년 기준값이 이미 있어요");
        }
        runSql(
                () -> {
                    adminTaxCriteriaMapper.copyDeductionTypesByYear(
                            dto.getSourceYear(), dto.getTargetYear(), updatedBy);
                    adminTaxCriteriaMapper.copyTaxBracketsByYear(
                            dto.getSourceYear(), dto.getTargetYear(), updatedBy);
                    return adminTaxCriteriaMapper.copyBenefitConditionsByYear(
                            dto.getSourceYear(), dto.getTargetYear(), updatedBy);
                });
        writeAccessLog(updatedBy, SCREEN + "/years/" + dto.getTargetYear(), ipAddress);
    }

    /* [추가 11/26 고은] 한 행의 변경 이력 (최신순 · 최대 100줄) */
    @Transactional(readOnly = true)
    public List<CriteriaHistoryResponseDTO> getHistory(String targetType, Long id) {
        return adminTaxCriteriaMapper.selectHistory(targetType, id);
    }

    /**
     * [추가 11/26 고은] DB CHECK 제약과 같은 규칙을 먼저 검사 → 어긋나면 400 (DB까지 안 감)
     *
     * <ul>
     *   <li>구간 끝 &gt; 시작 (CK_DEDUCTION_SALARY · CK_TAX_BRACKET_RANGE) · 끝이 비면 상한 없음이라 통과
     *   <li>최소 나이 ≤ 최대 나이 (CK_BENEFIT_AGE)
     *   <li>테이블마다 꼭 있어야 하는 값 (공제율 · 구간 시작 · 누적액 · 무주택 조건)
     * </ul>
     */
    void checkValues(TaxCriteriaRequestDTO dto) {
        switch (dto.getTargetType()) {
            case "DEDUCTION_TYPE" -> {
                if (dto.getRate() == null || dto.getSalaryMin() == null || isBlank(dto.getName())) {
                    throw new BadRequestException(INVALID_MESSAGE);
                }
                if (dto.getSalaryMax() != null && dto.getSalaryMax() <= dto.getSalaryMin()) {
                    throw new BadRequestException("총급여 구간 끝은 시작보다 커야 해요");
                }
            }
            case "TAX_BRACKET" -> {
                if (dto.getRate() == null
                        || dto.getLowerAmount() == null
                        || dto.getBaseAmount() == null) {
                    throw new BadRequestException(INVALID_MESSAGE);
                }
                if (dto.getUpperAmount() != null && dto.getUpperAmount() <= dto.getLowerAmount()) {
                    throw new BadRequestException("구간 끝은 시작보다 커야 해요");
                }
            }
            default -> {
                if (isBlank(dto.getName()) || isBlank(dto.getHomelessHeadReqYn())) {
                    throw new BadRequestException(INVALID_MESSAGE);
                }
                if (dto.getMinAge() != null
                        && dto.getMaxAge() != null
                        && dto.getMinAge() > dto.getMaxAge()) {
                    throw new BadRequestException("최소 나이는 최대 나이보다 클 수 없어요");
                }
            }
        }
    }

    /* [추가 11/26 고은] 행 추가 때만 보는 규칙: 공제 종류 필수 · 카드 공제율 행은 분류 필수 (CK_DEDUCTION_CARD) */
    private void checkInsertOnly(TaxCriteriaRequestDTO dto) {
        if (!"DEDUCTION_TYPE".equals(dto.getTargetType())) {
            return;
        }
        String kind = dto.getDeductionKind();
        if (kind == null
                || !List.of("CARD", "CARD_RULE", "INCOME_DEDUCTION", "TAX_CREDIT", "TAX_REDUCTION")
                        .contains(kind)) {
            throw new BadRequestException("공제 종류를 확인해 주세요");
        }
        if ("CARD".equals(kind) && dto.getCategoryId() == null) {
            throw new BadRequestException("카드 공제율 행은 지출 분류가 필요해요");
        }
    }

    /**
     * [추가 11/26 고은] SQL 실행 + DB 오류를 알아듣기 쉬운 응답으로 바꾸기
     *
     * <ul>
     *   <li>DuplicateKeyException (ORA-00001 · 같은 연도 · 코드 · 구간 시작이 이미 있음) → 409 DUPLICATE_CRITERIA
     *   <li>DataIntegrityViolationException (ORA-02290 값 제한 · ORA-02291 없는 분류 번호) → 400 「입력값을 확인해
     *       주세요」
     * </ul>
     *
     * <p>Supplier = 「나중에 실행할 코드 한 덩어리」를 넘기는 자바 문법. 여기서는 mapper 호출을 감싸서 오류만 바꿔요.
     */
    private int runSql(Supplier<Integer> sql) {
        try {
            return sql.get();
        } catch (DuplicateKeyException e) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "DUPLICATE_CRITERIA", "같은 연도 · 코드 · 구간 시작 행이 이미 있어요");
        } catch (DataIntegrityViolationException e) {
            log.warn("기준값 저장 실패 (DB 제약 위반)", e);
            throw new BadRequestException(INVALID_MESSAGE);
        }
    }

    /**
     * [추가 11/26 고은] ACCESS_LOG 한 줄 저장 (F-20 · F-21 · LoginService와 같은 규칙)
     *
     * <p>번호가 겹치면 한 번 더 시도 · 그래도 실패하면 서버 로그만 (기록 실패 때문에 기준값 수정이 막히지 않게)
     */
    private void writeAccessLog(Long adminId, String screen, String ipAddress) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                accessLogMapper.insertAccessLog(adminId, screen, ipAddress, RESULT_SUCCESS);
                return;
            } catch (DuplicateKeyException e) {
                if (attempt == 2) {
                    log.error("관리자 동작 기록 저장 실패 (번호 겹침) screen={}", screen, e);
                }
            } catch (DataAccessException e) {
                log.error("관리자 동작 기록 저장 실패 screen={}", screen, e);
                return;
            }
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
