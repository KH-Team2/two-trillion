-- 계정_2_계산서버_권한.sql · 계산 서버 계정에 기준값 3개 읽기 권한 (DB 관리자 계정으로 · 02까지 끝난 뒤 실행)
-- 계산 서버는 DEDUCTION_TYPE · TAX_BRACKET · BENEFIT_CONDITION만 읽어요 → 이 3개만 SELECT
-- 계산 서버 코드가 테이블 이름만 쓰기 때문에 동의어로 주인 계정 테이블에 연결 (코드 수정 없음)
-- 나중에 계산 서버가 다른 테이블을 읽게 되면 그 테이블도 GRANT SELECT + CREATE SYNONYM 추가 (안 하면 ORA-00942)

SET VERIFY OFF

ACCEPT owner_user CHAR PROMPT 'Owner account name: '
ACCEPT calc_user  CHAR PROMPT 'Calc account name: '

GRANT SELECT ON &owner_user..DEDUCTION_TYPE    TO &calc_user;
GRANT SELECT ON &owner_user..TAX_BRACKET       TO &calc_user;
GRANT SELECT ON &owner_user..BENEFIT_CONDITION TO &calc_user;

CREATE SYNONYM &calc_user..DEDUCTION_TYPE    FOR &owner_user..DEDUCTION_TYPE;
CREATE SYNONYM &calc_user..TAX_BRACKET       FOR &owner_user..TAX_BRACKET;
CREATE SYNONYM &calc_user..BENEFIT_CONDITION FOR &owner_user..BENEFIT_CONDITION;

-- 확인 (계산 서버 계정으로 새로 접속):
--   SELECT COUNT(*) FROM TAX_BRACKET;          → 숫자
--   SELECT COUNT(*) FROM <주인계정>.MEMBER;      → ORA-00942
--   DELETE FROM TAX_BRACKET;                   → ORA-01031
