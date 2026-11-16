import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

// 배포 전용 런타임을 일반 Vite로 바꾸고 원본의 @ 경로 별칭을 유지합니다.
// 개발 서버 프록시: 화면이 부르는 /api/... 를 Spring 서버로 넘겨요 (브라우저 CORS 막힘 방지 · 개발용)
// 주소는 .env의 VITE_API_PROXY (없으면 http://localhost:8080)
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  return {
    plugins: [react()],
    server: {
      allowedHosts: ['terminal.local'],
      proxy: {
        '/api': { target: env.VITE_API_PROXY || 'http://localhost:8080', changeOrigin: true },
      },
    },
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  };
});
