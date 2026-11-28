import React from 'react';
import { ArrowRight, LogOut, ShieldCheck, Leaf } from 'lucide-react';
import { nav, Logo } from '@/components/common/site-primitives.jsx';

// 기존 메뉴·사용자 정보·로그아웃을 포함한 사이드바입니다.
export default function Sidebar({ page, data, mobile, admin, go, onLogout, normalInput }) {
  return (
    <aside className={'sidebar ' + (mobile ? 'open' : '')}>
      <button className="bare" onClick={() => go('home')}>
        <Logo />
      </button>
      <div className="workspace-label">MY TAX WORKSPACE</div>
      <nav>
        {nav.map(([id, label, Icon]) => (
          <button
            key={id}
            onClick={() => (id === 'input' ? normalInput() : go(id))}
            className={page === id || (page === 'compare' && id === 'dashboard') ? 'active' : ''}
          >
            <Icon size={19} />
            {label}
            {page === id && <i />}
          </button>
        ))}
      </nav>
      {admin && (
        <button
          className={'admin-link ' + (page === 'admin' ? 'active' : '')}
          onClick={() => go('admin')}
        >
          <ShieldCheck size={18} />
          관리자 콘솔
        </button>
      )}
      <div className="sidebar-bottom">
        <div className="tip">
          <span className="iconbox">
            <Leaf size={19} />
          </span>
          <b>작은 준비, 더 큰 환급</b>
          <p>
            놓치기 쉬운 혜택을
            <br />
            미리 확인해보세요.
          </p>
          <button onClick={() => go('benefits')}>
            청년 혜택 알아보기 <ArrowRight size={14} />
          </button>
        </div>
        <button className="sidebar-user" onClick={() => go('profile')}>
          <span className="avatar">{data.name.slice(-2, -1) || '희'}</span>
          <span>
            <b>{data.name}</b>
            <small>
              {/* [수정 11/23] 「 · 체험」 · 「비로그인 체험」 표시 삭제 · 이유: 체험판 기능 삭제 결정
                  (로그인하지 않으면 이 화면에 들어올 수 없어요) */}
              {admin ? '관리자' : '일반 회원'}
            </small>
          </span>
          {/* [수정 11/23] ▾ 삭제 · 메뉴가 열리는 것처럼 보이지만 마이페이지로만 이동했음 (메뉴는 상단 계정 버튼) */}
        </button>
        <button className="logout" onClick={onLogout}>
          <LogOut size={14} />
          로그아웃
        </button>
      </div>
    </aside>
  );
}
