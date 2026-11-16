# DB 작업 파일 — Oracle 적재용 (ERD v4.1)

희망환급 DB(ERD v4.1 · ERDCloud 기준)를 새 Oracle 계정에 만들고 기준값을 넣는 SQL이에요.
팀 노션 「DB 작업 파일 — Oracle 적재용 (v4.1)」의 `DB_v4.1.zip`에서 스키마 · 기준데이터만 저장소에 둬요.

- 공용 DB에는 03(데이터 적재)까지 이미 적재되어 있어요. 팀원은 따로 실행하지 않아도 돼요.
- `03_데이터적재.sql`(가맹점 공공 데이터 · 가상 회원 · 소득 · 카드 내역 · 약 9만 줄)과 `cp949/` 복사본은 크기 때문에 저장소에 올리지 않고 팀 노션 zip으로만 공유해요.
- 다운로드: 팀 노션 [🗄️ DB 작업 파일 — Oracle 적재용 (v4.1)](https://app.notion.com/p/3f899fb7397c80e3b37ef5308d3489a5)의 `DB_v4.1.zip`

## 파일

| 순서 | 파일 | 실행 계정 | 내용 |
|---|---|---|---|
| 계정 1 | `계정_1_생성.sql` | DB 관리자 | 주인 계정 · 계산 서버 계정 만들기 + 최소 권한 (이름 · 비밀번호는 실행할 때 입력) |
| 0 | `00_초기화.sql` | 주인 | 기존 테이블·시퀀스 전부 삭제 (처음 만들 때는 건너뛰어도 됨) |
| 1 | `01_schema.sql` | 주인 | 테이블 20개 · 제약조건 · 시퀀스 · 인덱스 |
| 2 | `02_기준데이터.sql` | 주인 | 기준값 (`DEDUCTION_TYPE` · `TAX_BRACKET` · `BENEFIT_CONDITION` · `SPEND_CATEGORY` · `GROUP_STAT` 등) |
| 3 | `03_데이터적재.sql` | 주인 | **팀 노션 zip에만 있음** · 가맹점 목록(`MERCHANT_LIST` · 공공 데이터) · 가상 회원 · 소득 · 카드 내역 |
| 4 | `04_분류함수.sql` | 주인 | 가맹점명 정제 함수 `FN_NORMALIZE_NAME` (카드 내역 자동 분류용) |
| 계정 2 | `계정_2_계산서버_권한.sql` | DB 관리자 | 계산 서버 계정에 기준값 3개 읽기 권한 + 동의어 (02 다음) |

## 실행 순서

새 DB에 처음부터 만들 때만 실행해요. SQL Developer에서 파일을 열고 F5(스크립트 실행)를 눌러요.

1. **DB 관리자**로 접속 → `계정_1_생성.sql` (계정 이름 · 비밀번호 입력 창이 떠요)
2. 방금 만든 **주인 계정**으로 접속 → `00` → `01` → `02` → `03`(노션 zip) → `04`
3. 다시 **DB 관리자**로 → `계정_2_계산서버_권한.sql`
4. 각자 `.env`에 계정 넣기: `backend/.env` = 주인 계정 · `calc/.env` = 계산 서버 계정

- 파일은 UTF-8이에요. 한글 Windows의 SQL Developer 기본 설정에서는 한글이 깨져요. **도구 > 환경설정 > 환경 > 인코딩**을 `UTF-8`로 바꾸고 실행하세요.
- 설정을 바꾸기 어려우면 노션 zip의 `cp949/` 복사본(한글 Windows용 · 내용 같음)을 쓰세요.
- 공용 DB(CDB 루트)에서는 계정 이름이 `C##`으로 시작해야 해요 (아니면 ORA-65096)
- `03_데이터적재.sql`은 18MB라 편집창에 열지 말고 `@"경로\03_데이터적재.sql"`로 실행하세요.

## DB 계정 (2개 · 둘 다 최소 권한)

| 계정 | 쓰는 곳 | 할 수 있는 것 |
|---|---|---|
| 주인 계정 | Spring(`backend/.env`) · DB 담당의 스키마 작업 | 자기 테이블 20개 조회 · 저장 · 수정 + 테이블 · 시퀀스 · 함수 만들기 |
| 계산 서버 계정 | 계산 서버(`calc/.env`) | 기준값 테이블 3개 **읽기만** |

- 계정 이름 · 비밀번호 · DB 주소: **팀 단톡에만** (노션 · GitHub에 적지 않음)
- 아래 SQL의 `<주인계정>` · `<계산서버계정>` · `<비밀번호>`는 실제 값으로 바꿔서 실행해요
- 공용 DB에는 두 계정 모두 이미 만들어져 있어요. 팀원은 각자 `.env`에만 넣으면 돼요

## 주인 계정 (테이블 소유 · 최소 권한)

테이블 · 시퀀스 · 인덱스 · 분류 함수(`FN_NORMALIZE_NAME`)를 가진 계정이에요.
`CONNECT` · `RESOURCE` 롤은 쓰지 않는 권한(트리거 · 타입 · 클러스터 등)까지 딸려 와서, 롤 대신 필요한 권한 4개만 줘요.

| 권한 | 쓰는 곳 |
|---|---|
| `CREATE SESSION` | 접속 |
| `CREATE TABLE` | 테이블 20개 (인덱스 · LOB은 자기 테이블에 딸린 것이라 따로 권한 필요 없음) |
| `CREATE SEQUENCE` | 시퀀스 |
| `CREATE PROCEDURE` | 분류 함수 `FN_NORMALIZE_NAME` (`04_분류함수.sql`) |

- 저장 공간은 `UNLIMITED TABLESPACE` 대신 USERS 테이블스페이스에 한도(2GB)를 줘요
- 자기 테이블의 조회 · 저장 · 수정 · 삭제는 주인이라 따로 권한이 필요 없어요 (Spring은 이것만 써요)

만드는 SQL (DB 관리자 계정으로 실행 · `계정_1_생성.sql`과 같은 내용):

```sql
CREATE USER <주인계정> IDENTIFIED BY "<비밀번호>"
  DEFAULT TABLESPACE USERS
  QUOTA 2G ON USERS;

GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE PROCEDURE TO <주인계정>;
```

이미 `CONNECT` · `RESOURCE`로 만든 계정을 줄일 때 (DB 관리자 계정 · 만든 테이블 · 데이터는 그대로):

```sql
-- 권한을 먼저 주고 롤을 나중에 빼야 그 사이에 새 접속이 막히지 않아요
GRANT CREATE SESSION, CREATE TABLE, CREATE SEQUENCE, CREATE PROCEDURE TO <주인계정>;
REVOKE CONNECT, RESOURCE FROM <주인계정>;
```

확인 (주인 계정으로 **새로 접속**해서 · 롤을 뺀 뒤에도 이미 열려 있던 접속에는 예전 권한이 남아 있어요):

```sql
SELECT 'ROLE' AS 구분, GRANTED_ROLE AS 권한 FROM USER_ROLE_PRIVS
UNION ALL
SELECT 'SESSION', PRIVILEGE FROM SESSION_PRIVS
ORDER BY 1, 2;
```

ROLE 행 없이 SESSION 4개(`CREATE PROCEDURE` · `CREATE SEQUENCE` · `CREATE SESSION` · `CREATE TABLE`)만 나오면 정상이에요.

- 11/16 공용 DB 적용 · 확인 완료

나중에 트리거 · 뷰처럼 다른 종류의 객체를 만들게 되면 그 권한(`CREATE TRIGGER` · `CREATE VIEW`)만 추가해요.

## 계산 서버 전용 읽기 계정 (최소 권한)

계산 서버(`calc/`)는 기준값 테이블 3개(`DEDUCTION_TYPE` · `TAX_BRACKET` · `BENEFIT_CONDITION`)를 **읽기만** 해요.
그래서 테이블 주인 계정 대신, 이 3개만 읽을 수 있는 계정을 따로 써요. 계산 서버에 문제가 생겨도 회원 정보를 읽거나 데이터를 고치거나 지울 수 없어요.

- 계산 서버 `calc/.env`의 `DB_USERNAME` · `DB_PASSWORD`에 이 계정을 넣어요
- Spring 서버(`backend/`)는 소득 · 계산 결과를 저장해야 해서 테이블 주인 계정을 그대로 써요

만드는 SQL (DB 관리자 계정으로 실행 · `계정_1_생성.sql` + `계정_2_계산서버_권한.sql`과 같은 내용):

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
