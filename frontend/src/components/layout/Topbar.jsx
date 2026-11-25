import { getTaxYear } from '@/utils/tax-year.js';
import React, { useEffect, useRef, useState } from 'react';
import { ChevronRight, Bell, Menu, ChevronDown, UserRound, LogOut } from 'lucide-react';
import { nav } from '@/components/common/site-primitives.jsx';

// 기존 상단바의 구조·버튼·알림 상태를 유지합니다.
// 알림을 눌렀을 때 갈 수 있는 화면 (서버가 준 값을 그대로 쓰지 않고 이 목록 안에서만)
const ALERT_PAGES = ['dashboard', 'input', 'support'];

export default function Topbar({
  page,
  taxYear,
  data,
  mobile,
  setMobile,
  notification,
  setNotification,
  alerts = [],
  unreadCount,
  onReadAlerts,
  go,
  onLogout,
}) {
  // [수정 11/20 · 이우진 · TT-151] 고정 안내 문구 → 서버 실시간 알림(SSE) 목록 · 안 읽은 알림이 있을 때만 점 표시
  // [수정 11/23 · TT-159] 서버 안 읽은 개수가 있으면 그걸로 (목록 20개 밖의 안 읽은 알림도 셈) · 없으면 목록에서 셈
  const unread = Number.isInteger(unreadCount) ? unreadCount : alerts.filter((a) => !a.read).length;
  const toggle = () => {
    if (!notification) onReadAlerts?.(); // 열면 읽음 처리
    setNotification(!notification);
    setAccountOpen(false);
  };
  // [추가 11/23] 계정 버튼(이름 ▾) → 마이페이지 · 로그아웃 메뉴 (전에는 마이페이지로만 이동)
  const [accountOpen, setAccountOpen] = useState(false);
  const accountRef = useRef(null);
  useEffect(() => {
    if (!accountOpen) return undefined;
    // 메뉴 밖을 누르거나 Esc를 누르면 닫아요
    const closeOutside = (e) => {
      if (!accountRef.current?.contains(e.target)) setAccountOpen(false);
    };
    const closeEsc = (e) => e.key === 'Escape' && setAccountOpen(false);
    document.addEventListener('mousedown', closeOutside);
    document.addEventListener('keydown', closeEsc);
    return () => {
      document.removeEventListener('mousedown', closeOutside);
      document.removeEventListener('keydown', closeEsc);
    };
  }, [accountOpen]);
  const pick = (action) => {
    setAccountOpen(false);
    action();
  };
  return (
    <header className="topbar">
      <div>
        <button className="mobile-menu" aria-label="메뉴 열기" onClick={() => setMobile(!mobile)}>
          <Menu />
        </button>
        <span>나의 연말정산</span>
        <ChevronRight size={13} />
        <b>
          {nav.find((n) => n[0] === page)?.[1] ||
            (page === 'compare' ? '시뮬레이션 비교' : '관리자 콘솔')}
        </b>
      </div>
      <div>
        <span className="year">{taxYear ?? getTaxYear(data)}년 귀속</span>
        <button
          className={unread > 0 ? 'bell has-unread' : 'bell'}
          aria-label={unread ? `알림 (안 읽은 알림 ${unread}개)` : '알림'}
          onClick={toggle}
        >
          <Bell size={19} />
          {unread > 0 && <i aria-hidden="true">{unread > 9 ? '9+' : unread}</i>}
        </button>
        <div className="account-menu-wrap" ref={accountRef}>
          <button
            className="top-profile"
            aria-haspopup="menu"
            aria-expanded={accountOpen}
            onClick={() => {
              setAccountOpen(!accountOpen);
              setNotification(false);
            }}
          >
            <span className="avatar">희</span>
            {data.name}
            <ChevronDown size={14} />
          </button>
          {accountOpen && (
            <div className="account-menu card" role="menu">
              <button role="menuitem" onClick={() => pick(() => go('profile'))}>
                <UserRound size={15} />
                마이페이지
              </button>
              <button role="menuitem" onClick={() => pick(onLogout)}>
                <LogOut size={15} />
                로그아웃
              </button>
            </div>
          )}
        </div>
      </div>
      {notification && (
        <div className="notification card">
          <b>알림</b>
          {alerts.length === 0 ? (
            <p>새 알림이 없어요. 계산이 끝나거나 마감이 다가오면 여기에 알려 드려요.</p>
          ) : (
            <ul className="notification-list">
              {alerts.map((a) => (
                <li key={a.id}>
                  <button
                    onClick={() => {
                      setNotification(false);
                      // [수정 11/23 · TT-159] 알림마다 갈 화면(서버 LINK_URL) · 모르는 값이면 대시보드
                      go(ALERT_PAGES.includes(a.link) ? a.link : 'dashboard');
                    }}
                  >
                    <span>{a.message}</span>
                    <small>
                      {a.at.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}
                    </small>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </header>
  );
}
