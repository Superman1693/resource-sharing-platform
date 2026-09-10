import request from './request'


// 1) 注册
export const userRegister = (body) =>
  request.post('/user/register', body)

// 2) 登录
export const userLogin = (body) =>
  request.post('/user/login', body)

// 3) 注销
export const userLogout = () =>
  request.post('/user/userLogout')

// 4) 获取当前登录用户
export const getCurrentUser = () =>
  request.get('/user/current')

// 获取用户公开信息（免登录）
export const getPublicUser = (userId) =>
  request.get(`/user/public/${userId}`)

// 5) 更新用户（仅本人）
export const updateUser = (body) =>
  request.put('/user/update', body)

// 6) 搜索用户（仅管理员）
export const searchUsers = (params) =>
  request.get('/user/search', { params })

// 7) 删除用户（仅管理员）
export const deleteUser = (userId) =>
  request.post('/user/delete', userId, {
    headers: { 'Content-Type': 'application/json' }
  })

// 8) 文件上传
export const uploadFile = (formData) =>
  request.post('/user/upload', formData)

// ========== 笔记/内容管理 API ==========

// 9) 获取笔记/内容列表
export const getNoteList = (params) =>
  request.get('/note/list', { params })

// 10) 获取笔记/内容详情
export const getNoteDetail = (id) =>
  request.get(`/note/${id}`)

// 11) 发布/创建笔记/内容
export const createNote = (body) =>
  request.post('/note/add', body)

// 12) 更新笔记/内容
export const updateNote = (id, body) =>
  request.put(`/note/update/${id}`, body)

// 13) 删除笔记/内容
export const deleteNote = (id) =>
  request.post('/note/delete', { id }, {
    headers: { 'Content-Type': 'application/json' }
  })

// 14) 搜索内容（管理员）
export const searchContent = (params) =>
  request.get('/content/search', { params })

// ========== 评论管理 API（核心优化） ==========

// 15) 获取评论列表
export const getCommentList = (params) =>
  request.get('/comment/list', {
    params: {
      page: params.page || 1,    // 默认第一页
      pageSize: params.pageSize || 20, // 默认每页20条
      noteId: params.noteId,     // 笔记ID（必传）
      keyword: params.keyword,   // 可选：搜索关键词
      status: params.status      // 可选：评论状态
    }
  })

// 16) 添加评论
export const addComment = (body) => {
  // 统一转换noteId为数字，避免字符串传递
  const requestBody = {
    ...body,
    noteId: Number(body.noteId)
  }
  return request.post('/comment/add', requestBody)
}

// 17) 回复评论（适配后端路径：/comment/reply/{commentId}）
export const replyComment = (commentId, body) => {
  // 统一转换参数为数字
  const requestBody = {
    ...body,
    noteId: Number(body.noteId)
  }
  return request.post(`/comment/reply/${Number(commentId)}`, requestBody)
}

// 18) 删除评论
export const deleteComment = (id) =>
  request.post('/comment/delete', { id: Number(id) }, {
    headers: { 'Content-Type': 'application/json' }
  })

// 19) 审核评论（管理员）
export const approveComment = (id, status) =>
  request.post('/comment/approve', {
    id: Number(id),
    status
  }, {
    headers: { 'Content-Type': 'application/json' }
  })

// 20) 点赞评论
export const likeComment = (id) =>
  request.post(`/comment/like/${Number(id)}`)

// ========== 资源管理 API ==========

// 21) 获取资源列表
export const getResourceList = (params) =>
  request.get('/resource/list', { params })

// 16.1) 举报评论
export const reportComment = (body) =>
  request.post('/comment/report', body, {
    headers: { 'Content-Type': 'application/json' }
  })
// 21.1) 获取资源详情
export const getResourceDetail = (id) =>
  request.get(`/resource/${Number(id)}`)

// 22) 添加资源
export const addResource = (body) =>
  request.post('/resource/add', body)

// 23) 更新资源
export const updateResource = (id, body) =>
  request.put(`/resource/update/${id}`, body)

// 24) 删除资源
export const deleteResource = (id) =>
  request.post('/resource/delete', { id: Number(id) }, {
    headers: { 'Content-Type': 'application/json' }
  })

// 35.1) 举报笔记（可附带 reason，落库到 report 表）
export const reportNote = (id, reason) =>
  request.post(`/note/report/${Number(id)}`, reason ? { reason } : null, {
    headers: { 'Content-Type': 'application/json' }
  })

// 25) 上传资源文件
export const uploadResourceFile = (formData) => {
  return request({
    url: '/resource/upload', // 匹配后端接口
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data' // 必须设置
    }
  })
}

// 26) 下载资源
export const downloadResource = (id) =>
  request.get(`/resource/download/${id}`, { responseType: 'blob' })

// 27) 切换资源状态
export const toggleResourceStatus = (id, status) =>
  request.post('/resource/toggleStatus', {
    id: Number(id),
    status
  }, {
    headers: { 'Content-Type': 'application/json' }
  })

// 27.1) 审核资源（管理员）
export const reviewResource = (id, status) =>
  request.post('/resource/review', {
    id: Number(id),
    status
  }, {
    headers: { 'Content-Type': 'application/json' }
  })

// ========== 知识图谱 API ==========

// 28) 获取知识地图
export const getKnowledgeMap = (starId) =>
  request.get('/knowledge/map', { params: { starId: Number(starId) } })

// 29) 保存知识地图
export const saveKnowledgeMap = (body) =>
  request.post('/knowledge/map/save', body)

// 管理员：全量重建星球知识地图（自动生成节点+连线）
export const syncKnowledgeMap = (starId) =>
  request.post('/knowledge/map/sync', null, { params: { starId: Number(starId) } })

// 30) 获取知识地图节点内容
export const getKnowledgeNodeContent = (nodeId) =>
  request.get(`/knowledge/node/${encodeURIComponent(nodeId)}/content`)

// ========== 统计数据 API ==========

// 31) 获取数据统计概览
export const getDataStats = (params) =>
  request.get('/stats/data', { params })

// 32) 获取用户统计数据
export const getUserStats = (params) =>
  request.get('/stats/user', { params })

// 33) 获取当前用户个人统计数据
export const getPersonalStats = () =>
  request.get('/stats/personal')

// 33.1) 获取我的资源（分页，"我的内容"页）
export const getMyResources = (params) =>
  request.get('/resource/my', { params })

// 33.2) 获取我的评论（分页，"我的内容"页）
export const getMyComments = (params) =>
  request.get('/comment/my', { params })

// 33.3) 获取我的获赞（分页，"我的内容"页）
export const getMyLikes = (params) =>
  request.get('/stats/myLikes', { params })

// ========== 笔记互动 API ==========

// 34) 点赞笔记/内容
export const likeNote = (id) =>
  request.post(`/note/like/${Number(id)}`)

// 35) 增加浏览量
export const incrementViewCount = (id) =>
  request.post(`/note/view/${Number(id)}`)

// ========== 其他 API ==========

// 36) 修改密码
export const changePassword = (body) =>
  request.post('/user/changePassword', body, {
    headers: { 'Content-Type': 'application/json' }
  })

// 36.5) 获取验证码
export const getCaptcha = () =>
  request.get('/captcha/get')

// 37) 重置密码（忘记密码）
export const resetPassword = (body) =>
  request.post('/user/resetPassword', body, {
    headers: { 'Content-Type': 'application/json' }
  })

// ========== AI 聊天 API ==========

// 37) 获取会话列表
export const getChatSessions = () =>
  request.get('/chat/sessions')

// 38) 获取会话详情
export const getChatSession = (sessionId) =>
  request.get(`/chat/session/${sessionId}`)

// 39) 创建会话
export const createChatSession = (body) =>
  request.post('/chat/session', {}, { params: { sessionName: body.sessionName } })

// 40) 更新会话 — 后端 @RequestBody SessionRequest，需传 JSON body
export const updateChatSession = (body) =>
  request.put('/chat/session', { sessionId: body.sessionId, sessionName: body.sessionName })

// 41) 删除会话
export const deleteChatSession = (sessionId) =>
  request.delete(`/chat/session/${sessionId}`)

// 42) 获取会话消息列表
export const getChatMessages = (sessionId, params = {}) =>
  request.get(`/chat/messages/${sessionId}`, { params })

// 43) 发送聊天消息（普通）
export const sendChatMessage = (body) =>
  request.post('/chat/message', body)

// 44) 发送聊天消息（流式）— 实现在 src/utils/stream.js，此处不重复定义
// 调用方请从 stream.js 导入：import { sendStreamChatMessage } from '../utils/stream'

// 45) 标记消息为已读
export const markMessageAsRead = (messageId) =>
  request.put(`/chat/message/${messageId}/read`)

// ========== 管理员 API（需求11/12/13）==========

// 置顶/取消置顶笔记（管理员）
export const topNote = (id, isTop) =>
  request.post(`/note/top/${Number(id)}`, null, { params: { isTop } })

// 获取热榜
export const getHotRank = (period = 'day') =>
  request.get('/note/hot', { params: { period } })

// 封禁用户
export const banUser = (id) =>
  request.post(`/admin/user/ban/${Number(id)}`)

// 解封用户
export const unbanUser = (id) =>
  request.post(`/admin/user/unban/${Number(id)}`)

// 获取待审核内容
export const getPendingContent = () =>
  request.get('/admin/content/pending')

// 审核通过笔记
export const approveNote = (id) =>
  request.post(`/admin/note/approve/${Number(id)}`)

// 拒绝笔记
export const rejectNote = (id) =>
  request.post(`/admin/note/reject/${Number(id)}`)

// 获取所有用户列表（管理员）
export const getUserList = (params) =>
  request.get('/user/search', { params: { userAccount: params?.keyword || params?.userAccount, ...params } })

// ========== 星球 API ==========

// 获取星球列表
export const getStarList = (params) =>
  request.get('/star/list', { params })

// 获取热门星球
export const getHotStars = (params) =>
  request.get('/star/hot', { params })

// 获取我的星球
export const getMyStars = () =>
  request.get('/star/my')

// 获取星球详情（支持分页）
export const getStarDetail = (id, page = 1, pageSize = 10) =>
  request.get(`/star/${Number(id)}`, { params: { page, pageSize } })

// 更新星球公告
export const updateStarAnnouncement = (id, announcement) =>
  request.post(`/star/${Number(id)}/announcement`, { announcement })

// 获取星球成员列表
export const getStarMembers = (id) =>
  request.get(`/star/${Number(id)}/members`)

// 加入星球（付费星球需传 { pay: true } 确认支付）
export const joinStar = (id, body) =>
  request.post(`/star/join/${Number(id)}`, body || {})

// 退出星球
export const exitStar = (id) =>
  request.post(`/star/exit/${Number(id)}`)

// 移除星球成员
export const removeStarMember = (starId, userId) =>
  request.post(`/star/${Number(starId)}/members/${Number(userId)}/remove`)

// 创建星球（普通用户，创建者自动成为 owner）
export const createStar = (body) =>
  request.post('/star/create', body)

// 我加入的星球最新帖子（首页社区动态）
export const getStarFeed = (limit = 10) =>
  request.get('/star/feed', { params: { limit } })

// 通用图片上传（仅返回 URL，不修改用户头像）
export const uploadImage = (formData) =>
  request.post('/user/upload/image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })

// ========== 用户成长 API ==========

// 获取当前用户成长统计
export const getUserGrowth = () =>
  request.get('/user/growth')

// ========== 管理员星球 API ==========

export const adminGetStarList = (params) =>
  request.get('/admin/star/list', { params })

export const adminCreateStar = (body) =>
  request.post('/admin/star/create', body)

export const adminUpdateStar = (id, body) =>
  request.put(`/admin/star/update/${Number(id)}`, body)

export const adminDeleteStar = (id) =>
  request.delete(`/admin/star/delete/${Number(id)}`)

export const adminToggleStarStatus = (id) =>
  request.post(`/admin/star/toggle/${Number(id)}`)

// ========== 通知 API ==========

// 获取通知列表
export const getNotificationList = (params) =>
  request.get('/notification/list', { params })

// 获取未读通知数量
export const getUnreadNotificationCount = () =>
  request.get('/notification/unread/count')

// 标记单条已读
export const markNotificationRead = (id) =>
  request.put(`/notification/${id}/read`)

// 标记全部已读
export const markAllNotificationsRead = () =>
  request.put('/notification/read/all')

// ========== 全局搜索 API ==========

// 搜索笔记（ES）
export const searchNotes = (params) =>
  request.get('/search/notes', { params })

// 搜索资源（ES）
export const searchResources = (params) =>
  request.get('/search/resources', { params })

// 管理员：全量同步笔记到 ES
export const syncNotesToEs = () =>
  request.post('/search/sync/notes')

// 管理员：全量同步资源到 ES
export const syncResourcesToEs = () =>
  request.post('/search/sync/resources')

// ========== 私信系统 ==========

// 获取会话列表
export const getConversationList = (params) =>
  request.get('/message/conversations', { params })

// 获取消息历史
export const getMessageHistory = (params) =>
  request.get('/message/history', { params })

// 发送私信
export const sendMessage = (body) =>
  request.post('/message/send', body)

// 获取未读私信数量
export const getUnreadMessageCount = () =>
  request.get('/message/unread/count')

// 标记会话消息已读
export const markConversationRead = (conversationId) =>
  request.post(`/message/read/${conversationId}`)

// 获取或创建与指定用户的会话（发起私信入口）
export const getOrCreateConversation = (userId) =>
  request.post(`/message/conversation/${userId}`)

// ===== 数据统计（仪表盘） =====
export const getContributionData = (params) =>
  request.get('/stats/contribution', { params })

export const getLearningTrend = (params) =>
  request.get('/stats/trend', { params })

// ===== 学习路径 =====
export const getLearningPaths = () =>
  request.get('/learning-path/list')

export const getLearningPathDetail = (id) =>
  request.get(`/learning-path/${id}`)

export const updatePathNodeStatus = (nodeId, status) =>
  request.post('/learning-path/node/status', { nodeId, status })

// ========== 关注系统 ==========

// 关注用户
export const followUser = (userId) =>
  request.post('/follow/add', { userId: Number(userId) })

// 取消关注
export const unfollowUser = (userId) =>
  request.post('/follow/delete', { userId: Number(userId) })

// 获取关注列表
export const getFollowList = (params) =>
  request.get('/follow/list', { params })

// 获取粉丝列表
export const getFollowerList = (params) =>
  request.get('/follow/follower/list', { params })

// 检查关注状态
export const checkFollowStatus = (userId) =>
  request.get('/follow/status', { params: { userId } })

// ===== OAuth 第三方登录 =====
export const getGithubAuthUrl = () =>
  request.get('/oauth/github/url')

export const githubLogin = (code) =>
  request.post('/oauth/github', { code })

export const getQQAuthUrl = () =>
  request.get('/oauth/qq/url')

export const qqLogin = (code) =>
  request.post('/oauth/qq', { code })

// ========== 收藏 API ==========
// 收藏/取消收藏笔记（toggle，返回 collected: bool）
export const toggleCollectNote = (noteId) =>
  request.post(`/collection/${noteId}`)

// 检查是否已收藏
export const checkCollected = (noteId) =>
  request.get(`/collection/check/${noteId}`)

// 我的收藏列表（分页）
export const getMyCollections = (params) =>
  request.get('/collection/my', { params })

// ========== 搜索热搜 API ==========
// 热搜词（Redis ZSET top10）
export const getHotKeywords = () =>
  request.get('/search/hot')

// 公开用户搜索（按用户名/账号模糊）
export const searchPublicUsers = (params) =>
  request.get('/search/users', { params })

// ========== 标签 API ==========
// 标签广场（按 usage_count 降序）
export const getTagList = () =>
  request.get('/tag/list')

// 某标签下的笔记列表（分页）
export const getNotesByTag = (name, params) =>
  request.get(`/tag/${encodeURIComponent(name)}/notes`, { params })

// 标签联想（发布页用）
export const suggestTags = (q) =>
  request.get('/tag/suggest', { params: { q } })

// 标签回填（管理员：从 note.tags JSON 迁移到 tag+note_tag 关联表）
export const migrateTags = () =>
  request.post('/tag/migrate')

// ========== 积分签到 API ==========
// 签到
export const signIn = () =>
  request.post('/points/sign')

// 查询今日是否已签到
export const checkSignedToday = () =>
  request.get('/points/sign/today')

// 积分账户（balance/totalEarned）
export const getPointsAccount = () =>
  request.get('/points/account')

// 积分流水（分页）
export const getPointsLog = (params) =>
  request.get('/points/log', { params })

// 签到日历（某月，参数 month 格式 yyyy-MM）
export const getSignCalendar = (params) =>
  request.get('/points/sign/calendar', { params })

// 积分回填（管理员：按贡献值初始化现有用户 balance，幂等）
export const migratePoints = () =>
  request.post('/points/migrate')

// ========== 举报管理 API（管理员） ==========
// 举报列表（status 可选：pending/resolved/ignored）
export const getReportList = (params) =>
  request.get('/report/list', { params })

// 处理举报（status: resolved/ignored + remark）
export const handleReport = (id, body) =>
  request.post(`/report/handle/${Number(id)}`, body)

// ========== 敏感词管理 API（管理员） ==========
// 查看当前敏感词库（{ total, words }）
export const getSensitiveWords = () =>
  request.get('/admin/sensitive/words')

// 全量更新敏感词库（热生效，words 为字符串数组）
export const updateSensitiveWords = (words) =>
  request.put('/admin/sensitive/words', { words })

// 重载敏感词库（热生效，从词库文件重新加载）
export const reloadSensitiveWords = () =>
  request.post('/admin/sensitive/reload')

// ========== 平台总览 API（管理员） ==========
// 平台数据总览（总用户/总笔记/总评论/总资源/今日新增笔记/待审核举报数）
export const getStatsOverview = () =>
  request.get('/stats/overview')

// ========== 专栏/合集 API ==========
// 创建专栏
export const createColumn = (body) =>
  request.post('/column/add', body)

// 更新专栏
export const updateColumn = (id, body) =>
  request.put(`/column/update/${Number(id)}`, body)

// 删除专栏
export const deleteColumn = (id) =>
  request.delete(`/column/delete/${Number(id)}`)

// 专栏列表（按星球/作者筛选，免登录浏览）
export const getColumnList = (params) =>
  request.get('/column/list', { params })

// 专栏详情（含章节笔记列表，按章节序号升序）
export const getColumnDetail = (id) =>
  request.get(`/column/${Number(id)}`)

// 把笔记加入专栏
export const addNoteToColumn = (body) =>
  request.post('/column/note/add', body)

// 把笔记移出专栏
export const removeNoteFromColumn = (noteId) =>
  request.post('/column/note/remove', { noteId: Number(noteId) })

// 我的专栏
export const getMyColumns = () =>
  request.get('/column/my')

// ========== 个性化推荐 API ==========
// 推荐笔记（登录走标签偏好，未登录走热门兜底）
export const getRecommendList = (size = 6) =>
  request.get('/recommend/list', { params: { size } })

// ========== 我的笔记（草稿箱）API ==========
// 我的笔记（按 status 筛选：draft/scheduled/published/pending/rejected，需登录）
export const getMyNotes = (params) =>
  request.get('/note/my', { params })

// 我的笔记详情（作者编辑回填用，返回任意状态含草稿/定时，需登录）
export const getMyNoteDetail = (id) =>
  request.get(`/note/my/${Number(id)}`)
