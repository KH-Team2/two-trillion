package com.hoperefund.expense.service;

import com.hoperefund.expense.dto.CardUploadResponseDTO;
import org.springframework.web.multipart.MultipartFile;

/** 카드 내역 업로드 (F-07 · F-02-02 · 업로드 직후 자동 분류 F-02-03까지) */
public interface CardUploadService {

    /**
     * 파일 해석 → 중복 확인 → 저장 → 분류 결과 반영
     *
     * @param memberId 로그인한 회원번호
     * @param file 사용자가 올린 파일 (csv · xlsx · xls)
     * @param payMethod 사용자가 고른 결제수단 (사용구분 칸이 없는 파일일 때만 · CREDIT/CHECK · 없으면 null)
     * @param allowDuplicate 중복 경고를 보고 「계속」을 눌렀으면 true
     */
    CardUploadResponseDTO upload(
            long memberId, MultipartFile file, String payMethod, boolean allowDuplicate);
}
