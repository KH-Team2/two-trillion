package com.hoperefund.common.util;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/*
 * MultipartFile 로 전달된 파일을 서버 디스크에 저장하는 작업
 * 
 * - 이미지 업로드 흐름
 *   1) 브라우저에서 파일을 전송 (form enctype="multipart/form-data")
 *   2) 스프링MVC에서 객체로 받아서 컨트롤러로 전달
 *   3) FileUploadUtil 클래스가 실제 바이트를 특정 폴더(uploads/)에 파일로 저장
 *   4) DB에 파일이 저장된 경로만 저장
 *   	==> 파일명을 그대로 저장하지 않음!
 */
@Component
public class FileUploadUtil {

	/**
	 * 파일 저장한 후 경로를 반환하는 메소드
	 * @param file 파일 데이터
	 * @param uploadDir 파일 업로드 경로
	 * @param webPrefix 웹 요청 주소
	 * @throws IOException 
	 * @throws IllegalStateException 
	 */
	public SavedFile save(MultipartFile file, String uploadDir, String webPrefix) throws IllegalStateException, IOException {
		if (file == null || file.isEmpty()) {
			return null;
		}
		
		// 원본 파일명 추출 (파일명 + 확장자)
		String originalName = file.getOriginalFilename();
		
		// 확장자 추출 
		//  ex) image.png / test.2026.png 
		String ext = "";		
		int dotIndex = originalName.lastIndexOf(".");
		if (dotIndex > -1) {
			ext = originalName.substring(dotIndex);
		}
		
		// 저장할 파일명을 임의로 변경
		// UUID : 122비트의 무작위 값으로 겹치지 않게 만들어 주는 객체
		String saveName = UUID.randomUUID() + ext; 
		
		// File 객체를 사용하여 업로드할 경로 확인
		File dir = new File(uploadDir).getAbsoluteFile();
		//System.out.println(dir);	// 확인용
		
		// 해당 경로가 없으면 폴더를 생성
		if (!dir.exists()) {
			dir.mkdirs();
		}
		
		File target = new File(dir, saveName);
		file.transferTo(target);	// MultipartFile 형태로 전달받은 파일을 실제 target 정보로 저장
		
		String path = webPrefix + "/" + saveName;
		
		return new SavedFile(originalName, saveName, path);
	}
	
	
	/**
	 * 웹 요청 주소와 파일이 저장된 위치 경로를 전달받아
	 * 해당 파일을 삭제
	 * 
	 * @param webPath 저장된 웹 요청 주소 (/uploads/profile/xxx.png)
	 * @param uploadDir 파일이 저장된 위치 (uploads/profile)
	 */
	public boolean delete(String webPath, String uploadDir) {
		if (webPath == null || webPath.isEmpty()) {
			return false;
		}
		if (uploadDir == null || uploadDir.isEmpty()) {
			return false;
		}
		
		// 파일명을 추출
		String fileName = webPath.substring( webPath.lastIndexOf("/") + 1 );
		
		File target = new File( new File(uploadDir).getAbsoluteFile(), fileName );
		if (target.exists()) {
			target.delete();
		}
		
		return true;
	}
}
