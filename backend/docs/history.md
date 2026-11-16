# 작업 이력

최신 작업이 맨 위에 오도록 기록한다.

### [2026-11-16] 결과 이력 조회 API (F-04-02)
- **작업 내용**:
  - `GET /api/predictions?year=2026`: 본인의 그 연도 결과 전부(현재 · 시나리오) 최신순 · 없으면 빈 목록(오류 아님)
  - `GET /api/predictions/{predictionId}`: 하나 다시 열기 (대시보드와 같은 모양 · 항목별 내역 포함) · 없거나 남의 것이면 404
  - 새 `PredictionSummaryDTO` · `PredictionController` · 매퍼 `findPredictions` · `findPrediction` · `CalculationService.getHistory()` · `getOne()`
- **변경 사유**:
  - 기능명세서 F-04-02 · 주소는 팀 규칙(목록 거르기는 `?` 뒤 · 그 한 건은 경로)
- **검증 결과**:
  - 테스트 3개 추가 · curl: 회원 81 목록 3건 · 6번 열기 200 · 82번이 6번 열기 404

### [2026-11-15] 조건 변경 시뮬레이션 API (F-04-01)
- **작업 내용**:
  - `POST /api/simulations/{taxYear}`: 바꾼 입력값(소득 칸 전부 · 무주택 여부 · 카드 분류별 금액)으로 세액 계산(F-03-01)을 다시 하고 `PREDICTION`에 시나리오 이름(`SCENARIO_NAME`) · 기준 결과 번호(`BASE_PREDICTION_ID`)를 채워 저장
  - 기준 결과는 본인의 같은 연도 결과만 (`countOwnPrediction`) · 아니면 404 (403이 아니라 존재 여부를 숨김) · 자녀 수 > 부양가족 수는 400
  - `PredictionSaveService.saveScenario()` 추가 (결과 1행 INSERT만 다르고 입력값 · 상세 저장은 기존과 같은 함수 · 같은 트랜잭션)
  - 요청 검사: `SimulationRequestDTO`(`income` · `cardSpends`에 `@Valid`) · `CardSpendDTO`에 분류 1 ~ 5 · 결제수단 · 금액 검사 (DB 합계는 `@Valid`를 거치지 않아 기존 계산에 영향 없음)
- **변경 사유**:
  - 기능명세서 F-04-01 · 원래 소득(`INCOME`)과 카드 내역(`EXPENSE`)은 읽지도 고치지도 않음 (입력값은 결과마다 `PREDICTION_INPUT`에만)
- **검증 결과**:
  - 테스트 4개 추가 · curl mock 모드 200 · 404 · 400
  - 학원 PC에서 계산 서버(http) + 저장: 회원 81 기준 결과 3번 → 시나리오 6번 · `PREDICTION_INPUT` 월세 60만 · `INCOME` 월세는 그대로

### [2026-11-13] 그룹 비교 통계 조회 API (F-06-02)
- **작업 내용**:
  - `GET /api/group-stats/{taxYear}`: 본인의 성별·연령대 그룹과 총급여 구간 그룹의 국세통계 평균(인원 · 1인당 총급여 · 결정세액 · 환급세액) · 못 찾은 그룹은 그 칸만 null(200)
  - 새 패키지 `statistics` (`GROUP_STAT` 읽기만)
  - SQL 규칙: 통계 연도는 귀속 연도 이하 최근(지금 2024) · 나이 = 귀속 연도 − 태어난 해 · `AGE_MIN` · `AGE_MAX` NULL이면 제한 없음 · 급여는 `SALARY_MIN` 초과 ~ `SALARY_MAX` 이하이고 `SALARY_MIN`에는 「NULL이면 통과」를 붙이지 않음 (「1억 초과(결정세액 없는 자)」 행이 누구에게나 걸리는 것 방지)
- **변경 사유**:
  - 화면이 브라우저 입력값과 화면 안 통계 파일로 그룹을 찾고 있었음 → 서버에 저장된 회원 정보 · 소득으로 찾도록
- **검증 결과**:
  - 테스트 6개 추가 (Service 3 · Controller 3)
  - 학원 DB: 회원 81 → 여성 40대 · 4천만 이하 · 82 → 여성 50대 · 2천만 이하 · 없는 회원 → 200에 둘 다 null
  - 화면 스냅샷과 평균이 조금 다름: 나누는 인원 칸이 다름 (DB는 「과세대상근로소득」 인원 · 스냅샷은 「급여총계」 인원) · DB는 고칠 것 없음

### [2026-11-12] 저장된 계산 결과 조회 API (F-06-01 결과 대시보드)
- **작업 내용**:
  - `GET /api/calculations/{taxYear}`: 본인의 그 연도 계산 결과 중 가장 최근 1건 + 항목별 공제 내역 · 없으면 404 `NOT_FOUND`
  - 응답은 계산(POST)과 같은 `CalculationResponseDTO` · 시뮬레이션 결과(`BASE_PREDICTION_ID`가 있는 행)는 빼고 기준 결과만
  - `prediction` 패키지에 둠 (PREDICTION · PREDICTION_INPUT · PREDICTION_DETAIL의 주인 · 시뮬레이션도 같은 조회를 쓸 수 있게)
- **변경 사유**:
  - 전에는 계산 직후 응답과 브라우저 저장에만 결과가 있어서, 다른 브라우저로 들어오거나 브라우저 저장이 지워지면 대시보드가 저장된 결과를 못 보여 줌
- **검증 결과**:
  - 테스트 4개 추가 (Service 2 · Controller 2) · 「계산은 POST만 받는다」 → 「GET과 POST만 받는다」(PUT → 405)
  - curl: 회원 81 → 200 · 82 → 404

### [2026-11-12] 소득 API 보완 (PR #7과 비교 반영)
- **작업 내용**:
  - `IncomeRequestDTO`: 모든 입력 검사에 한글 문구(message) 추가
  - `IncomeResponseDTO` · `IncomeMapper.xml`: 응답에 `incomeId` · `taxYear` 추가
  - `IncomeServiceImpl`: 2000~2100 밖 연도는 DB 조회 없이 바로 400 `NO_CRITERIA`
  - `IncomeControllerTest`: 테스트 메서드 이름 영문화(@DisplayName에 한글 설명) · 한글 문구 · incomeId/taxYear 확인 추가
  - `IncomeServiceImplTest`: 범위 밖 연도 테스트 추가
  - develop 최신 README · .gitignore 반영 (README의 소득 항목은 develop에 이미 있어 따로 수정하지 않음)
- **변경 사유**:
  - 검사 문구가 없으면 기본 문장("널이어서는 안됩니다" 등)이 화면에 그대로 나감
  - API 명세서 소득 행의 응답 칸(incomeId · taxYear)과 맞춤
  - 같은 기능의 팀원 PR #7과 비교해 빠진 부분 반영
- **검증 결과**:
  - `mvnw spotless:check` · `mvnw -B verify` 통과
  - Thunder Client로 실제 DB 확인: PUT 200 · GET 200 · 2024 GET 400 `NO_CRITERIA`
  - 화면(PR #10 화면 코드 · npm run dev)에서 「계산」 → `PUT /api/incomes/2026` 200 확인 (뒤이은 계산 API는 이 브랜치에 없어 404 · 정상)

### [2026-11-12] 소득 입력·수정 API (F-02-01)
- **작업 내용**:
  - `GET /api/incomes/{taxYear}` 소득 조회 · `PUT /api/incomes/{taxYear}` 소득 저장 추가
  - `income/` controller · service · mapper · dto, `mapper/income/IncomeMapper.xml`, 컨트롤러 · 서비스 테스트
  - 저장은 MERGE (그 연도 행이 있으면 UPDATE, 없으면 INSERT)
  - 검증: 총급여 · 기납부세액 필수, 모든 금액 0 이상, 20세 이하 자녀 수 ≤ 부양가족 수
  - 연도 검사: DB 기준값(`DEDUCTION_TYPE`)이 있는 연도만 받음 (없으면 400 `NO_CRITERIA`)
- **변경 사유**:
  - 기능명세서 F-02-01 구현
  - 연도 검사를 코드 고정값(2025 · 2026) → DB 기준값으로 변경: 관리자가 새 연도 기준값을 넣으면 코드 수정 없이 열리게 (팀원 리뷰 반영)
- **검증 결과**:
  - `mvnw spotless:check` · `mvnw -B verify` 통과
  - Thunder Client로 실제 DB 확인: PUT 200 · GET 200