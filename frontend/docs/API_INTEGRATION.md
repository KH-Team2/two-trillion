# Spring Boot · MyBatis · Oracle · Gemma3 연결 안내

이 문서는 구현할 위치를 설명합니다. 현재 외부 API 호출을 추가하지 않았으며 React는 Oracle에 직접 접속하지 않습니다. 문서에 없는 API 경로나 DB 컬럼을 확정하지 않습니다.

## 계산 결과

`src/api/calculation-contract.js`의 `parseCalculationResult(response)`로 서버 응답을 검증합니다. `{error: 'NO_CRITERIA', message: '...'}`는 오류로 전달합니다. 응답의 필수 숫자값 누락·NaN을 0으로 숨기지 않습니다.

- `taxYear`, `totalSalary`, `withheldTax`, `taxableIncome`, `calculatedTax`, `decidedTax`, `expectedRefund`
- `details[]`: `typeCode`, `typeName`, `kind`, `baseAmount`, `deductionAmount`, `sourceNote`
- 종류: `INCOME_DEDUCTION`, `TAX_CREDIT`, `TAX_REDUCTION`, `CARD`, `CARD_RULE`
- `effect`, `formula`, `note`, `status`는 응답 계약에 포함하지 않습니다. 설명은 `deductionPresentation()`에서 제공합니다.
- 음수·0원 공제액을 보존하며 `cardDeductionTotal()`은 CARD/CARD_RULE 행만 합산합니다. 소득공제·세액공제·감면을 하나의 금액으로 섞어 더하지 않습니다.

> 2026-11-23 체험판 기능 삭제: 체험용 계산(`taxService`) · `isDemo` · `canUseDemo` · `prototype-records.js` · `aiService` · 브라우저 계정(`createAccountService`)을 지웠습니다. 아래 문단 중 이 이름이 나오는 설명은 지난 기록입니다. 화면은 서버 계산 결과만 보여 주고, 결과가 없으면 `App.jsx`의 `NeedResult` 안내, 서버 API 전 화면은 `ComingSoon`을 보여 줍니다.

`App.calculate()`가 서버 계산 응답을 상태로 보관하여 각 화면에 동일 결과를 전달합니다.

`Dashboard`는 서버 계산 결과를 `result`로 받습니다. `dashboardModel(data, rows, result)`, `groupComparison(data, result)`도 서버 결과를 꼭 받아야 합니다. 비교 화면은 `br`(기준 결과) · `sr`(시뮬레이션 결과)를 사용합니다. AI 상담은 「준비 중」이며, 연동할 때 AI가 세액을 다시 계산하게 하지 마세요.

실제 응답 파서는 `taxableIncome`에 숫자를 요구합니다. 사용자 피드백의 예시 숫자를 법정 검증값으로 사용하지 않습니다.

## 입력과 귀속 연도

`src/utils/tax-year.js`: 한국 시간 1~3월 전년도/4~12월 해당 연도라는 서비스 기본 선택 정책. 저장된 `taxYear` 또는 선택 연도가 있으면 유지합니다. 입력·상단·거래 집계·계산·시뮬레이션·사용자 통계 비교에 이를 전달합니다. 통계 원본의 귀속 연도 2024는 바꾸지 않습니다.

`TaxInfoForm`에서 독립 상태로 관리하는 `nationalPension`, `healthEmploymentInsurance`는 프론트 필드입니다. `erd-contract.insuranceInput()`에서 구분하여 받되 v3.9 컬럼·필수값 확인 후 서버 매핑을 구현해야 합니다. 기존 SOCIAL_INSURANCE에 두 값을 임의로 합산해서 저장하지 않습니다. `incomeDTO()`는 이 보험료 부분을 운영 DTO로 확정하지 않았습니다.

`qualificationDTO.HOMELESS_HEAD_YN`, `incomeDTO.SME_REDUCTION_YN`: Y/N/null을 구분합니다. null을 N으로 치환하지 마세요. 서버 저장 요건이 Y/N 필수라면 입력 요청 오류로 처리하거나 팀이 정한 미입력 처리 규칙을 구현해야 합니다.

`MONTHLY_RENT`는 월 금액 그대로입니다. 연간 금액은 월 금액 × 12로 서버가 계산합니다. 실제 납부 개월·계약 검증은 서버가 담당합니다. 과거 연간 입력은 재입력을 요청하고 원본을 별도 보존합니다.

## 업로드·분류·회원 격리

**로그인 회원 (서버 저장 · 11/17~ · F-02-02 · F-02-04)**: `card-upload-service.js`
- 업로드: `uploadCardFile()` → `POST /api/card-uploads` (FormData · `file` · 선택 `payMethod` · `allowDuplicate`) → 백엔드가 계산 서버(`POST /card/parse`)로 해석 · 분류 후 `CARD_UPLOAD` · `EXPENSE` 저장 → `fetchUploadExpenses()`(`GET /api/expenses?uploadId=`)로 분류 근거까지 받아 `toUploadRow()`로 화면 모양 변환
- 결제수단 없음(400 `PAY_METHOD_REQUIRED`) → 신용/체크 선택 창 → 다시 보냄 · 중복(409 `DUPLICATE_WARNING`) → 확인 창 → `allowDuplicate=true`로 다시 보냄 (최대 3번)
- 화면이 열릴 때 `loadServerUploads()`(`GET /api/card-uploads` + 업로드별 거래 목록)로 이력 복원 · 파일 삭제는 `deleteUpload()`(`DELETE /api/card-uploads/{uploadId}` · 204)
- 미지원 형식(11/19 · TT-148): 이름이 `.csv` · `.xlsx` · `.xls`가 아니면 서버로 보내지 않음 · 서버가 `UNSUPPORTED_FORMAT`으로 답한 파일도 목록에 넣지 않음 → 끝난 뒤 `alert` 한 번 (「지원하지 않는 형식입니다.」 + 파일 이름)
- `api-client.js`: body가 FormData면 JSON으로 바꾸지 않고 `Content-Type`도 넣지 않음 (브라우저가 boundary 포함해서 넣음) · 204면 JSON을 읽지 않고 `null`
- 회원번호는 보내지 않음 · 백엔드가 로그인 토큰에서 꺼냄 (11/19 · TT-149)

`expense-service.js`: 업로드 묶음 · 중복 확인 · `uploadTotals(rows, taxYear)` 집계만 합니다. 브라우저 파일 해석(`parseUploadFile` 등)과 예시 상호명 분류는 2026-11-23 체험판 삭제 때 지웠습니다. 다른 연도 거래를 삭제하지 않습니다.

`erd-contract.expenseDTO(row, categoryIdByCode, uploadIdByClientId)`는 서버에서 조회한 분류코드→숫자 CATEGORY_ID, 로컬 파일 ID→서버 발급 UPLOAD_ID 매핑이 준비되어야 객체를 생성합니다. 결제수단은 CREDIT/CHECK/CASH를 검증하며 임의 번호·알 수 없는 결제수단을 보내지 않습니다. 기존 예시 목록 LIST_ID는 운영 외래키가 아니므로 null로 내보내며 서버가 실제 목록 대조 결과로 확정해야 합니다.

회원번호는 요청 객체에서 제외합니다. 서버가 로그인 인증정보로 MEMBER_ID를 결정하고 업로드 소유권을 검증하며 거래 조회·삭제에도 동일한 회원 조건을 적용해야 합니다. 브라우저 저장 키 분리는 운영 인증이 아닙니다. 분류 결과의 CATEGORY_CODE는 UI/분류 경계 값으로 CATEGORY_ID와 다릅니다. 사용자 소비 유형 직접 수정 기능은 추가하지 않았습니다.

## 회원·동의

`account-service.js`, `AccountForms.jsx`: 가입 · 로그인 · 중복 검사 · 이메일 인증은 서버 API를 씁니다 (`member-api.js`). 회원정보 수정 · 비밀번호 변경 · 탈퇴 · 관리자 회원 관리는 서버 API 전이라 「준비 중」입니다.

`src/config/consent-versions.js`의 환경변수는 팀의 운영 동의서 버전 설정입니다. `consentDTO()`는 누락·빈 문자열·미확정 체험 버전을 차단합니다. 최종 시각·버전은 서버가 검증하고 기록하세요. 클라이언트가 보낸 동의 시각을 신뢰하지 않습니다.

## 통계

`GROUP_STAT`의 실제 열만 `src/data/statistics/group-stat-2024.json`에 유지했습니다. `group-stat-metadata.json`의 POPULATION/원본 합계 등은 출처 재현용 보조 메타데이터로 DB 컬럼이 아닙니다. `STAT_FILE`의 FILE_ID 관계와 파일 경로는 `stat-file.json`에 있습니다.

서버에 연결되면 내가 속한 그룹은 서버(`GET /api/group-stats/{taxYear}`)가 찾아 주고, 연결되지 않으면 이 스냅샷에서 찾습니다(아래 「그룹 비교 통계 (F-06-02)」). `group-model.js`는 GENDER_AGE와 SALARY 행을 독립적으로 조회합니다. `GroupStats.jsx`는 AVG_DECIDED_TAX/AVG_REFUND_TAX만 비교하며 중앙값·순위·백분위를 생성하지 않습니다. DB 연동 시 실재하는 필드와 TAX_YEAR 기준으로 조회하고 없는 항목은 생성하지 마세요. 정적 파일의 메타데이터는 DB 응답의 필수 열로 가정하지 않습니다.

## 그 밖의 연결 위치

- 고객센터 문의(`SupportPage.jsx`) · 관리자(`AdminPage.jsx`): 「준비 중」 · 문의 · 관리자 API가 들어오면 연결 (예전 브라우저 시연 코드 `prototype-records.js` · `AdminTools.jsx`는 git 기록에 있음)
- AI 상담(`ChatbotPage.jsx` · 대시보드 상담 카드): 「준비 중」 · 상담방/메시지 저장, Gemma3·RAG 호출 연결 필요 (예전 고정 응답 `ChatPanel.jsx`는 git 기록에 있음)
- `App.jsx`, `userService`: 입력·계산 이력·시나리오 조회/저장을 서버로 교체. 원본과 가상 입력은 분리 유지.

## 소비 유형 표 복구 후 표시 경계

대시보드의 소비 유형별 표는 업로드 거래에서만 집계합니다. `details`를 소비 유형으로 재분류하거나 지출액으로 사용하지 않습니다. 별도 `DeductionDetails`는 기본 접힌 표이며, 계산 API 미연동 시 숫자를 확정값으로 표시하지 않습니다. 서버 계산 결과만 받으므로 0원과 음수 조정액도 그대로 표시하고 카드 합계에 반영합니다 (2026-11-23 `calculationConnected` · `isDemo` 삭제).

## 백엔드 API 연결 흐름 — 세액 계산·진단 파트 (F-02-01 · F-03-01 · F-03-02)

화면이 어떤 API를 어떤 순서로 부르는지 요약만 적습니다. 상세 기록은 작업 단위로 `docs/api_integ_logs/`에 따로 둡니다 (11/11 팀 결정).

- 이 파트의 상세 기록: [`api_integ_logs/TT-11_소득입력_계산_혜택진단.md`](api_integ_logs/TT-11_소득입력_계산_혜택진단.md)
- 결과 대시보드(F-06-01)의 상세 기록: [`api_integ_logs/TT-14_대시보드_저장결과조회.md`](api_integ_logs/TT-14_대시보드_저장결과조회.md)
- 그룹 비교 통계(F-06-02)의 상세 기록: [`api_integ_logs/TT-14_그룹비교통계.md`](api_integ_logs/TT-14_그룹비교통계.md)
- 조건 변경 시뮬레이션 · 결과 이력(F-04-01 · F-04-02)의 상세 기록: [`api_integ_logs/TT-105_시뮬레이션_결과이력.md`](api_integ_logs/TT-105_시뮬레이션_결과이력.md)

그 밖의 세부 사항은 아래 원본을 봅니다.

| 세부 사항 | 관리하는 곳 |
| --- | --- |
| 응답 봉투 · 오류 코드 | `backend/docs/api-rules.md` |
| 화면 값 → 서버 이름 (소득 저장) | `src/api/income-service.js`의 `incomeRequest()` |
| 계산 결과 응답 검사 | `src/api/calculation-contract.js`의 `parseCalculationResult()` |
| 개발용 설정 (`VITE_API_PROXY`) | `.env.example` · `vite.config.js` |
| 로그인 토큰 보관 이름 (`ACCESS_TOKEN_KEY`) | `src/api/api-client.js` |

### 공통

- 모든 호출은 `src/api/api-client.js`의 `apiRequest()`를 거칩니다. 성공이면 응답 봉투의 `data`만 돌려주고, 실패면 서버의 `error.code` · `error.message`를 가진 오류를 던집니다.
- 서버에 연결할 수 없으면 `OFFLINE` 오류입니다. 연결 실패 안내만 보여 줍니다 (2026-11-23 체험용 계산 대체 `canUseDemo` 삭제).
- 회원 구분: localStorage의 로그인 토큰(`hope-refund.accessToken`)을 `Authorization: Bearer` 헤더로 보냅니다. 서버가 토큰에서 회원번호를 꺼냅니다. 토큰이 없으면 서버가 401 `UNAUTHORIZED`를 줍니다 (2026-11-17 · `X-Member-Id` 헤더 없앰).
- 로그인(`accountService.login` → `POST /api/auth/login`)이 토큰을 저장합니다 (2026-11-17). 보낸 토큰이 401이면 `expireLogin()`이 토큰을 지우고 `App`이 로그인 화면으로 보냅니다.

### 소득 저장 · 세액 계산 (F-02-01 · F-03-01)

`App.calculate()` → `src/api/calculation-service.js`의 `saveAndCalculate(d)`

1. `PUT /api/incomes/{taxYear}` — 소득 저장. 한 해 소득 전체를 덮어쓰므로 모든 칸을 보냅니다.
2. 저장이 성공하면 `POST /api/calculations/{taxYear}` — 계산 + 결과 저장.
3. 결과를 `calcResult` 상태로 보관해 대시보드에 `result` · `isDemo={false}`로 넘깁니다. 응답의 `predictionId`(저장된 결과 번호)는 그대로 남깁니다.

- 서버 오류면 서버 문구를 알림으로 띄우고 입력 화면에 머뭅니다 (입력값 유지).
- 계산 버튼을 연달아 눌러도 요청은 한 번만 보냅니다.

### 결과 대시보드 — 저장된 계산 결과 조회 (F-06-01)

대시보드 · 통계 화면 진입 → `src/api/calculation-service.js`의 `fetchLatestCalculation(taxYear)` → `GET /api/calculations/{taxYear}`

- 화면에 그 귀속 연도의 서버 결과(`calcResult`)가 없을 때만 부릅니다. 방금 계산했거나 브라우저 저장에서 이어 온 결과가 있으면 부르지 않습니다.
- 받은 결과는 계산 직후와 같은 `calcResult`에 넣으므로 대시보드가 같은 코드로 그립니다 (`result` · `isDemo={false}`).
- 계산한 적이 없거나(404 `NOT_FOUND`) 서버에 연결할 수 없으면 알림 없이 지금 화면을 그대로 둡니다.

### 그룹 비교 통계 (F-06-02)

대시보드 · 통계 화면 진입 → `src/api/statistics-service.js`의 `fetchGroupStats(taxYear)` → `GET /api/group-stats/{taxYear}`

- 받은 값은 `groupStats` 상태에 넣고, 화면의 귀속 연도와 같을 때만 대시보드 · 통계 화면에 넘깁니다 (`serverGroups`).
- `utils/group-model.js`의 `groupComparison()`이 서버 값이 있으면 그것만 쓰고, 없으면(서버 연결 실패) 통계 스냅샷에서 찾습니다. 두 쪽을 섞지 않습니다 (서버가 못 찾은 그룹은 비워 둠).
- 화면 문구가 출처를 따라 바뀝니다: 「서버 통계 조회」 / 「제공 원본 파일 반영」 · 나의 결과는 「계산 결과」 / 「데모」.

### 청년 혜택 자격 진단 (F-03-02)

혜택 화면 → `src/api/benefit-service.js`의 `fetchBenefits(taxYear)` → `GET /api/benefits/{taxYear}`

- 응답의 `eligible`은 `true`(해당) · `false`(비해당) · `null`(정보 부족) 세 값을 그대로 보여 주고, 화면에서 다른 값으로 바꾸지 않습니다.
- 판정은 서버에 저장된 회원 정보로 합니다. 화면의 「나의 자격 정보」는 아직 브라우저 입력값이라, 두 값이 다를 수 있다는 안내를 함께 보여 줍니다.

### 조건 변경 시뮬레이션 · 결과 이력 (F-04-01 · F-04-02)

가상 입력 화면에서 계산 → `src/api/prediction-service.js`의 `simulate()` → `POST /api/simulations/{taxYear}` → 비교 화면

- 출발값은 서버에 저장된 소득(`GET /api/incomes/{taxYear}`)입니다. 없거나 연결이 안 되면 브라우저 입력값으로 시작합니다.
- 기준 결과는 시뮬레이션을 시작할 때 대시보드에 보이던 서버 계산 결과(`predictionId`)입니다. 없으면 요청하지 않고 「먼저 기존 정보로 계산해 주세요」를 띄웁니다.
- 요청 본문은 바꾼 값만이 아니라 입력값 전부입니다: 소득(`incomeRequest()` 재사용) · 무주택 세대주(`homelessHeadYn()`) · 카드 사용액(`cardSpends()`, 분류 × 결제수단 합계).
- 비교 화면은 기존 · 변경 후 모두 서버 값입니다. 서버 결과가 없으면 「비교할 결과가 없어요」 안내를 보여 줍니다.
- 비교 화면 아래 결과 이력: `fetchPredictions(taxYear)` → `GET /api/predictions?year=` · 「열기」 → `fetchPrediction(id)` → `GET /api/predictions/{id}`.

### 아직 연결 안 된 부분

- 카드 사용액: 업로드 내역이 서버(`EXPENSE`)에 저장되기 전까지 서버 계산에 들어가지 않습니다.
- 시뮬레이션의 무주택 세대주: 기존 결과는 회원 정보(`MEMBER`), 시뮬레이션은 화면 입력값이라 두 값이 다르면 월세 · 청약 공제가 다르게 나올 수 있습니다.
- 시뮬레이션의 카드 사용액: 기존 결과는 서버 `EXPENSE`로, 시뮬레이션은 화면의 업로드 거래 + 가상 추가액으로 계산합니다. 업로드가 `EXPENSE`에 저장되기 전에는 조건을 안 바꿔도 카드 공제가 다르게 나올 수 있습니다.
