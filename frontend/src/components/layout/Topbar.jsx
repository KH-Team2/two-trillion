import { getTaxYear } from '@/utils/tax-year.js';
import React from 'react';
import { ChevronRight, Bell, Menu, ChevronDown } from 'lucide-react';
import { nav } from '@/components/common/site-primitives.jsx';

// 기존 상단바의 구조·버튼·알림 상태를 유지합니다.
export default function Topbar({
  page,
  taxYear,
  data,
  mobile,
  setMobile,
  notification,
  setNotification,
  alerts = [],
  onReadAlerts,
  go,
}) {
  // [수정 11/20 · 이우진 · TT-151] 고정 안내 문구 → 서버 실시간 알림(SSE) 목록 · 안 읽은 알림이 있을 때만 점 표시
  const unread = alerts.filter((a) => !a.read).length;
  const toggle = () => {
    if (!notification) onReadAlerts?.(); // 열면 읽음 처리
    setNotification(!notification);
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
          className="bell"
          aria-label={unread ? `알림 (안 읽은 알림 ${unread}개)` : '알림'}
          onClick={toggle}
        >
          <Bell size={19} />
          {unread > 0 && <i />}
        </button>
        <button className="top-profile" onClick={() => go('profile')}>
          <span className="avatar">희</span>
          {data.name}
          <ChevronDown size={14} />
        </button>
      </div>
      {notification && (
        <div className="notification card">
          <b>알림</b>
          {alerts.length === 0 ? (
            <p>새 알림이 없어요. 계산이 끝나면 여기에 알려 드려요.</p>
          ) : (
            <ul className="notification-list">
              {alerts.map((a) => (
                <li key={a.id}>
                  <button
                    onClick={() => {
                      setNotification(false);
                      go('dashboard');
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
