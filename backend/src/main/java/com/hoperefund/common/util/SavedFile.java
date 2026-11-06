package com.hoperefund.common.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/*
 * 저장된 파일 정보를 담을 객체
 * - 회원 프로필 이미지 : 저장된 위치
 * - 게시글의 이미지 : 원본 파일명, 저장된 파일명, 위치
 */
@Getter
// @AllArgsConstructor
@RequiredArgsConstructor
public class SavedFile {
	private final String originalName;	// 원본 파일명
	private final String saveName;		// 저장된 파일명
	private final String path;			// 저장된 위치
}
