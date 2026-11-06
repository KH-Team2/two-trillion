# 남은 작업 (backend · 세액 계산·진단 파트)

보안 점검(11/06)에서 나온 것 중 아직 안 한 항목과 이유.

| 항목 | 상태 | 이유 · 언제 |
|---|---|---|
| Content-Type이 JSON이 아닌 요청을 415로 (지금은 공통 처리기에서 500) | 예정 | `GlobalExceptionHandler`가 공통 파일이라 형상관리자에게 알린 뒤 처리기 1개 추가 |
| `calc.mode=http`인데 `CALC_API_TOKEN`이 비어 있으면 시작 시 경고 | 예정 | 설정 누락을 빨리 알게 · `HttpCalcClient` 생성자에서 검사 |
| 회원번호를 `X-Member-Id` 헤더 대신 로그인 정보에서 꺼내기 | 대기 | Spring Security는 회원·보안 담당 일정에 맞춤. 그 전까지 헤더 방식은 개발용 |
| 계산 이력 조회 API를 만들 때 `PREDICTION_DETAIL`은 `PREDICTION`과 JOIN해 본인 확인 | 메모 | 아직 조회 API 없음 |
