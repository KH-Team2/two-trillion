-- 계정_1_생성.sql · DB 계정 2개 만들기 (DB 관리자 계정으로 · 맨 먼저 실행)
-- 주인 계정: 테이블 · 시퀀스 · 함수 주인 · Spring(backend/.env)이 사용 · 롤(CONNECT · RESOURCE) 대신 필요한 권한 4개만
-- 계산 서버 계정: 기준값 3개 읽기 전용 · 계산 서버(calc/.env)가 사용 · 읽기 권한은 테이블이 생긴 뒤 계정_2에서 줌
-- 계정 이름 · 비밀번호는 실행할 때 입력받아요 (저장소 · 노션에 적지 않음 · 실제 값은 팀 단톡)
-- 공용 DB처럼 CDB 루트(CDB$ROOT)에서 만들 때는 이름이 C##으로 시작해야 해요 (아니면 ORA-65096)
-- SQL Developer에서 F5(스크립트 실행) → 입력 창 4번 (Owner name · Owner password · Calc name · Calc password 순서 · 인코딩 설정과 상관없이 안 깨지게 영어)

SET VERIFY OFF

ACCEPT owner_user CHAR PROMPT 'Owner account name: '
ACCEPT owner_pw   CHAR PROMPT 'Owner account password: ' HIDE
ACCEPT calc_user  CHAR PROMPT 'Calc account name: '
ACCEPT calc_pw    CHAR PROMPT 'Calc account password: ' HIDE

-- 1) 주인 계정: 저장 공간은 UNLIMITED TABLESPACE 대신 USERS 2GB 한도
CREATE USER &owner_user IDENTIFIED BY "&owner_pw"
  DEFAULT TABLESPACE USERS
  QUOTA 2G ON USERS;

-- CREATE SESSION 접속 · CREATE TABLE 테이블 21개(인덱스 · LOB 포함) · CREATE SEQUENCE 시퀀스 · CREATE PROCEDURE 함수 FN_NORMALIZE_NAME
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE PROCEDURE TO &owner_user;

-- 2) 계산 서버 계정: 접속만 (테이블을 만들지 않으므로 저장 공간 · 다른 권한 없음)
CREATE USER &calc_user IDENTIFIED BY "&calc_pw";
GRANT CREATE SESSION TO &calc_user;

-- 다음: 주인 계정으로 접속해서 00 → 01 → 02 → 03 → 04 → (관리자) 계정_2_계산서버_권한.sql
