import { useEffect } from 'react';

// 원본 해시 라우터를 그대로 분리했습니다. #dashboard 등 주소와 뒤로/앞으로 가기를 유지합니다.
// features/how는 랜딩의 앵커이며 expenses는 원본처럼 input으로 연결됩니다.
export function useHashRouting({ setPage, setMode, setFormSeed, setMobile }) {
  useEffect(() => {
    const handle = () => {
      const route = location.hash.slice(1) || 'home';
      if (['features', 'how'].includes(route)) return;
      // [수정 11/23] 관리자 화면 브라우저 접근 기록(recordAccess) 삭제 · 이유: 체험판 기능 삭제 결정
      //   로그인 여부 검사(비로그인은 홈 · 로그인 · 회원가입만)는 App.jsx에서 해요
      if (route === 'expenses') {
        setMode('normal');
        setFormSeed((s) => s + 1);
        setPage('input');
        location.hash = 'input';
        return;
      }
      setPage(route);
      setMobile(false);
    };
    handle();
    window.addEventListener('hashchange', handle);
    return () => window.removeEventListener('hashchange', handle);
  }, []);
}
