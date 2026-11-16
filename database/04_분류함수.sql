-- 04_분류함수.sql · 가맹점명 정제 함수 FN_NORMALIZE_NAME (주인 계정으로 실행 · 01 다음 아무 때나)
-- 카드 내역 자동 분류(EXPENSE.CATEGORY_ID)가 가맹점명을 MERCHANT_LIST.NORMALIZED_NAME과 같은 규칙으로 정제할 때 써요
-- 규칙: 전각→반각 · 대문자 · 법인 표기 제거 · 끝의 「한국지점」 제거 · 한글·영문·숫자만 남김 (Python NFKC 정제와 결과 같음)
-- 분류 쿼리 전체(업로드 1건 분류 MERGE)는 팀 노션 zip의 04_분류쿼리.sql에 있어요 · 이 파일은 그중 함수 부분만
-- 확인: SELECT FN_NORMALIZE_NAME('(주)카페 66℃ 한국지점') FROM DUAL;  → 카페66C

CREATE OR REPLACE FUNCTION FN_NORMALIZE_NAME (p_name IN VARCHAR2)
RETURN VARCHAR2 DETERMINISTIC
IS
  v VARCHAR2(400);
  TYPE t_list IS TABLE OF VARCHAR2(10);
  -- 로마 숫자·원 문자는 Python(NFKC)과 같게 일반 글자로 바꿈 (예: 상영관Ⅰ → 상영관I)
  -- v3.1: Python(NFKC)과 결과가 같도록 ℃·아래첨자 숫자·ⅵ~ⅹ·원 숫자 11~20·㈕까지 추가 (예: 카페(cafe)66℃ → 카페CAFE66C)
  --       cp949에 없는 글자(원 숫자 16~20, 아래첨자 0·5~9)는 UNISTR로 적음 (SQL Developer cp949 파일에서도 그대로 실행되게)
  src t_list := t_list('Ⅰ','Ⅱ','Ⅲ','Ⅳ','Ⅴ','Ⅵ','Ⅶ','Ⅷ','Ⅸ','Ⅹ','ⅰ','ⅱ','ⅲ','ⅳ','ⅴ','ⅵ','ⅶ','ⅷ','ⅸ','ⅹ','①','②','③','④','⑤','⑥','⑦','⑧','⑨','⑩','⑪','⑫','⑬','⑭','⑮',UNISTR('\246F'),UNISTR('\2470'),UNISTR('\2471'),UNISTR('\2472'),UNISTR('\2473'),'℃','℉',UNISTR('\2080'),'₁','₂','₃','₄',UNISTR('\2085'),UNISTR('\2086'),UNISTR('\2087'),UNISTR('\2088'),UNISTR('\2089'),'¹','²','³','㈕');
  dst t_list := t_list('I','II','III','IV','V','VI','VII','VIII','IX','X','i','ii','iii','iv','v','vi','vii','viii','ix','x','1','2','3','4','5','6','7','8','9','10','11','12','13','14','15','16','17','18','19','20','C','F','0','1','2','3','4','5','6','7','8','9','1','2','3','(아)');
BEGIN
  IF p_name IS NULL THEN RETURN NULL; END IF;
  v := TO_SINGLE_BYTE(p_name);
  FOR i IN 1 .. src.COUNT LOOP v := REPLACE(v, src(i), dst(i)); END LOOP;
  v := UPPER(v);
  v := REPLACE(v, '㈜', '(주)');
  v := REGEXP_REPLACE(v, '(주식회사|유한회사|유한책임회사|사단법인|재단법인|사회적협동조합|협동조합|\(주\)|\(유\)|\(사\)|\(재\)|\(합\)|㈔|㈕|주\))', '');
  v := REGEXP_REPLACE(TRIM(v), '한국지점$', '');
  v := REGEXP_REPLACE(v, '[^0-9A-Z가-힣]', '');
  RETURN v;
END;
/
