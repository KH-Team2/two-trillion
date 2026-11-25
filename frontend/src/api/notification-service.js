// 알림 목록 · 읽음 API 연결 (TT-159) · 서버: GET /api/notifications · PUT /api/notifications/read (TT-158)
// 알림은 서버 NOTIFICATION 에 저장돼 있어서, 새로고침 · 다른 기기에서도 같은 목록이 보여요.
import { apiRequest } from './api-client.js';

/** 서버 알림 한 건(NotificationDTO) → 종 아이콘 목록 한 줄 · 번호가 없으면(서버 저장 실패) 화면에서 임시 번호 */
export function toAlert(n) {
  return {
    id: Number.isInteger(n?.notificationId) ? n.notificationId : `live-${Date.now()}`,
    message: String(n?.content ?? '').trim(),
    link: typeof n?.linkUrl === 'string' && n.linkUrl ? n.linkUrl : null,
    at: n?.createdAt ? new Date(n.createdAt) : new Date(),
    read: n?.readYn === 'Y',
  };
}

/** 내 알림: { unreadCount, items[] } (items 는 새것부터 최대 20개) */
export async function fetchNotifications(options = {}) {
  return apiRequest('/api/notifications', {
    ...options,
    offlineMessage: '알림 서버에 연결할 수 없어요.',
  });
}

/** 안 읽은 알림을 모두 읽음으로 (종 아이콘 목록을 열 때) */
export async function markNotificationsRead(options = {}) {
  return apiRequest('/api/notifications/read', {
    ...options,
    method: 'PUT',
    offlineMessage: '알림 서버에 연결할 수 없어요.',
  });
}
