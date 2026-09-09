/**
 * API 契约统一定义
 * 前端所有接口默认遵循 BaseResponse<T>
 */

/**
 * @typedef {Object} BaseResponse
 * @property {number} code
 * @property {any} [data]
 * @property {string} [message]
 * @property {string} [description]
 */

/**
 * 标准化并校验后端响应结构。
 * 如果后端返回非标准对象，主动抛错，避免业务层拿到脏数据。
 * @param {any} payload
 * @returns {BaseResponse}
 */
export const normalizeBaseResponse = (payload) => {
  if (!payload || typeof payload !== 'object' || typeof payload.code !== 'number') {
    throw new Error('后端响应不符合 BaseResponse 契约')
  }
  return payload
}

export const API_ERROR_CODES = {
  SUCCESS: 0,
  NOT_LOGIN: 40100,
  NO_AUTH: 40101,
}
