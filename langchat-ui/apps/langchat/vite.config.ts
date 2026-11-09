import { defineConfig } from '@vben/vite-config';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      server: {
        proxy: {
          '/api': {
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/api/, ''),
            // 本地开发统一代理到 LangChat 后端服务
            // 注：本机 8080 被 Steam 占用，后端改监听 8081
            target: 'http://localhost:8081/api',
            ws: true,
          },
        },
      },
    },
  };
});
