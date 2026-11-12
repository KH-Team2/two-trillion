package com.hoperefund.expense.service;

import com.hoperefund.expense.client.CardParseClient;
import com.hoperefund.expense.dto.CardParseResultDTO;
import com.hoperefund.expense.dto.CardUploadDTO;
import com.hoperefund.expense.dto.CardUploadResponseDTO;
import com.hoperefund.expense.dto.CategoryCountDTO;
import com.hoperefund.expense.dto.ExpenseGroupCountDTO;
import com.hoperefund.expense.dto.ParsedExpenseDTO;
import com.hoperefund.expense.dto.SpendCategoryDTO;
import com.hoperefund.expense.exception.CardUploadException;
import com.hoperefund.expense.mapper.CardUploadMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 카드 업로드의 실제 내용 (F-07)
 *
 * <p>흐름 (기능명세서 F-02-02)
 *
 * <ol>
 *   <li>파일 확인: 비었는지 · 확장자(csv · xlsx · xls) · 결제수단 값
 *   <li>계산 서버에 파일 보내기 → 공통 4칸 + 분류 결과를 받음 (트랜잭션 밖)
 *   <li>중복 확인 → 겹치는 결제가 있고 「계속」 전이면 409 DUPLICATE_WARNING
 *   <li>저장: 업로드 1행 + 지출 N행 + 분류 UPDATE (CardUploadSaveService · 트랜잭션 안)
 *   <li>분류별 건수를 만들어 돌려줌
 * </ol>
 *
 * <p>원본 파일과 카드번호는 저장하지 않는다 (기능명세서 S-02 확정 사항)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CardUploadServiceImpl implements CardUploadService {

    // 받을 수 있는 확장자 (홈택스 현금영수증은 xls로만 받을 수 있어서 xls 포함)
    static final Set<String> EXTENSIONS = Set.of("csv", "xlsx", "xls");
    // 사용자가 고를 수 있는 결제수단 (현금영수증 CASH는 홈택스 파일이면 계산 서버가 알아서 정함)
    static final Set<String> USER_PAY_METHODS = Set.of("CREDIT", "CHECK");

    private final CardParseClient cardParseClient;
    private final CardUploadSaveService cardUploadSaveService;
    private final CardUploadMapper cardUploadMapper;

    @Override
    public CardUploadResponseDTO upload(
            long memberId, MultipartFile file, String payMethod, boolean allowDuplicate) {
        // 1. 파일 확인
        String fileName = checkFile(file);
        if (payMethod != null && !USER_PAY_METHODS.contains(payMethod)) {
            throw CardUploadException.badRequest("VALIDATION", "결제수단은 신용카드 또는 체크카드만 고를 수 있어요.");
        }

        // 2. 계산 서버에서 해석 · 분류 (DB 트랜잭션 밖 · 팀 규칙)
        CardParseResultDTO parsed = cardParseClient.parse(readBytes(file), fileName, payMethod);
        List<ParsedExpenseDTO> rows = parsed.getRows();
        if (rows.isEmpty()) {
            throw CardUploadException.badRequest("VALIDATION", "파일에 거래 내역이 없어요.");
        }

        // 3. 중복 확인 (「계속」을 눌러 다시 보낸 요청이면 건너뜀)
        if (!allowDuplicate) {
            int duplicates = countDuplicates(memberId, rows);
            if (duplicates > 0) {
                // 막지는 않고 경고만 (같은 날 같은 가게 같은 금액 결제는 정상일 수도 있어서 · 04_분류쿼리 0번)
                throw new CardUploadException(
                        409, "DUPLICATE_WARNING", "이미 올린 내역과 같은 결제가 " + duplicates + "건 있어요");
            }
        }

        // 4. 저장 (트랜잭션 안)
        List<SpendCategoryDTO> categories = cardUploadMapper.findCategories();
        Map<String, Long> categoryIds = new HashMap<>();
        for (SpendCategoryDTO category : categories) {
            categoryIds.put(category.getCategoryCode(), category.getCategoryId());
        }
        if (!categoryIds.containsKey(CardUploadSaveService.GENERAL)) {
            // 분류표에 「일반」이 없으면 저장할 수 없음 · DB 설정 문제라 500 (원인은 로그로)
            throw new IllegalStateException("SPEND_CATEGORY에 GENERAL이 없어요");
        }
        long uploadId = cardUploadSaveService.save(memberId, fileName, rows, categoryIds);
        log.info("카드 업로드 저장 member={} upload={} rows={}", memberId, uploadId, rows.size());

        // 5. 응답 만들기
        CardUploadDTO saved = cardUploadMapper.findUpload(uploadId, memberId);
        return CardUploadResponseDTO.builder()
                .uploadId(uploadId)
                .fileName(fileName)
                .rowCount(rows.size())
                .uploadedAt(saved == null ? null : saved.getUploadedAt())
                .categoryCounts(countByCategory(rows, categories))
                .build();
    }

    /** 파일이 비었는지 · 확장자가 맞는지 확인하고 원본 파일명을 돌려줌 */
    private String checkFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw CardUploadException.badRequest("VALIDATION", "파일을 선택해 주세요.");
        }
        String fileName = file.getOriginalFilename();
        // 파일명 끝의 확장자를 소문자로 (예: "내역.XLSX" → "xlsx")
        String ext =
                fileName == null || !fileName.contains(".")
                        ? ""
                        : fileName.substring(fileName.lastIndexOf('.') + 1)
                                .toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.contains(ext)) {
            throw CardUploadException.badRequest(
                    "UNSUPPORTED_FORMAT", "csv · xlsx · xls 파일만 올릴 수 있어요.");
        }
        return fileName;
    }

    /** 업로드 파일 → 바이트 (읽기 실패는 서버 문제라 500으로) */
    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 파일 읽기 실패", e);
        }
    }

    /**
     * 이미 저장된 거래와 겹치는 건수 (04_분류쿼리.sql 0번 설명과 같은 계산)
     *
     * <p>(사용일 · 가맹점명 · 금액 · 결제수단)이 같은 결제끼리 min(기존 건수, 새 파일 건수)를 더한다.
     *
     * <p>예: 기존에 「4/20 세븐일레븐 3,200원 신용」이 1건, 새 파일에 2건 → 겹침 1건
     */
    int countDuplicates(long memberId, List<ParsedExpenseDTO> rows) {
        // 새 파일의 가장 이른 날 · 가장 늦은 날 (이 기간만 DB에서 조회)
        LocalDate minDate = rows.get(0).getUsedDate();
        LocalDate maxDate = minDate;
        // 새 파일의 거래를 같은 것끼리 세기: "날짜|가맹점|금액|결제수단" → 건수
        Map<String, Integer> newCounts = new HashMap<>();
        for (ParsedExpenseDTO row : rows) {
            if (row.getUsedDate().isBefore(minDate)) {
                minDate = row.getUsedDate();
            }
            if (row.getUsedDate().isAfter(maxDate)) {
                maxDate = row.getUsedDate();
            }
            String key =
                    key(
                            row.getUsedDate(),
                            row.getMerchantName(),
                            row.getAmount(),
                            row.getPayMethod());
            newCounts.merge(key, 1, Integer::sum); // 없으면 1, 있으면 +1
        }

        int duplicates = 0;
        for (ExpenseGroupCountDTO old :
                cardUploadMapper.countExpenseGroups(memberId, minDate, maxDate)) {
            String key =
                    key(
                            old.getUsedDate(),
                            old.getMerchantName(),
                            old.getAmount(),
                            old.getPayMethod());
            Integer newCount = newCounts.get(key);
            if (newCount != null) {
                duplicates += Math.min(old.getCnt(), newCount);
            }
        }
        return duplicates;
    }

    /** 같은 결제인지 비교할 열쇠 글자 */
    private static String key(LocalDate date, String merchant, Long amount, String payMethod) {
        return date + "|" + merchant + "|" + amount + "|" + payMethod;
    }

    /** 분류별 건수 (분류표 순서대로 · 0건인 분류는 빼고) */
    private static List<CategoryCountDTO> countByCategory(
            List<ParsedExpenseDTO> rows, List<SpendCategoryDTO> categories) {
        // LinkedHashMap: 넣은 순서를 기억하는 Map
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ParsedExpenseDTO row : rows) {
            counts.merge(row.getCategoryCode(), 1, Integer::sum);
        }
        List<CategoryCountDTO> result = new ArrayList<>();
        int known = 0; // 분류표에 있는 코드로 온 건수
        for (SpendCategoryDTO category : categories) {
            Integer count = counts.get(category.getCategoryCode());
            if (count != null) {
                known += count;
                result.add(
                        new CategoryCountDTO(
                                category.getCategoryCode(), category.getCategoryName(), count));
            }
        }
        // 분류표에 없는 코드로 온 행은 저장할 때 일반으로 들어가니, 건수도 일반에 더해 줌
        int unknown = rows.size() - known;
        if (unknown > 0) {
            addToGeneral(result, categories, unknown);
        }
        return result;
    }

    /** 모르는 분류 코드 건수를 일반에 더하기 (일반 줄이 없으면 새로 만듦) */
    private static void addToGeneral(
            List<CategoryCountDTO> result, List<SpendCategoryDTO> categories, int extra) {
        for (CategoryCountDTO count : result) {
            if (CardUploadSaveService.GENERAL.equals(count.getCategoryCode())) {
                count.setCount(count.getCount() + extra);
                return;
            }
        }
        for (SpendCategoryDTO category : categories) {
            if (CardUploadSaveService.GENERAL.equals(category.getCategoryCode())) {
                result.add(
                        0,
                        new CategoryCountDTO(
                                category.getCategoryCode(), category.getCategoryName(), extra));
                return;
            }
        }
    }
}
