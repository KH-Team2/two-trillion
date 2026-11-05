# 희망환급 React JSX 전체 소스 · 현재 배포 버전 13

현재 배포 버전 13의 실제 개발용 원본을 그대로 추출했습니다. v10 대시보드 UI를 복원한 최신 상태이며, 새 계산 API 데이터 구조와 기존 체험 기능을 유지합니다. 이번 추출에서는 기능·계산·화면을 수정하지 않았습니다. 기존 파란색·흰색 스타일, 공통 UI, 페이지와 해시 주소, 회원/관리자 체험은 유지합니다. 개발용 원본이며 압축된 빌드 코드가 아닙니다.

## 실행

Node.js 22.13 이상에서 ZIP의 `hope-refund` 폴더를 VS Code로 열고 실행합니다.

```bash
npm install
npm run format
npm run dev
```

터미널에 표시된 localhost 주소를 엽니다. 최초 동의서 버전 설정이나 API 서버 없이 기존 체험 기능을 이용할 수 있습니다.

```bash
npm run format:check
npm test
npm run build
npm run preview
```

`npm test`는 계정·업로드·문의 등 서비스 회귀, 피드백 계약, 16개 화면 서버 렌더 및 전체 101개 JS/JSX 모듈 번들 검사를 수행합니다. 실제 사용자 조작·백엔드·메일·Oracle 테스트를 대신하지 않습니다.

## 구조와 읽는 순서

- `src/App.jsx`: 입력·거래·원본/시뮬레이션·로그인 상태 연결.
- `src/pages/`: 기존 인증, 입력, 대시보드, 시뮬레이션, 통계, 챗봇, 혜택, 고객센터, 관리자, 마이페이지.
- `src/components/layout/`: 기존 Sidebar·Topbar·PublicHeader.
- `src/components/expense/ExpenseUpload.jsx`: CSV/XLSX, 다중 파일·삭제·중복 확인·결제수단 확인·분류 조회.
- `src/components/common/DeductionDetails.jsx`: 새 API 공제 내역, 0원·음수 조정액과 합계·근거 표시.
- `src/api/`: 기존 브라우저 서비스 및 향후 서버 DTO/응답 경계.
- `src/utils/tax-year.js`: 한국 시간 기본 귀속 연도 정책.
- `src/utils/benefit-model.js`: 입력 기준 예비 혜택 점검.
- `src/config/consent-versions.js`: 확정할 동의서 운영 버전.
- `src/data/statistics/`: 기존 2024년 공식 통계 원본/정제 자료. DB 필드와 원본 검증용 보조 메타데이터를 별도 JSON으로 구분.
- `src/styles/globals.css`: 기존 전체 CSS와 음수 공제 막대 스타일.
- `docs/FEEDBACK_REPORT.md`: 반영 사항·실제 수정 파일 전체 목록·보류 사항.
- `docs/API_INTEGRATION.md`: 서버 연결 순서·주의할 함수.
- `docs/VERIFICATION.md`: 검증 결과와 미검증 범위.
- `docs/SOURCE_MAP.md`, `docs/source-provenance.json`: 버전 10 원본 추출 경로 이력.

원본은 React Router 라이브러리 대신 `#dashboard`, `#input`, `#compare`, `#guide`, `#benefits`, `#stats`, `#profile`, `#support`, `#admin`, `#login`, `#signup`, `#reset` 해시 라우팅을 사용했습니다. `src/routes/useHashRouting.js`에 이를 유지했습니다.

## 데이터·계산의 현재 상태

- 실제 연동: 제공된 2024년 국세통계 정적 파일만 읽습니다. 은행·국세청 조회 API, Spring Boot, Oracle, 실제 이메일, Gemma3는 미연동입니다.
- React 상태: 편집 중 입력, 단계, 모달/펼침, 시뮬레이션 비교.
- LocalStorage/SessionStorage: 계정 체험, 사용자별 입력·거래·업로드 메타·계산 이력·시나리오·상담방·문의·관리 설정. 운영 보안/권한 경계가 아닙니다.
- 예시 데이터: `src/api/services.js`의 `initial`, `merchantService` 목록, `sampleTransactions`, `aiService` 고정 응답. 초기 카드 금액은 0이며 실제 사용액은 업로드로만 생성합니다.
- 체험 계산: `taxService.calculate()`. 기존 체험 산식을 유지하고 반환 계약만 새 필드명으로 통일했습니다. 새로 나눈 보험료도 체험 결과에 반영합니다. 이 계산은 법정 산출세액·한도·혜택 적용 선택을 구현하지 않습니다.
- 과세표준: 기존 엔진에 해당 계산이 없으므로 체험 결과에서는 `taxableIncome: null`과 ‘계산 API 연동 후 제공’을 표시합니다. 실제 응답에는 숫자를 요구하며 숫자가 오면 그대로 표시합니다. 근로소득공제·카드 6종·한도 조정·표준세액공제는 API가 제공하는 모든 행을 표시할 수 있으나 체험 엔진이 법정 결과를 만들어내지는 않습니다.
- 그룹 평균에 사용자 결과를 합산하지 않습니다. 2024년 공식 자료 연도는 유지하고 사용자의 선택 귀속 연도는 별도로 표시합니다.

## 팀에서 확인할 사항

`.env.example`을 `.env`로 복사해 팀이 확정한 `VITE_TERMS_VERSION`, `VITE_PRIVACY_VERSION`, `VITE_FINANCE_VERSION`을 설정할 수 있습니다. 임의 운영 버전은 설정하지 않았습니다. 미설정 상태에서는 로컬 체험 가입을 유지하지만 `consentDTO()`가 서버 저장 요청용 객체 생성을 차단합니다. 기존 가입자의 미확정 동의 이력에 새 버전을 소급 적용하지 않습니다. 실제 동의 시각과 버전 검증은 서버 책임입니다.

v3.9 보험료 컬럼명, 시뮬레이션 저장 컬럼, 실제 `CATEGORY_ID`·서버 발급 `UPLOAD_ID` 매핑은 팀 확인이 필요합니다. `insuranceInput()`은 프론트 필드이며 DB 컬럼 확정이 아닙니다. `MEMBER_ID`는 백엔드가 인증정보에서 결정해야 합니다.

예전 통합 보험료와 연간 월세는 임의 분할·12분의 1 환산하지 않습니다. 원래 값을 보존하고 다시 확인해 입력하도록 안내합니다. 예전 결과 이력은 읽기 경계에서 새 필드로 호환합니다. 기존 사이트와 localhost는 저장소가 달라 실제 브라우저 데이터가 자동 복사되지 않습니다.

ZIP에는 전체 원본 소스, CSS·SVG·통계 파일, lockfile, Vite·Prettier·테스트·문서를 포함하고 `node_modules`, `dist`, Git 메타데이터·배포 자격증명·개인 입력 데이터는 제외합니다.

## 이번 추출의 기준과 차이

- 기준: https://hope-refund-young.uio8130.chatgpt.site 의 배포 버전 13.
- 원본 커밋: `4aca76f4fbc72110f08cf1209d2338700f7b2baa`.
- 추출일: 2026-10-31 (한국 시간). 사이트 재배포나 소스 변경은 하지 않았습니다.
- 원래부터 JS/JSX + Vite이므로 TypeScript를 재변환하거나 컴포넌트를 재구성하지 않았습니다.
- 현재 해시 주소 라우팅을 유지했습니다. 원본에 없는 React Router 라이브러리를 추가하지 않았습니다.
- 포맷팅과 추출 안내 문서만 정리합니다. `src/`의 동작·데이터·스타일은 원본과 비교해 확인합니다.
- `.openai/hosting.json`도 원본 설정으로 포함했지만 localhost 실행에는 사용되지 않습니다. 배포용 인증정보는 포함하지 않습니다.
- Noto Sans KR 폰트는 원본처럼 Google Fonts를 사용하므로 동일 폰트를 불러오려면 인터넷 연결이 필요합니다.
- localhost의 브라우저 저장소는 사이트 도메인과 다릅니다. 배포 사이트에 입력한 개인 데이터가 ZIP에 포함되거나 자동 이전되지는 않습니다.
- `docs/FEEDBACK_REPORT.md`, `SOURCE_MAP.md`, `VERIFICATION.md`는 이전 작업의 기록입니다. 이번 추출 검증은 `docs/EXPORT_VERIFICATION.md`를 참고하세요.
