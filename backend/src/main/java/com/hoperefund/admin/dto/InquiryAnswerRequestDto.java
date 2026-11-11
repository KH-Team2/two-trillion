package com.hoperefund.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InquiryAnswerRequestDto {
	/* F-18 (answer, status, adminId 등) */
	private String answer;	// 관리자 답변 내용 (임시 안내 또는 최종 답변)
	private String status;	// 변경할 처리 상태 (접수 / 확인 중 / 답변 완료)
	private String adminid;	// 답변을 등록하는 관리자 ID (D-22 연동)
		
}
