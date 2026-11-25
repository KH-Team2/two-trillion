# 작업 이력

최신 작업이 맨 위에 오도록 기록한다.

### [2026-11-25] develop 병합(#60 feature/member · #59 · #61) · 회원 기능의 체험판 코드 정리
- **작업 내용**:
  - develop을 remove-demo에 병합: 윤경님 #60 `feature/member`(아이디 찾기 · 비밀번호 재설정 · 회원정보 수정 · 비밀번호 변경 · 회원 탈퇴 서버 연결)와 충돌난 4개 파일 정리
  - `account-service.js`: 서버 탈퇴(`withdraw` → `memberApi.withdrawMe`)만 남기고 빠른 체험 · 브라우저 탈퇴 분기, `LOCK_MS` · `ACCOUNT_KEY`(브라우저 체험 계정 전용 상수) 삭제
  - `AccountForms.jsx`: member 버전 기준으로 아이디 찾기 · 비밀번호 재설정(`AccountRecovery`) · 이메일 인증 환경변수(`VITE_EMAIL_VERIFICATION_REQUIRED`) · 동의서 버전 표시 · 서버 탈퇴(`WithdrawAccount`)는 그대로 두고, 체험 부분(`notice` · 최초 관리자 설정 `setup` · 브라우저 가입/로그인/중복확인/인증번호 · `recordAccess` · `MemberManagement` · 체험 계정 탈퇴 안내) 삭제 · `demoCode` → `devCode`
  - `ProfileDetails.jsx`: remove-demo에서 지웠던 파일을 다시 살림 · 서버 화면(`ServerProfileDetails` · `ServerPasswordChange`)만 남기고 빠른 체험 폼(`ProfileDetails` 함수) 삭제
  - `ProfilePage.jsx`: 개인정보 · 비밀번호 변경 · 계정 관리(탈퇴) 탭을 「준비 중」 → 서버 화면으로 · 「브라우저 내 저장」 표시 · 시연용 비밀번호 변경 폼 삭제 · 탈퇴 후에는 로그아웃과 같이 로그인 화면으로
  - `App.jsx`: `ProfilePage`에 `setData` · `setSession` · `go` · `notify` 전달 (회원정보 저장 · 탈퇴 · 알림에 필요)
  - `render-pages.mjs`: 「준비 중」 검사 목록에서 `reset` 제외 (재설정 화면은 서버 연결 화면인지 따로 검사)
  - 문서: `frontend/README.md` 화면 표 · 「준비 중」 목록, `docs/API_INTEGRATION.md` 회원 기능 설명
- **변경 사유**:
  - 회원 기능(F-01-03 · 04 · 05)이 서버에 연결됐는데 remove-demo는 그 화면을 「준비 중」으로 바꾸고 `ProfileDetails.jsx`를 지워서 충돌함 → 서버 기능은 살리고 체험 코드만 지움
- **검증 결과**:
  - `npm test` 통과 (render-pages 포함 · 작업 환경에서는 `lucide-react` 아이콘을 빈 그림으로 바꿔서 실행) · esbuild 번들 성공 · ESLint 새 오류 없음 · 고친 파일 prettier 통과
  - `npm test` 120건 통과 · `npm run build` 통과 (11/25 · `npm install`로 #59의 `@stomp/stompjs` 설치 후)
  - 브라우저: 아이디 찾기 · 비밀번호 재설정 · 회원정보 조회 · 수정 · 비밀번호 변경 · 탈퇴 후 로그인 화면 이동 확인

### [2026-11-25] 체험판 기능 삭제 · 로그인 회원만 사용 (develop d09099a 기준 · #51 · #53 · #56 반영)
- **작업 내용**:
  - 로그인 검사: 로그인하지 않으면 홈 · 로그인 · 회원가입 · 아이디 찾기/비밀번호 재설정만 볼 수 있음 · 다른 주소는 로그인 화면으로 (`App.jsx` `PUBLIC_PAGES`) · 로그인한 회원이 로그인 · 가입 주소로 오면 대시보드로
  - 홈 화면: 「체험 먼저 해보기」 · 「예시 대시보드 둘러보기」 등 체험 진입 버튼 → 로그인 · 회원가입으로만 이동 · 공개 상단바의 「고객센터」 버튼 삭제
  - 체험 계정 삭제: `account-service.js`를 서버 로그인 · 로그아웃만 남김 (빠른 체험 · 브라우저 가입 · 체험 인증번호 · 관리자 회원 관리 · 성별 수정 · 비밀번호 변경 · 탈퇴 삭제) · `AccountForms.jsx`는 서버 가입 · 로그인만
  - 체험용 계산 삭제: `services.js`의 `taxService` · `sampleTransactions` · `merchantService` · `parseCSV` · `aiService`, `api-client.js`의 `canUseDemo` · `demoFallbackAllowed`, 화면의 `isDemo` · `calculationConnected` 분기(Dashboard · SimulationPage · GroupStats · DeductionDetails · group-model · dashboard-model), 혜택 진단 연결 실패 때 체험 판정(`benefit-model.js` 파일 삭제)
  - 서버 결과가 없으면: 대시보드 · 통계 · 비교 화면 대신 「먼저 계산해 주세요」 안내(`App.jsx` `NeedResult`) · 계산 · 시뮬레이션 오류는 안내 후 입력 화면에 머묾
  - 서버 API 전 화면은 「준비 중」(`components/common/ComingSoon.jsx` 추가): AI 절세 가이드 화면 · 대시보드 상담 카드(세금 용어는 #52 FAQ 챗봇 안내), 관리자의 회원 관리 · 공제 기준값 · 예측 결과 · 접근 기록 탭, 마이페이지 개인정보 · 비밀번호 변경 · 계정 관리(탈퇴), 비밀번호 재설정
  - 고객센터 · 관리자 문의 관리(PR #53 서버 API)는 그대로 사용: `SupportPage.jsx`는 #53 버전에 FAQ 답변의 체험 문구만 수정 · `AdminTools.jsx`는 서버 문의 관리(`InquiryManagement`)만 남기고 브라우저 시연 도구(기준값 · 접근 기록 · 예측 결과) 삭제 · `AdminPage.jsx`는 탭 상태를 직접 관리하고 「공공데이터 목록」(브라우저 예시 목록) 탭 삭제
  - 삭제한 파일: `api/prototype-records.js`(브라우저 문의 · 기준값 · 접근 기록), `pages/chatbot/ChatPanel.jsx`, `pages/profile/ProfileDetails.jsx`, `utils/benefit-model.js`
  - `expense-service.js`: 브라우저 파일 해석(`parseUploadTable` · `parseUploadCSV` · `parseUploadFile`)과 예시 상호명 분류 삭제 (업로드 · 분류는 서버) · `ExpenseUpload.jsx`: 서버에 없는 예전 체험 거래를 남기던 부분 · `recordAccess` 삭제
  - 마이페이지 「저장한 시뮬레이션」 탭 삭제 (시나리오는 서버 저장 · 비교 화면 결과 이력에서 확인) · 「이전 계산 결과」는 서버 결과만
  - 화면 문구: 「시연」 · 「체험」 · 「데모」 · 「미연동」 안내를 지금 서비스(서버 계산)에 맞게 수정 (입력 화면 확인 체크박스 · FAQ · 이용약관 초안 문구 포함)
  - 테스트: `prototype-flows.mjs` · `feedback-contract.mjs` · `calculation-service.mjs` · `render-pages.mjs`에서 삭제된 체험 코드 검사를 빼고, 서버 결과 모양으로 다시 작성 · 화면에 체험 · 시연 문구가 남지 않는지, 준비 중 화면이 안내를 보여 주는지, 관리자 문의 관리가 준비 중으로 보이지 않는지 검사 추가
  - 알림(`App.jsx`): #51(TT-159 저장된 알림 목록 · 이어 받기) 코드를 그대로 두고, `canUseDemo` · `demoFallbackAllowed` import와 「체험 계정」 주석만 정리
  - 문서: 최상위 `README.md` 구조도, `frontend/README.md`, `frontend/AGENTS.md`, `docs/API_INTEGRATION.md`, `docs/PROJECT_STRUCTURE.md`
  - 고치지 않은 문서: `docs/VERIFICATION.md` · `docs/FEEDBACK_REPORT.md` · `docs/EXPORT_VERIFICATION.md` · `docs/api_integ_logs/*`는 그때의 작업 기록이라 체험 관련 문장도 그대로 둠 (`API_INTEGRATION.md` 맨 위에 「지난 기록」 안내)
- **변경 사유**:
  - 팀 결정(11/23): 체험판 기능 자체를 삭제 · 비회원은 회원가입만, 비로그인 회원은 로그인만 사용
  - 체험 기능은 브라우저에만 저장되는 가짜 데이터라 서버 기능과 섞여 보이면 실제로 동작하는 것처럼 오해할 수 있음
- **검증 결과**:
  - `npm test` 116건 통과 · `npm run build` 통과 · 백엔드 `mvnw verify` 215건 통과 (11/25 · develop d09099a 병합 후)
  - `prettier --check --end-of-line auto "src/**/*.{js,jsx,css,json}" "tests/*.mjs"`: 이번에 고친 파일은 모두 통과 · `faq-service.js` · `components/chatbot/Faq*.jsx` · `styles/chatbot.css` 5개는 develop(#52)에 원래 있던 것이라 고치지 않음
  - ESLint(no-undef · no-unused-vars) 검사: 새 오류 없음 (남은 경고는 develop에 원래 있던 것)
  - import 검사 스크립트: 지운 파일 · 이름을 가져오는 곳 없음
  - 브라우저 (백엔드 · 계산 서버 · 챗봇 서버 실행):
    - 로그인 전 `#dashboard` → 로그인 화면 이동 · 홈 버튼은 로그인 · 회원가입으로만 이동 · 새 회원가입 → 대시보드 「아직 계산 결과가 없어요」 · 소득 입력 → 계산 → 대시보드
    - 카드 업로드 · 통계 · 시뮬레이션: 서버 결과로 표시 (개발자 도구 Network에서 `/api/calculations` · `/api/group-stats` · `/api/simulations` 200 확인)
    - 고객센터 문의 작성 · 관리자 문의 관리(#53) · 새로고침 후 알림 목록 유지(#51)
    - 「준비 중」 화면: AI 절세 가이드 · 관리자 회원 관리 · 공제 기준값 · 예측 결과 · 접근 기록 탭 · 마이페이지 개인정보 · 비밀번호 변경 · 계정 관리 · 로그아웃 후 `#dashboard` → 로그인 화면
  - 참고: 윈도우에서는 `prettier --check`가 거의 모든 파일을 경고함 → Git이 줄바꿈을 CRLF로 바꿔서 받기 때문 (팀 설정은 `endOfLine: lf` · 커밋하면 LF로 올라가서 코드 문제 아님) · `--end-of-line auto`로 확인하거나 루트 `.gitattributes`(`* text=auto eol=lf`) 추가를 팀에 제안
  - 참고: 확인 중 계산 서버가 DB에 접속하지 못함 (`DPY-4027` → `ORA-01045`) → 코드 문제가 아니라 PC 설정 문제
    - 윈도우 환경변수의 `DB_USERNAME` · `DB_DSN`이 `calc/.env` 값을 덮음 (`load_dotenv()`는 이미 있는 환경변수를 덮어쓰지 않음) → 환경변수 삭제
    - 로컬 DB는 `XE`에 권한이 있는데 `DB_DSN`이 `.env.example` 예시 `XEPDB1`을 가리킴 → `127.0.0.1:1521/XE`로 수정
  - 참고: 확인 중 백엔드 로그에 `HttpMessageNotWritableException … text/event-stream` 경고가 나옴 → SSE 알림 연결이 끊길 때 공통 오류 처리기가 JSON 응답을 쓰려다 나는 것으로, 이번 변경(프론트만)과 관계없음 · SSE 담당자에게 따로 전달
  - 참고: `xlsx` 패키지는 이제 `src`에서 쓰지 않음 (package.json에서 뺄지는 팀 확인 후)
