import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'


export default defineConfig({
  plugins: [vue()],
  server: {
    // ⚠️ 端口必须固定为 5173，且不允许自动递增。
    //
    // 原因：第三方登录（GitHub / QQ）的回调地址在后端配置里写死为
    //   http://localhost:5173/oauth/callback
    // 而 Vite 默认在 5173 被占用时会「静默」切到 5174、5175……
    // 授权方仍把浏览器重定向到 5173 → 无人监听 → 浏览器报
    // ERR_CONNECTION_REFUSED（localhost 拒绝连接）。
    //
    // strictPort: true：5173 被占用时直接启动失败并报错，而不是悄悄换端口，
    // 避免出现「服务在跑但回调打不通」的隐蔽问题。
    port: 5173,
    strictPort: true,
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