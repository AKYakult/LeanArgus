# MyArgus 项目学习进度

> 目标：严格参照 Argus-backend 参考项目架构，从 0 用 Spring Boot + MyBatis-Plus + JWT 实现用户认证与鉴权全链路，不引入 Spring Security 框架包袱，纯手写 Filter 校验 JWT。

---

## 技术选型

| 项目 | 选择 | 说明 |
| :--- | :--- | :--- |
| 构建工具 | Gradle Kotlin DSL (`build.gradle.kts`) |[cite: 5] |
| Spring Boot | 4.1.1 | 采用最新 Boot 4 体系[cite: 2] |
| Java | 21 | 全面采用 Record、Pattern Matching 等现代语法[cite: 2] |
| 数据库 | PostgreSQL（Docker 部署） |[cite: 2, 5] |
| ORM | MyBatis-Plus | `mybatis-plus-spring-boot4-starter:3.5.17`[cite: 2] |
| 密码加密 | `spring-security-crypto` | 仅引入 BCrypt 工具包，不引入完整 Spring Security[cite: 2] |
| JWT | `jjwt` 0.12.6 | api + impl + jackson 三件套[cite: 2] |
| 鉴权方式 | 手写 `JwtAuthenticationFilter` | 纯 Servlet Filter 拦截，与 Spring Security 解耦[cite: 2] |

---

## 整体路线图

1. ✅ 项目骨架打通：依赖配置 + 数据库连接 + schema 自动建表
2. ✅ `User` 实体 + Mapper（MyBatis-Plus），测试接口验证通过
3. ✅ 密码加密 + 基础设施 + 用户注册逻辑全链路
   - ✅ 注册 `PasswordEncoder` (BCrypt)
   - ✅ 基于 Java 21 Record 封装统一响应体 `ApiResponse<T>`
   - ✅ 建立 `common.exception` 异常体系与 `GlobalExceptionHandler`
   - ✅ 编写 `RegisterRequest` DTO（带 Bean Validation 注解）
   - ✅ 编写 `PasswordHasher` + `PasswordPolicyValidator`
   - ✅ 编写 `AuthService.register()`（校验格式、排重、BCrypt 哈希、落库）
   - ✅ 编写 `AuthController` 暴露 `POST /api/auth/register`
4. ✅ 登录接口与双 Token 签发（`POST /api/auth/login`）
   - ✅ 编写 `LoginRequest` DTO 与 `AuthTokensResponse` VO
   - ✅ `AuthService.login()`：行锁防竞态、支持用户名/邮箱登录、校验状态与密码
5. ✅ 令牌服务与防重放机制
   - ✅ `JwtAccessTokenService`（JJWT 0.12.x 规范签发与解析）
   - ✅ `RefreshTokenService`（UUID 生成、BCrypt 哈希持久化、吊销控制）
   - ✅ Refresh Token 轮转与重放攻击检测（撤销失败立即级联吊销全部 Token）
6. ✅ `JwtAuthenticationFilter`：手写无状态拦截 + 白名单 + `UserContext` 注入
   - ✅ 继承 `OncePerRequestFilter`，精准解析 Header Bearer Token
   - ✅ 绑定 `UserContext`（`ThreadLocal<AuthenticatedUser>`）
   - ✅ `finally` 严格清理上下文，杜绝线程池污染与内存泄漏
7. ✅ 受保护接口 `/api/auth/me` 与用户态强校验
   - ✅ `CurrentUserService`：结合 Token 身份与数据库实时状态（防封禁逃逸）
   - ✅ 暴露 `GET /api/auth/me` 并返回 `CurrentUserProfileResponse`
8. ✅ 安全 Cookie 管理与登出（`POST /api/auth/refresh` & `POST /api/auth/logout`）
   - ✅ `AuthCookieSupport`：将 Refresh Token 封装为 `httpOnly` + `SameSite=Lax` Cookie
   - ✅ 登出原子吊销 Refresh Token 并清除客户端 Cookie
9. ✅ 端到端全链路接口联调与验证（支持 requests.http 与静态页面联调）
10. ✅ **知识库群组与成员权限管理（`group` 模块，全链路完成）**
    - ✅ 4 张核心表结构创建（`groups`, `group_memberships`, `group_invitations`, `group_join_requests`）
    - ✅ 4 个状态与角色枚举 (`GroupRole`, `GroupStatus`, `GroupInvitationStatus`, `GroupJoinRequestStatus`)
    - ✅ 4 个 Entity、3 个 DTO、4 个 VO 全量建立
    - ✅ 2 个 MyBatis-Plus Mapper 接口及 XML 文件（`GroupMembershipMapper`, `GroupJoinRequestMapper`）
    - ✅ 3 个业务服务（`GroupMembershipService`, `GroupManagementService`, `GroupJoinRequestService`）
    - ✅ 4 个控制器（`GroupQueryController`, `GroupManagementController`, `InvitationDecisionController`, `GroupJoinRequestController`）
    - ✅ `requests.http` 补充 9 ~ 22 项全场景测试用例
11. ⏳ **下一阶段**：文档管理与分片上传模块（`document` 模块）

---

## 当前已完成详情

### Step 1：项目骨架与数据库连接
- `build.gradle.kts` 配置完整：涵盖 web、validation、lombok、`spring-security-crypto`、jjwt 0.12.6、`mybatis-plus-spring-boot4-starter`、postgresql 驱动。
- `application.yaml` 配置 PostgreSQL 数据源，配合 Docker Compose 部署的 PostgreSQL 18 + PGvector 实例。

### Step 2：User 实体与数据访问层
- 模块包分层：`com.example.myargus.user`。
- `User` 实体完整映射 `users` 表字段，`UserMapper` 继承 MyBatis-Plus 的 `BaseMapper<User>`。
- `UserQueryService` 封装基础只读查询与用户查重逻辑，返回只读 `UserRecord` 防止敏感字段外泄。

### Step 3：通用基础设施、安全配置与注册
- **统一响应封装 (`common.api.ApiResponse<T>`)**：Java 21 Record 定义，支持泛型与静态工厂方法。
- **全局异常拦截 (`common.exception.GlobalExceptionHandler`)**：覆盖 `BusinessException` (400)、`UnauthorizedException` (401)、`ForbiddenException` (403)、`MethodArgumentNotValidException` 等。
- **密码加密与策略**：
  - `PasswordHasher`：隔离 BCrypt 细节。
  - `PasswordPolicyValidator`：长度与字符复杂度校验。
- **用户注册**：`AuthService.register()` + `AuthController.register()`，规范用户名、排重并安全入库。

### Step 4 & 5：双 Token 架构、登录与防重放
- **JJWT 0.12.x 规范**：`JwtAccessTokenService` 签发 HMAC-SHA256 签名的 Access Token（有效 30 分钟），支持逆向解析与过期判定。
- **持久化刷新令牌**：`RefreshTokenService` 与 `UserRefreshTokenMapper`，将 Refresh Token 哈希后落库 `user_refresh_tokens`。
- **高安全登录**：`AuthService.login()` 采用 `SELECT ... FOR UPDATE` 行锁，支持多设备登录与旧令牌清理。
- **防重放攻击（Replay Attack Protection）**：刷新令牌时执行单次原子吊销；若旧 Token 已被使用，判定为凭据泄露，强制吊销该用户所有活跃会话。

### Step 6 & 7：手写 JWT Filter、UserContext 与 /me 端点
- **上下文管理 (`UserContext`)**：基于 `ThreadLocal<AuthenticatedUser>` 存储当前请求用户，提供静态存取。
- **拦截器 (`JwtAuthenticationFilter`)**：
  - 继承 `OncePerRequestFilter`，重写 `shouldNotFilter` 配置公开端点白名单。
  - 校验 Bearer Token 并在请求进入 Controller 前绑定上下文。
  - 严格通过 `finally { UserContext.clear(); }` 避免线程复用造成的内存泄漏或身份串号。
- **受保护端点**：`GET /api/auth/me` 经由 `CurrentUserService` 实时比对数据库用户状态（`ACTIVE` / `DISABLED`），确保被禁用户即便持有未过期 JWT 也无法调用业务。

### Step 8：安全 Cookie 与登出闭环
- **`AuthCookieSupport`**：将 Refresh Token 写入 `httpOnly` + `SameSite=Lax` Cookie，杜绝 XSS 窃取风险。
- **登出流程**：`POST /api/auth/logout` 清理数据库持久化 Token 并通过响应头 `Max-Age=0` 清空浏览器 Cookie。

### Step 9 & 10：group 模块全链路闭环
- **数据库表**：在 PostgreSQL 中建立 `groups`、`group_memberships`、`group_invitations`、`group_join_requests` 4 张核心表及相应索引。
- **模型与枚举**：严格对照 reference，建立 `GroupRole`、`GroupStatus`、`GroupInvitationStatus`、`GroupJoinRequestStatus` 4 个枚举；4 个数据库实体及前后端通信 DTO/VO。
- **Mapper 与 XML**：
  - `GroupMembershipMapper`：管理群组归属、成员角色与邀请流转，利用 PostgreSQL `INSERT ... RETURNING id` 与 CAS 乐观并发控制。
  - `GroupJoinRequestMapper`：管理申请加入与审批流转。
- **业务服务层（Service）**：
  - `GroupMembershipService`：群组可见性聚合与权限守卫（`requireGroupReadable`, `requireGroupOwner`）。
  - `GroupManagementService`：群组创建（自动绑定 OWNER）、发起邀请、邀请状态机流转、踢人与退群安全限制。
  - `GroupJoinRequestService`：凭 `groupCode` 申请入群、防重排查、群主审批/拒绝流转。
- **控制层（Controller）**：
  - 暴露 `GroupQueryController` (`/api/groups/my`)、`GroupManagementController` (`/api/groups`)、`InvitationDecisionController` (`/api/invitations`)、`GroupJoinRequestController` (`/api/groups/join-requests`)。
  - 纯净集成现有 `ApiResponse.ok(...)`。
- **接口测试套件**：在 `requests.http` 中提供 9 ~ 22 项全链路可执行 HTTP 测试用例。

---

## 下一步核心待办清单

1. **全链路接口测试验证**：启动后端服务（`./gradlew bootRun`），通过 IDEA `requests.http` 验证群组创建、成员邀请、申请加入全流程。
2. **开启 `document` 模块（文档管理与分片上传）**：
   - 文档实体 `Document`、`DocumentChunk`、分片元数据管理。
   - 对象存储/本地文件存储抽象接入。
   - 大文件分片并发上传与断点续传。