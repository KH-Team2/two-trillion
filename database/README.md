# DB 작업 파일 — Oracle 적재용 (ERD v4.1)

희망환급 DB(ERD v4.1 · ERDCloud 기준)를 새 Oracle 계정에 만들고 기준값을 넣는 SQL이에요.
팀 노션 「DB 작업 파일 — Oracle 적재용 (v4.1)」의 `DB_v4.1.zip`에서 스키마 · 기준데이터만 저장소에 둬요.

- 공용 DB에는 03(데이터 적재)까지 이미 적재되어 있어요. 팀원은 따로 실행하지 않아도 돼요.
- `03_데이터적재.sql`(가맹점 공공 데이터 · 가상 회원 · 소득 · 카드 내역 · 약 9만 줄)과 `cp949/` 복사본은 크기 때문에 저장소에 올리지 않고 팀 노션 zip으로만 공유해요.

## 파일

| 순서 | 파일 | 내용 |
|---|---|---|
| 0 | `00_초기화.sql` | 기존 테이블·시퀀스 전부 삭제 (처음 만들 때는 건너뛰어도 됨) |
| 1 | `01_schema.sql` | 테이블 20개 · 제약조건 · 시퀀스 · 인덱스 |
| 2 | `02_기준데이터.sql` | 기준값 (`DEDUCTION_TYPE` · `TAX_BRACKET` · `BENEFIT_CONDITION` · `SPEND_CATEGORY` · `GROUP_STAT` 등) |
| 3 | `03_데이터적재.sql` | **팀 노션 zip에만 있음** · 가맹점 목록(`MERCHANT_LIST` · 공공 데이터) · 가상 회원 · 소득 · 카드 내역 |

## 실행 순서

새 계정에 처음부터 만들 때만 실행해요. SQL Developer에서 **00 → 01 → 02 → 03(노션 zip)** 순서로 열고 F5(스크립트 실행)를 눌러요.

- 파일은 UTF-8이에요. SQL Developer는 한글이 그대로 읽혀요.
- cmd의 SQL*Plus에서 한글이 깨지면 노션 zip의 `cp949/` 복사본을 쓰세요.

## 계산 서버 전용 읽기 계정 (최소 권한)

계산 서버(`calc/`)는 기준값 테이블 3개(`DEDUCTION_TYPE` · `TAX_BRACKET` · `BENEFIT_CONDITION`)를 **읽기만** 해요.
그래서 테이블 주인 계정 대신, 이 3개만 읽을 수 있는 계정을 따로 써요. 계산 서버에 문제가 생겨도 회원 정보를 읽거나 데이터를 고치거나 지울 수 없어요.

- 계정 이름 · 비밀번호 · DB 주소: **팀 단톡에만** (노션 · GitHub에 적지 않음)
- 계산 서버 `calc/.env`의 `DB_USERNAME` · `DB_PASSWORD`에 이 계정을 넣어요
- Spring 서버(`backend/`)는 소득 · 계산 결과를 저장해야 해서 테이블 주인 계정을 그대로 써요

만드는 SQL (DB 관리자 계정으로 실행 · `<계산서버계정>` · `<비밀번호>` · `<주인계정>`은 실제 값으로 바꾸기):

```sql
CREATE USER <계산서버계정> IDENTIFIED BY "<비밀번호>";
GRANT CREATE SESSION TO <계산서버계정>;

GRANT SELECT ON <주인계정>.DEDUCTION_TYPE    TO <계산서버계정>;
GRANT SELECT ON <주인계정>.TAX_BRACKET       TO <계산서버계정>;
GRANT SELECT ON <주인계정>.BENEFIT_CONDITION TO <계산서버계정>;

-- 계산 서버 코드가 테이블 이름만 쓰기 때문에 동의어로 연결 (코드 수정 없음)
CREATE SYNONYM <계산서버계정>.DEDUCTION_TYPE    FOR <주인계정>.DEDUCTION_TYPE;
CREATE SYNONYM <계산서버계정>.TAX_BRACKET       FOR <주인계정>.TAX_BRACKET;
CREATE SYNONYM <계산서버계정>.BENEFIT_CONDITION FOR <주인계정>.BENEFIT_CONDITION;
```

확인 (새 계정으로 접속): 기준값 3개 `SELECT COUNT(*)`는 숫자가 나오고, `<주인계정>.MEMBER` 조회는 ORA-00942, `DELETE`/`UPDATE`는 ORA-01031이 나오면 정상이에요.

나중에 계산 서버가 다른 테이블을 읽게 되면 그 테이블도 `GRANT SELECT` + `CREATE SYNONYM`을 추가해야 해요. 안 하면 계산 서버에서 ORA-00942가 나요.

## 규칙

- 테이블·칸 이름은 ERD v4.1이 기준이에요. 구조를 바꿀 때는 ERD를 먼저 고치고 DB 담당(이우진)에게 알려 주세요.
- 기준값(`DEDUCTION_TYPE`)은 번호(`TYPE_ID`)가 아니라 `TAX_YEAR` + `TYPE_CODE`로 찾아요. 계정마다 번호가 달라요.
