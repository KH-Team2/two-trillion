# 작업 이력

최신 작업이 맨 위에 오도록 기록한다.

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
  - Thunder Client로 실제 DB 확인: PUT 200 · GET 200 · 2024 GET 400 `NO_CRITERIA`
  - 미검증: 화면(TaxInfoForm) 연결 (공통 `api-client.js`가 develop에 들어온 뒤 진행)