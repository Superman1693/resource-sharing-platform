/**
 * OAuth 第三方登录工具函数
 */

import request from './request'

/**
 * 获取 GitHub OAuth 授权 URL（从后端动态获取）
 */
export const getGithubOAuthUrl = async () => {
  const res = await request.get('/oauth/github/url')
  return res.data.url
}

/**
 * 获取 QQ OAuth 授权 URL（从后端动态获取）
 */
export const getQQOAuthUrl = async () => {
  const res = await request.get('/oauth/qq/url')
  return res.data.url
}

/**
 * 生成随机 state 参数防止 CSRF
 * 使用 crypto.getRandomValues 确保密码学安全
 */
const generateState = (provider = 'github') => {
  const array = new Uint8Array(32)
  crypto.getRandomValues(array)
  const state = Array.from(array, byte => byte.toString(16).padStart(2, '0')).join('')
  sessionStorage.setItem(`${provider}_oauth_state`, state)
  return state
}

/**
 * 验证 OAuth 回调的 state 参数
 */
export const validateOAuthState = (state, provider = 'github') => {
  const savedState = sessionStorage.getItem(`${provider}_oauth_state`)
  sessionStorage.removeItem(`${provider}_oauth_state`)
  return state === savedState
}

/**
 * 根据回调里的 state 反查是哪个渠道发起的授权。
 *
 * 两个渠道都是整页跳转（同标签页），sessionStorage 仍然可读。
 * 用它替代原先「用 state 值猜 provider」的做法（QQ 现在也使用随机 state）。
 *
 * @returns {'github' | 'qq' | null}
 */
export const resolveOAuthProvider = (state) => {
  if (!state) return null
  if (sessionStorage.getItem('github_oauth_state') === state) return 'github'
  if (sessionStorage.getItem('qq_oauth_state') === state) return 'qq'
  return null
}

/**
 * 从 URL 中提取 OAuth 授权码
 */
export const extractOAuthCode = () => {
  const urlParams = new URLSearchParams(window.location.search)
  return urlParams.get('code')
}

/**
 * 从 URL 中提取 state 参数
 */
export const extractOAuthState = () => {
  const urlParams = new URLSearchParams(window.location.search)
  return urlParams.get('state')
}

/**
 * 跳转到 GitHub 授权页面
 *
 * 说明：后端返回的授权地址里没有 state，这里补上随机 state 并存入 sessionStorage，
 * 回调页会用 validateOAuthState() 校验，防止 authorization code 被 CSRF 复用。
 * 必须用整页跳转（window.location.href），保证 sessionStorage 与回调页同源同标签页。
 */
export const redirectToGithub = async () => {
  const url = await getGithubOAuthUrl()
  const state = generateState('github')
  window.location.href = `${url}&state=${encodeURIComponent(state)}`
}

/**
 * 跳转到 QQ 授权页面
 *
 * 【修复】原先 QQ 分支使用后端硬编码的 state=qq_login，回调页据此识别来源，
 * 等于没有 CSRF 防护（攻击者可用自己的 code 诱导受害者在浏览器完成登录）。
 * 现在与 GitHub 分支对齐：生成随机 state 存入 sessionStorage，回调页强制校验。
 */
export const redirectToQQ = async () => {
  const url = await getQQOAuthUrl()
  const state = generateState('qq')
  window.location.href = `${url}&state=${encodeURIComponent(state)}`
}
