import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'


export default defineConfig({
  plugins: [vue()],
  server: {
    // 端口确认：前端是 5173，无需改,需要修改则为port：5174
    proxy: {
      // 匹配所有以 /api 开头的请求（必须精确匹配）
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true, // 跨域必开，模拟前端请求来自后端域名
        // 后端 context-path 已是 /api，直接转发，不去掉前缀
        // /api/user/login → http://localhost:8080/api/user/login ✓
      }

    }
  }

});