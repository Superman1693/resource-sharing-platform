import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/userLogin'
import { setStarScope, clearStarScope } from '../utils/starScope'


const BasicLayout = () => import('../layouts/BasicLayout.vue')
const UserLayout = () => import('../layouts/UserLayout.vue')

// 页面组件统一异步导入
const Login = () => import('../views/Login.vue')
const Register = () => import('../views/Register.vue')
const ChangeUserPassword = () => import('../views/changeUserPassword.vue')
const NoteList = () => import('../views/Notes/NoteList.vue')
const NoteDetail = () => import('../views/Notes/NoteDetail.vue')
const NotePublish = () => import('../views/Notes/NotePublish.vue')
const ContentPublish = () => import('../views/Content/ContentPublish.vue')
const ContentManage = () => import('../views/Content/ContentManage.vue')
const ResourceList = () => import('../views/Resources/ResourceList.vue')
const ResourceDetail = () => import('../views/Resources/ResourceDetail.vue')
const ResourceManage = () => import('../views/Resources/ResourceManage.vue')
const ResourceAdd = () => import('../views/Resources/ResourceAdd.vue')
const UserResourceAdd = () => import('../views/Resources/UserResourceAdd.vue')
const CommentManage = () => import('../views/Comments/CommentManage.vue')
const Account = () => import('../views/Home/Account.vue')
const Profile = () => import('../views/Home/Profile.vue')
const GrowthTimeline = () => import('../views/Growth/GrowthTimeline.vue')
const KnowledgeMap = () => import('../views/Knowledge/KnowledgeMap.vue')
const KnowledgeMapEdit = () => import('../views/Knowledge/KnowledgeMapEdit.vue')
const UserActivity = () => import('../views/Stats/UserActivity.vue')
const StarList = () => import('../views/Stars/StarList.vue')
const StarDetail = () => import('../views/Stars/StarDetail.vue')
const StarManage = () => import('../views/Stars/StarManage.vue')
const Dashboard = () => import('../views/Admin/Dashboard.vue')
const ReportManage = () => import('../views/Admin/ReportManage.vue')
const SensitiveWord = () => import('../views/Admin/SensitiveWord.vue')
const UserHome = () => import('../views/User/UserHome.vue')
const ResumeShowcase = () => import('../views/User/ResumeShowcase.vue')
const AdminUser = () => import('../views/AdminUser.vue')
const HotRank = () => import('../views/HotRank.vue')
const MessageCenter = () => import('../views/User/MessageCenter.vue')
const FollowList = () => import('../views/User/FollowList.vue')
const UserProfile = () => import('../views/User/UserProfile.vue')
const SearchResult = () => import('../views/Search/SearchResult.vue')
const TagSquare = () => import('../views/Tags/TagSquare.vue')
const TagDetail = () => import('../views/Tags/TagDetail.vue')
const DataDashboard = () => import('../views/User/DataDashboard.vue')
const MyContent = () => import('../views/User/MyContent.vue')
const LearningPath = () => import('../views/User/LearningPath.vue')
const ColumnDetail = () => import('../views/Stars/ColumnDetail.vue')
const ColumnManage = () => import('../views/Stars/ColumnManage.vue')
const OAuthCallback = () => import('../views/OAuthCallback.vue')
const ResetPassword = () => import('../views/ResetPassword.vue')

const router = createRouter({
  history: createWebHistory('/'),
  routes: [
    // 默认直接进入用户首页（免登录浏览）
    { path: '/', redirect: '/user/home' },
    { path: '/resume', name: 'ResumeShowcase', component: ResumeShowcase },
    { path: '/login', name: 'Login', component: Login },
    { path: '/oauth/callback', name: 'OAuthCallback', component: OAuthCallback },
    { path: '/register', name: 'Register', component: Register },
    { path: '/resetPassword', name: 'ResetPassword', component: ResetPassword },
    { path: '/changeUserPassword', name: 'ChangeUserPassword', component: ChangeUserPassword },

    // ===== 管理端路由（BasicLayout）—— 需要管理员登录 =====
    {
      path: '/main',
      component: BasicLayout,
      meta: { requiresAuth: true, requiresAdmin: true },
      children: [
        { path: '', redirect: '/main/dashboard' },
        { path: 'dashboard', name: 'Dashboard', component: Dashboard },
        { path: 'reportManage', name: 'ReportManage', component: ReportManage },
        { path: 'sensitiveWord', name: 'SensitiveWord', component: SensitiveWord },
        { path: 'contentManage', name: 'ContentManage', component: ContentManage },
        { path: 'contentPublish', name: 'ContentPublish', component: ContentPublish },
        { path: 'noteDetail/:id', name: 'NoteDetail', component: NoteDetail },
        { path: 'resourceManage', name: 'ResourceManage', component: ResourceManage },
        { path: 'resourceAdd', name: 'ResourceAdd', component: ResourceAdd },
        { path: 'commentManage', name: 'CommentManage', component: CommentManage },
        { path: 'Account', name: 'Account', component: Account },
        { path: 'userActivity', name: 'UserActivity', component: UserActivity },
        { path: 'knowledgeMapEdit/:starId?', name: 'KnowledgeMapEdit', component: KnowledgeMapEdit },
        { path: 'growthTimeline', name: 'GrowthTimeline', component: GrowthTimeline },
        { path: 'Profile', name: 'Profile', component: Profile },
        { path: 'adminUser', name: 'AdminUser', component: AdminUser },
        { path: 'starManage', name: 'StarManage', component: StarManage },
      ]
    },

    // ===== 用户端路由（UserLayout）—— 浏览类免登录，操作类需登录 =====
    {
      path: '/user',
      component: UserLayout,
      children: [
        { path: '', redirect: '/user/home' },
        // 免登录浏览
        { path: 'home', name: 'UserHome', component: UserHome },
        { path: 'notes', name: 'UserNoteList', component: NoteList },
        { path: 'noteDetail/:id', name: 'UserNoteDetail', component: NoteDetail },
        { path: 'resources', name: 'UserResourceList', component: ResourceList },
        { path: 'resourceDetail/:id', name: 'UserResourceDetail', component: ResourceDetail },
        { path: 'starList', name: 'UserStarList', component: StarList },
        { path: 'starDetail/:id', name: 'UserStarDetail', component: StarDetail },
        { path: 'knowledgeMap/:starId?', name: 'UserKnowledgeMap', component: KnowledgeMap },
        { path: 'search', name: 'SearchResult', component: SearchResult },
        { path: 'tags', name: 'TagSquare', component: TagSquare },
        { path: 'tag/:name', name: 'TagDetail', component: TagDetail },
        // 热榜（免登录）
        { path: 'hotRank', name: 'HotRank', component: HotRank },
        // 需要登录的核心操作
        { path: 'publish', name: 'UserNotePublish', component: NotePublish, meta: { requiresAuth: true } },
        { path: 'resourceAdd', name: 'UserResourceAdd', component: UserResourceAdd, meta: { requiresAuth: true } },
        { path: 'growthTimeline', name: 'UserGrowthTimeline', component: GrowthTimeline, meta: { requiresAuth: true } },
        { path: 'profile', name: 'UserProfile', component: Profile, meta: { requiresAuth: true } },
        { path: 'messages', name: 'MessageCenter', component: MessageCenter, meta: { requiresAuth: true, title: '私信' } },
        { path: 'followList', name: 'FollowList', component: FollowList, meta: { requiresAuth: true, title: '关注列表' } },
        { path: 'dashboard', name: 'DataDashboard', component: DataDashboard, meta: { requiresAuth: true, title: '数据仪表盘' } },
        { path: 'myContent', name: 'MyContent', component: MyContent, meta: { requiresAuth: true, title: '我的内容' } },
        { path: 'column/:id', name: 'ColumnDetail', component: ColumnDetail },
        { path: 'columnManage', name: 'ColumnManage', component: ColumnManage, meta: { requiresAuth: true, title: '我的专栏' } },
        { path: 'learningPath', name: 'LearningPath', component: LearningPath, meta: { requiresAuth: true, title: '学习路径' } },
        { path: 'user/:userId', name: 'UserDetail', component: UserProfile },
      ]
    },

    // 兜底：未定义路由跳转到用户首页
    { path: '/:pathMatch(.*)*', redirect: '/user/home' }
  ],
  // 路由滚动行为：新导航回到页面顶部（否则详情页继承列表页滚动位置，看起来"落在评论区"）；
  // 带 hash 锚点时滚到锚点；浏览器后退/前进恢复上次的滚动位置
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }
    if (to.hash) {
      return { el: to.hash, behavior: 'smooth' }
    }
    return { top: 0, left: 0 }
  }
})

// ===== 星球作用域同步 =====
// 进入星球相关页面时把星球ID写入作用域（由 request.js 附加为 X-Star-Id 请求头），
// 后端据此对 note / comment / note_column / knowledge_map 追加 star_id 条件，
// 使多租户行级隔离真正生效；离开这些页面则清除，保证首页/搜索/热榜仍可跨星球浏览。
//
// 注意：这里只登记「用户端」的星球页面。管理端（/main/*）刻意不设置作用域，
//       因为后台需要跨星球查看全部数据。
const ROUTE_STAR_PARAM = {
  UserStarDetail: 'id',        // /user/starDetail/:id
  UserKnowledgeMap: 'starId'   // /user/knowledgeMap/:starId?
}

const syncStarScopeFromRoute = (to) => {
  const paramName = ROUTE_STAR_PARAM[to.name]
  if (!paramName) {
    clearStarScope()
    return
  }
  const raw = to.params[paramName]
  const starId = raw === undefined || raw === null || raw === '' ? NaN : Number(raw)
  if (Number.isInteger(starId) && starId > 0) {
    setStarScope(starId)
  } else {
    // 形如 /user/knowledgeMap（未指定星球）→ 保持全平台范围
    clearStarScope()
  }
}

// ===== 路由守卫：免登浏览，按需登录 =====
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()

  // 先同步星球作用域（与登录态无关，匿名浏览星球也应带上）
  syncStarScopeFromRoute(to)

  let isAuthenticated = false
  let isAdmin = false
  let isBanned = false

  try {
    isAuthenticated = !!userStore.isLogin
    isAdmin = Number(userStore.userRole || 0) === 1
    isBanned = Number(userStore.userStatus || 0) === 1
  } catch (e) {
    console.warn('获取用户状态失败', e)
  }

  // 已登录但账号被封禁，强制退出（禁止循环重定向）
  if (isAuthenticated && isBanned && to.path !== '/login') {
    userStore.logout()
    localStorage.removeItem('userInfo')
    localStorage.removeItem('userLogin')
    next({ path: '/login', query: { msg: 'banned' } })
    return
  }

  // 需要登录的操作路由（未登录则跳转登录页，登录后回跳）
  if (to.meta.requiresAuth && !isAuthenticated) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  // 已登录用户访问登录页，按角色跳转（禁止循环重定向）
  if (to.path === '/login' && isAuthenticated) {
    const redirectTarget = isAdmin ? '/main/dashboard' : '/user/home'
    // 仅当目标不等于当前页面时才跳转，防止循环
    if (redirectTarget !== from.path) {
      next(redirectTarget)
      return
    }
  }

  // 普通用户不能访问管理端
  if ((to.meta.requiresAdmin || to.path.startsWith('/main')) && !isAdmin) {
    next(isAuthenticated ? '/user/home' : '/login')
    return
  }

  next()
})

// 异步路由组件加载失败时自动恢复一次，避免跳转后白屏
router.onError((error, to) => {
  const message = String(error?.message || '')
  const isChunkLoadError =
    message.includes('Failed to fetch dynamically imported module') ||
    message.includes('Importing a module script failed') ||
    message.includes('Loading chunk')

  if (!isChunkLoadError) {
    return
  }

  const reloadKey = 'router:chunk-reload'
  const targetPath = to?.fullPath || window.location.pathname
  const lastReloadPath = sessionStorage.getItem(reloadKey)

  if (lastReloadPath === targetPath) {
    sessionStorage.removeItem(reloadKey)
    return
  }

  sessionStorage.setItem(reloadKey, targetPath)
  window.location.assign(targetPath)
})

router.afterEach(() => {
  sessionStorage.removeItem('router:chunk-reload')
})

export default router