import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    // Uncomment if you want Vite to proxy WebSocket to the backend so the
    // frontend can connect to same-origin `/api/ws/chat` instead of a full
    // ws://localhost:3000/... URL. Useful for production-like routing tests.
    //
    // proxy: {
    //   '/api': {
    //     target: 'http://localhost:3000',
    //     changeOrigin: true,
    //     ws: true,
    //   },
    // },
  },
});
