# 작업 이력

최신 작업이 맨 위에 오도록 기록한다.

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
  - `prediction` 패키지에 둠 (PREDICTION · PREDICTION_INPUT · PREDICTION_DETAIL의 주인 · 시뮬레이션 · 챗봇도 같은 조회를 쓸 수 있게)
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