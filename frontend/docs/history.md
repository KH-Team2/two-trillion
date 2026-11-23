# 작업 이력

최신 작업이 맨 위에 오도록 기록한다.

### [2026-11-23] 체험판 기능 삭제 · 로그인 회원만 사용
- **작업 내용**:
  - 로그인 검사: 로그인하지 않으면 홈 · 로그인 · 회원가입(· 비밀번호 재설정 안내)만 볼 수 있음 · 다른 주소는 로그인 화면으로 (`App.jsx` `PUBLIC_PAGES`) · 로그인한 회원이 로그인 · 가입 주소로 오면 대시보드로
  - 홈 화면: 「체험 먼저 해보기」 · 「예시 대시보드 둘러보기」 등 체험 진입 버튼 → 로그인 · 회원가입으로만 이동 · 공개 상단바의 「고객센터」 버튼 삭제
  - 체험 계정 삭제: `account-service.js`를 서버 로그인 · 로그아웃만 남김 (빠른 체험 · 브라우저 가입 · 체험 인증번호 · 관리자 회원 관리 · 성별 수정 · 비밀번호 변경 · 탈퇴 삭제) · `AccountForms.jsx`는 서버 가입 · 로그인만
  - 체험용 계산 삭제: `services.js`의 `taxService` · `sampleTransactions` · `merchantService` · `parseCSV` · `aiService`, `api-client.js`의 `canUseDemo` · `demoFallbackAllowed`, 화면의 `isDemo` · `calculationConnected` 분기(Dashboard · SimulationPage · GroupStats · DeductionDetails · group-model · dashboard-model), 혜택 진단 연결 실패 때 체험 판정(`benefit-model.js` 파일 삭제)
  - 서버 결과가 없으면: 대시보드 · 통계 · 비교 화면 대신 「먼저 계산해 주세요」 안내(`App.jsx` `NeedResult`) · 계산 · 시뮬레이션 오류는 안내 후 입력 화면에 머묾
  - 서버 API 전 화면은 「준비 중」(`components/common/ComingSoon.jsx` 추가): AI 상담(챗봇 화면 · 대시보드 상담 카드), 고객센터 문의 작성 · 내 문의, 관리자 콘솔, 마이페이지 개인정보 · 비밀번호 변경 · 계정 관리(탈퇴), 비밀번호 재설정
  - 삭제한 파일: `api/prototype-records.js`(브라우저 문의 · 기준값 · 접근 기록), `pages/admin/AdminTools.jsx`, `pages/chatbot/ChatPanel.jsx`, `pages/profile/ProfileDetails.jsx`, `utils/benefit-model.js`
  - `expense-service.js`: 브라우저 파일 해석(`parseUploadTable` · `parseUploadCSV` · `parseUploadFile`)과 예시 상호명 분류 삭제 (업로드 · 분류는 서버) · `ExpenseUpload.jsx`: 서버에 없는 예전 체험 거래를 남기던 부분 · `recordAccess` 삭제
  - 마이페이지 「저장한 시뮬레이션」 탭 삭제 (시나리오는 서버 저장 · 비교 화면 결과 이력에서 확인) · 「이전 계산 결과」는 서버 결과만
  - 화면 문구: 「시연」 · 「체험」 · 「데모」 · 「미연동」 안내를 지금 서비스(서버 계산)에 맞게 수정 (입력 화면 확인 체크박스 · FAQ · 이용약관 초안 문구 포함)
  - 테스트: `prototype-flows.mjs` · `feedback-contract.mjs` · `calculation-service.mjs` · `render-pages.mjs`에서 삭제된 체험 코드 검사를 빼고, 서버 결과 모양으로 다시 작성 · 화면에 체험 · 시연 문구가 남지 않는지, 준비 중 화면이 안내를 보여 주는지 검사 추가
  - 문서: 최상위 `README.md` 구조도, `frontend/README.md`, `frontend/AGENTS.md`, `docs/API_INTEGRATION.md`, `docs/PROJECT_STRUCTURE.md`
- **변경 사유**:
  - 팀 결정(11/23): 체험판 기능 자체를 삭제 · 비회원은 회원가입만, 비로그인 회원은 로그인만 사용
  - 체험 기능은 브라우저에만 저장되는 가짜 데이터라 서버 기능과 섞여 보이면 실제로 동작하는 것처럼 오해할 수 있음
- **검증 결과**:
  - `prettier --check "src/**/*.{js,jsx,css,json}" "tests/*.mjs"` 통과
  - ESLint(no-undef · no-unused-vars) 검사: 새 오류 없음 (남은 경고는 develop에 원래 있던 것)
  - import 검사 스크립트: 지운 파일 · 이름을 가져오는 곳 없음
  - 미검증: `npm test` · `npm run build` · 브라우저 화면 확인 (작업 환경에 node_modules 설치 불가) → PR 전에 꼭 실행 필요
  - 참고: `xlsx` 패키지는 이제 `src`에서 쓰지 않음 (package.json에서 뺄지는 팀 확인 후)
