# 작업 이력

최신 작업이 맨 위에 오도록 기록한다.

### [2026-11-12] 소득 API 보완 (PR #7과 비교 반영)
- **작업 내용**:
  - `IncomeRequestDTO`: 모든 입력 검사에 한글 문구(message) 추가
  - `IncomeResponseDTO` · `IncomeMapper.xml`: 응답에 `incomeId` · `taxYear` 추가
  - `IncomeServiceImpl`: 2000~2100 밖 연도는 DB 조회 없이 바로 400 `NO_CRITERIA`
  - `IncomeControllerTest`: 테스트 메서드 이름 영문화(@DisplayName에 한글 설명) · 한글 문구 · incomeId/taxYear 확인 추가
  - `IncomeServiceImplTest`: 범위 밖 연도 테스트 추가 · `README.md`: income 패키지 · 구현 상태 추가
- **변경 사유**:
  - 문구가 없으면 기본 문장("널이어서는 안됩니다" 등)이 화면에 나감 · API 명세서 소득 행 응답 칸과 맞춤
  - 같은 기능의 팀원 PR #7과 비교해 빠진 부분 반영
- **검증 결과**:
  - 화면(PR #10 화면 코드 · npm run dev)에서 「계산」 → `PUT /api/incomes/2026` 200 확인
  - (이번 보완 뒤 `mvnw spotless:check` · `mvnw -B verify` 결과를 여기에 적기)

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
  - 화면 연결은 위 보완 기록 참고 (PR #10 화면 코드로 PUT 200 확인)