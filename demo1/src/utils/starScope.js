/**
 * 星球作用域（多租户上下文）
 *
 * 背景：后端用 MyBatis-Plus 的租户插件（TenantInterceptor）为 SQL 自动追加
 * `star_id = 当前星球` 条件，实现按星球的行级数据隔离。但本平台是**公开知识社区**，
 * 首页 / 搜索 / 热榜等场景必须能跨星球浏览，所以插件只在「本次请求显式声明了星球上下文」
 * 时才生效——声明的载体就是请求头 `X-Star-Id`。
 *
 * 职责划分：
 *   - 进入星球相关页面（星球详情、知识图谱）时，由路由守卫调用 setStarScope() 写入星球ID；
 *   - 离开这些页面时调用 clearStarScope()，让后续请求恢复为「跨星球公开读取」；
 *   - request.js 的请求拦截器读取本模块，自动附加 X-Star-Id 请求头。
 *
 * 后端还会二次校验：只有当前用户确实是该星球的成员，作用域才会被采纳
 * （见 AuthInterceptor#applyStarScope），所以这里存的值不必是可信输入。
 */

const STORAGE_KEY = 'starScopeId'

/**
 * 读取当前星球作用域
 * @returns {number|null} 星球ID；未设置时返回 null
 */
export const getStarScope = () => {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const id = Number(raw)
    return Number.isInteger(id) && id > 0 ? id : null
  } catch {
    return null
  }
}

/**
 * 设置星球作用域
 * @param {number|string} starId 星球ID；非法值会被忽略（并清除已有效果）
 */
export const setStarScope = (starId) => {
  const id = Number(starId)
  if (!Number.isInteger(id) || id <= 0) {
    clearStarScope()
    return
  }
  try {
    localStorage.setItem(STORAGE_KEY, String(id))
  } catch {
    /* localStorage 不可用（隐私模式等）时静默降级：仅本次会话不启用租户收窄 */
  }
}

/**
 * 清除星球作用域（离开星球页面、退出登录时调用）
 */
export const clearStarScope = () => {
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    /* ignore */
  }
}

export default { getStarScope, setStarScope, clearStarScope }
