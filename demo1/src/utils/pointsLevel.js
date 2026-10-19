/**
 * 积分等级工具。
 *
 * 设计要点：等级用「累计获得」（totalEarned）而不是「余额」（balance）计算 ——
 * 这样用户消费积分（解锁笔记、打赏作者）不会掉级，才敢放心花。
 *
 * ⚠️ LEVELS 的 min / priceLimit 必须与后端 `PointsLevel` 枚举保持一致。
 * 这里的 priceLimit 只用于展示与输入框预校验，最终校验以后端为准
 * （`GET /points/quota` 会下发权威值）。
 */

export const LEVELS = [
  { level: 1, name: '新手', min: 0, priceLimit: 0, perks: '发布笔记、签到得积分' },
  { level: 2, name: '活跃', min: 100, priceLimit: 50, perks: '可给内容定价（上限 50 积分）' },
  { level: 3, name: '贡献', min: 500, priceLimit: 100, perks: '售价上限 100 积分' },
  { level: 4, name: '资深', min: 2000, priceLimit: 200, perks: '售价上限 200 积分、专属徽章' },
  { level: 5, name: '核心', min: 10000, priceLimit: 500, perks: '售价上限 500 积分、可申请创建星球' },
]

/** 每日「平台发放」积分上限（与后端 PointsService.DAILY_EARN_LIMIT 对齐，仅用于展示） */
export const DAILY_EARN_LIMIT = 200

/**
 * 根据「累计获得积分」计算等级信息。
 *
 * @param {number} totalEarned 累计获得积分
 * @returns {{level:number,name:string,min:number,perks:string,total:number,next:object|null,progress:number}}
 */
export const getLevelInfo = (totalEarned) => {
  const total = Number(totalEarned) || 0
  let current = LEVELS[0]
  for (const lv of LEVELS) {
    if (total >= lv.min) current = lv
  }
  const next = LEVELS.find((l) => l.min > total) || null
  const progress = next
    ? Math.min(100, Math.round(((total - current.min) / (next.min - current.min)) * 100))
    : 100
  return { ...current, total, next, progress }
}
