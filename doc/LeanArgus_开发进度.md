# LeanArgus 后端开发进度

> 更新时间：2026-09-07
>
> 目标：严格参考 `Argus` 完整参考项目，逐步完成 `LeanArgus` 后端。当前 Java/Spring 不熟悉，因此每一步同时记录“做什么”和“为什么”。

## 1. 项目目标与约束

### 参考项目
- 项目：`Argus`
- 后端包根路径：`com.argus.rag`
- 主要模块：`auth`、`common`、`user`、`group`、`document`、`ingestion`、`engine`、`qa`、`assistant`、`metrics`

### 当前项目
- 项目：`LeanArgus`
- 当前后端包根路径：`com.example.myargus`

### 开发原则
1. 优先严格对应参考项目的包结构、类名和职责。
2. 不在没有说明的情况下自行改变参考项目架构。
3. 如果发现更好的设计，先按参考项目实现，再单独说明可选优化。
4. 每完成一个阶段先编译，避免错误累积。
5. Java/Spring 概念会结合 C++ 背景解释，但不把两者完全等同。

## 2. 当前目录进度

```text
com.example.myargus
├── auth
│   ├── config
│   │   ├── AuthConfiguration
│   │   └── AuthProperties
│   ├── controller
│   │   └── AuthController           ← 新增（已完成，暴露 /api/auth/*）
│   ├── mapper
│   │   └── UserRefreshTokenMapper
│   ├── model
│   │   ├── dto
│   │   │   ├── LoginRequest
│   │   │   └── RegisterRequest
│   │   ├── entity
│   │   │   └── UserRefreshToken
│   │   └── vo
│   │       ├── AuthTokensResponse       ← 新增（已完成）
│   │       └── CurrentUserProfileResponse ← 新增（已完成）
│   ├── security
│   │   ├── JwtAccessTokenService
│   │   ├── RefreshTokenService
│   │   ├── AuthCookieSupport
│   │   └── JwtAuthenticationFilter  ← 新增（已完成，手写请求拦截与上下文绑定）
│   └── service
│       ├── PasswordHasher
│       ├── PasswordPolicyValidator
│       ├── RefreshTokenRecord
│       └── AuthService              ← 已完成（login/register/refresh/logout）
│
├── common
│   ├── api
│   ├── enums
│   ├── exception
│   └── security
│       ├── AuthenticatedUser
│       └── UserContext
│
└── user
    ├── controller
    │   └── UserTestController
    ├── mapper
    │   └── UserMapper
    ├── model
    │   ├── entity
    │   │   └── User
    │   └── vo
    │       └── AdminUserItemResponse
    └── service
        └── UserQueryService
```

> 以上表示目前已经建立/实现的完整结构。认证链路（Service + Controller + Filter + Cookie 支持）已全量完成并通过编译，待本地运行全链路联调验证。

## 3. 已完成：common

### `common.enums`
已经涉及：
- `SystemRole`
- `UserStatus`

核心概念：

```text
SystemRole
├── ADMIN
└── USER

UserStatus
├── ACTIVE
└── DISABLED
```

### `AuthenticatedUser`
表示 JWT 认证后得到的身份信息。

```text
JWT
 ↓
解析
 ↓
AuthenticatedUser
```

### `UserContext`
保存当前 HTTP 请求对应的 `AuthenticatedUser`。

核心思想可以理解为：

```text
当前线程
   ↓
当前请求用户
```

通常基于：

```java
ThreadLocal<AuthenticatedUser>
```

工作流程：

```text
HTTP Request
    ↓
JWT Filter
    ↓
AuthenticatedUser
    ↓
UserContext.set(...)
    ↓
Controller / Service
    ↓
UserContext.get(...)
    ↓
得到当前用户身份
    ↓
请求结束
    ↓
UserContext.clear()
```

重要理解：`UserContext` 不是 Spring 自动产生的，也不是数据库，而是项目自己定义的请求上下文。

## 4. 已完成：user

### `User`
对应数据库 `users` 表，是数据库层面的用户实体。

主要字段：

```text
id
userCode
username
email
displayName
passwordHash
systemRole
status
mustChangePassword
lastLoginAt
createdAt
updatedAt
```

### `UserMapper`
负责 `User` 与数据库之间的 MyBatis-Plus 操作。

使用：

```java
BaseMapper<User>
```

因此可以使用：

```text
selectById(...)
selectList(...)
exists(...)
insert(...)
updateById(...)
deleteById(...)
```

调用关系：

```text
Service
   ↓
Mapper
   ↓
PostgreSQL
```

### `AdminUserItemResponse`
管理员查询用户列表/用户信息时，对外返回的 VO。

区别：

```text
User
    = 数据库模型

AdminUserItemResponse
    = API 返回模型
```

### `UserQueryService`
已经完成。

主要方法：

- `listUsers()`：查询全部用户，并按 ID 升序转换为 `AdminUserItemResponse`
- `getUser(Long userId)`：查询指定用户，不存在时抛出 `BusinessException`
- `existsByUsername(String username)`：检查用户名是否存在
- `existsByEmail(String email)`：检查邮箱是否存在
- `findById(Long userId)`：查询当前用户所需的基础信息

`findById()` 返回内部 `UserRecord`，包含：

```text
userId
userCode
displayName
systemRole
status
mustChangePassword
```

这样避免把完整数据库实体直接暴露给当前用户逻辑。

## 5. 已完成：auth 基础组件

### DTO
- `LoginRequest`
- `RegisterRequest`

负责接收登录、注册请求数据。

### `UserRefreshToken`
对应数据库中的 refresh token 记录。

### `UserRefreshTokenMapper`
负责 refresh token 数据库操作。

### `JwtAccessTokenService`
负责 Access Token（JWT）的创建/处理。

```text
登录成功
   ↓
JwtAccessTokenService
   ↓
Access Token
```

### `RefreshTokenService`
负责 Refresh Token 的生成、验证、撤销等逻辑。

### `PasswordHasher`
负责密码哈希。

```text
password
   ↓
hash
   ↓
password_hash
```

数据库不保存明文密码。

### `PasswordPolicyValidator`
负责检查密码是否符合规定的密码策略。

与 `PasswordHasher` 的职责区别：

```text
PasswordPolicyValidator
    ↓
“这个密码是否合法？”

PasswordHasher
    ↓
“把合法密码安全地转换成 hash”
```

### `RefreshTokenRecord`
用于表示 refresh token 相关记录/数据。

## 6. 已完成：`auth.CurrentUserService`

当前已经完成：

```text
auth
└── CurrentUserService.java
```

职责：

> 获取当前登录用户，并根据数据库状态、系统角色执行当前用户相关检查。

### `getRequiredCurrentUser()`

```text
UserContext.get()
      ↓
有没有 AuthenticatedUser？
      ↓
   ┌──┴──┐
   │     │
  没有   有
   │     │
   ↓     ↓
401     userId
         ↓
   查询数据库
         ↓
     CurrentUser
```

未登录：

```java
throw new UnauthorizedException("当前请求未登录");
```

### `requireSystemAdmin()`

```text
getRequiredCurrentUser()
        ↓
检查 systemRole
        ↓
ADMIN ?
  ├── 是 → 返回 CurrentUser
  └── 否 → 403 Forbidden
```

### `requireBusinessUser()`

要求当前用户不是系统管理员。

管理员访问普通业务区时返回 403。

### `loadUserById()`

内部方法：

```text
userId
  ↓
UserQueryService.findById()
  ↓
数据库 users
  ↓
检查用户是否存在
  ↓
检查是否 DISABLED
  ↓
生成 CurrentUser
```

用户不存在：

```text
BusinessException("当前用户不存在")
```

用户被禁用：

```text
BusinessException("账号已被禁用")
```

## 6.5 已完成：`auth.service.AuthService`（本次新增）

登录、注册、刷新令牌、登出的核心业务逻辑已全部实现，并配套了 `AuthCookieSupport` 处理 refresh token 的 Cookie 读写。

### `login(loginId, password)`

```text
LoginRequest
    ↓
校验 loginId / password 格式
    ↓
按用户名或邮箱查询用户（SELECT ... FOR UPDATE 加行锁）
    ↓
校验账号状态（DISABLED 拒绝）
    ↓
校验密码（BCrypt matches）
    ↓
撤销该用户所有旧 refresh token
    ↓
签发新 access token（JWT）
    ↓
签发新 refresh token
    ↓
更新 lastLoginAt
    ↓
返回 AuthTokens
```

要点：
- 用 `SELECT ... FOR UPDATE` 防止并发登录时的竞态。
- 增加了“登录标识匹配到多个用户”的兜底校验（`ensureUniqueLoginMatch`），避免脏数据导致越权。
- 密码长度做了双重限制：普通长度上限 + BCrypt 72 字节截断保护。

### `register(RegisterRequest)`

```text
RegisterRequest
    ↓
用户名归一化 + 合法字符校验（仅允许字母数字下划线短横线）
    ↓
排除保留用户名（admin/root/system 等）
    ↓
邮箱 / displayName 长度校验
    ↓
密码策略校验（PasswordPolicyValidator）
    ↓
检查用户名、邮箱唯一性
    ↓
密码 hash（BCrypt）
    ↓
写入 users（默认 USER 角色 / ACTIVE 状态）
```

### `refresh(refreshToken)`

```text
refreshToken
    ↓
查找有效 token 记录
    ↓
查询用户 + 状态校验
    ↓
撤销旧 token（原子操作）
    ↓
撤销失败 → 判定为重放攻击 → 撤销该用户全部 token，要求重新登录
    ↓
撤销成功 → 签发新的 access token + refresh token
```

要点：这里专门处理了 **refresh token 重放攻击**：如果撤销旧 token 失败（说明已经被用过一次），会连带撤销该用户全部 token，强制重新登录。

### `logout(refreshToken)`

```text
refreshToken
    ↓
撤销该 token
```

#### `AuthCookieSupport`

负责把 refresh token 写入/清除为 `httpOnly` + `SameSite=Lax` 的 Cookie（生产环境应为 `Secure`），避免前端 JS 直接接触 refresh token。

## 6.6 已完成：`auth.controller.AuthController` 与 `auth.security.JwtAuthenticationFilter`
 
 认证控制层与纯手写 JWT 认证拦截器已全部实现，并已通过编译。
 
 ### `AuthController`
 暴露 5 个核心端点：
 - `POST /api/auth/register`：用户注册，入参使用 `@Valid` 激活 Bean Validation，统一返回 `ApiResponse.ok()`。
 - `POST /api/auth/login`：用户登录，成功后调用 `AuthCookieSupport` 将 refresh token 写入 httpOnly Cookie，并返回包含 `accessToken` 的 `AuthTokensResponse`。
 - `POST /api/auth/refresh`：刷新令牌，自动从 Request Cookie 读取 refresh token 并调用 `AuthService.refresh()`，新 refresh token 回写 Cookie，返回新 access token。
 - `POST /api/auth/logout`：用户登出，从 Request Cookie 提取 refresh token 进行后端注销，并调用 `AuthCookieSupport.clearRefreshTokenCookie()` 清除浏览器 Cookie。
 - `GET /api/auth/me`：受保护接口，调用 `CurrentUserService.getRequiredCurrentUser()` 获取当前登录用户画像，返回 `CurrentUserProfileResponse`。
 
 ### `JwtAuthenticationFilter`
 继承 Spring 的 `OncePerRequestFilter`，实现纯手写无状态拦截：
 1. **白名单策略**：通过重写 `shouldNotFilter()` 放行 `/api/auth/login`、`/api/auth/register`、`/api/auth/refresh`、`/api/auth/logout` 等非受保护路由。
 2. **Token 解析**：从 `Authorization` 请求头提取 `Bearer <token>`，调用 `JwtAccessTokenService.parse()` 解码并验证签名。
 3. **上下文绑定**：将解析出的用户身份封装为 `AuthenticatedUser`，存入 `UserContext`（`ThreadLocal`）。
 4. **生命周期清理**：在 `finally` 块中调用 `UserContext.clear()`，防止 Tomcat 线程池复用导致上下文污染和内存泄漏。
 5. **未授权响应**：遇到格式错误或过期的 Token，直接使用 `ObjectMapper` 写回 401 统一响应 `ApiResponse<>(false, null, msg)`。
 
## 6.7 已完成：`group` 模块基础设施、模型、Mapper 与权限守卫服务（本次新增）

严格对照原版 `Argus-backend` 的设计模式，已完成群组全套底层数据基础设施，并 100% 通过编译。

### 1. 数据库持久层（PostgreSQL）
已在 Docker 容器数据库 `argus-pg` 中初始化并验证了群组领域的 4 张表：
- `groups`：群组/知识库主表（包含 `group_code`、`owner_user_id`、`status`）。
- `group_memberships`：成员关系表（包含 `groupId`、`userId`、`role`：`OWNER`/`MEMBER`）。
- `group_invitations`：群组邀请流转表（邀请人、被邀请人、状态机 `PENDING`/`ACCEPTED`/`REJECTED`/`CANCELED`）。
- `group_join_requests`：加入申请流转表（申请人、审批人、状态机 `PENDING`/`APPROVED`/`REJECTED`/`CANCELED`）。

### 2. 模型层（Entity / DTO / VO）
- **实体（Entity）**：`Group`、`GroupMembership`、`GroupInvitation`、`GroupJoinRequest`。
- **请求（DTO）**：
  - `CreateGroupRequest`：创建群组（带 Bean Validation `@NotBlank` / `@Size` 约束）。
  - `CreateInvitationRequest`：创建群组邀请。
- **响应（VO）**：
  - `GroupMemberResponse`：成员信息。
  - `MySentInvitationResponse`：我发出的邀请记录。
  - `MyJoinRequestResponse`：我的加入申请记录。
  - `OwnerJoinRequestResponse`：群主视角的加入申请记录。

### 3. 数据访问层（Mapper & XML）
- **`GroupMembershipMapper` & `GroupMembershipMapper.xml`**：
  - 继承 MyBatis-Plus `BaseMapper<Group>`。
  - 采用 PostgreSQL `INSERT ... RETURNING id` 规约实现高效单往返自增主键返回。
  - 实现 `selectOwnedGroupsByUserId`（包含待审批子查询统计）、`selectJoinedGroupsByUserId`、`selectPendingInvitationsByInviteeUserId` 等多表联查。
  - 采用 `updateInvitationStatus` 结合乐观并发控制（CAS，`WHERE id = #{id} AND status = #{fromStatus}`）。
- **`GroupJoinRequestMapper` & `GroupJoinRequestMapper.xml`**：
  - 继承 `BaseMapper<GroupJoinRequest>`。
  - 支持 `selectActiveGroupByCode`、`countPendingJoinRequest`、`selectMyJoinRequests` 等入群申请相关的全套复杂 SQL。

### 4. 权限守卫与可见性服务（`GroupMembershipService`）
- **可见性查询**：`listVisibleGroups()` 聚合拥有的群组、加入的群组与待处理邀请。
- **权限安全守卫（Guard）**：
  - `requireGroupReadable(groupId)`：检查当前用户是否为该组活跃成员，非成员立即抛出 `BusinessException`。
  - `requireGroupOwner(groupId)`：检查当前用户是否为群组 `OWNER`，非群主拒绝操作。

## 7. 当前最重要的认证与鉴权链路

```text
                    HTTP Request
                         │
                         ↓
                  Authorization
                         │
                         ↓
                       JWT
                         │
                         ↓
             JwtAuthenticationFilter        ← 已完成实现
                         │
                         ↓
                AuthenticatedUser
                         │
                         ↓
                  UserContext
                         │
                         ↓
               CurrentUserService
                         │
                         ↓
                 UserQueryService
                         │
                         ↓
                    UserMapper
                         │
                         ↓
                    PostgreSQL
```

三个核心概念：

```text
UserContext
    ↓
当前请求是谁

UserQueryService
    ↓
数据库里这个用户是谁、当前状态是什么

CurrentUserService
    ↓
当前用户能不能继续进行某种业务操作
```

## 8. 为什么 JWT 后还要查数据库？

例如：

```text
10:00
用户登录
 ↓
JWT userId = 123
```

之后：

```text
10:30
管理员禁用用户 123
```

JWT 可能仍未过期。

因此不能只相信 JWT：

```text
JWT
 ↓
userId = 123
 ↓
允许访问
```

当前设计是：

```text
JWT
 ↓
UserContext
 ↓
userId
 ↓
数据库 users
 ↓
检查 status
 ↓
DISABLED → 拒绝
ACTIVE   → 继续
```

这样用户状态变化可以及时生效。

## 9. 目前已经涉及的 Java/Spring 知识

- `@Service` / `@RestController` / `@Component`
- Spring Bean 与依赖注入
- MyBatis-Plus `BaseMapper`
- `LambdaQueryWrapper` / `LambdaUpdateWrapper`
- `ThreadLocal` 与请求上下文
- Java 21 `record` 与不可变 DTO/VO
- DTO / Entity / VO 分层隔离
- Service / Mapper 分层
- JJWT 0.12.x 签发与解析
- Access Token / Refresh Token 双令牌机制
- HTTP 401 Unauthorized / 403 Forbidden 语义规范
- 用户状态与系统角色权限
- 数据库查询与业务逻辑分离
- `@Transactional` 与事务边界
- 行级锁 `SELECT ... FOR UPDATE`
- Refresh Token 轮转与重放攻击检测
- httpOnly Cookie 传递与 XSS 防护
- `OncePerRequestFilter` 纯手写无状态过滤器

## 10. 下一步：本地运行与 Postman 全链路联调测试

`auth` 模块的代码（实体、Mapper、Service、Controller、Filter、VO/DTO）已全部编写完毕并通过编译。下一步推荐进行端到端全链路接口联调：

### 1. 环境准备
```bash
# 1. 启动数据库容器
docker compose up -d

# 2. 启动后端应用
./gradlew bootRun
```

### 2. 联调测试路径
1. **注册测试**：`POST /api/auth/register`
   - Body: `{"username": "testuser", "password": "Password123!", "email": "test@example.com", "displayName": "Test User"}`
   - 验证：返回 `{"success": true}`，数据库 `users` 产生一条新记录。
2. **登录测试**：`POST /api/auth/login`
   - Body: `{"loginId": "testuser", "password": "Password123!"}`
   - 验证：返回 `accessToken`，且响应头包含 `Set-Cookie: MYARGUS_REFRESH_TOKEN=...; HttpOnly; Path=/api/auth`。
3. **受保护端点访问**：`GET /api/auth/me`
   - 不带 Token：验证是否返回 401。
   - 带 Header `Authorization: Bearer <accessToken>`：验证是否正确返回当前用户画像。
4. **刷新 Token**：`POST /api/auth/refresh`
   - 携带 Cookie 请求，验证是否签发新 `accessToken`，旧 Refresh Token 是否被原子吊销。
5. **登出测试**：`POST /api/auth/logout`
   - 验证数据库中该 Refresh Token 被标记 `revoked_at`，响应头中 Cookie 被清空（`Max-Age=0`）。

### 3. 下一业务模块规划
认证全链路联调完毕后，按照 `Argus` 蓝图推进下一个核心业务模块：
- **`group` 模块**：知识库群组管理（`groups` 表、`group_memberships` 表、群组创建/查询/邀请与加入审批流程）。

## 11. 当前状态总览

| 模块 | 状态 |
|---|---|
| `common` | 🟢 基础结构已完成 |
| `common.security` | 🟢 `AuthenticatedUser` / `UserContext` 已建立 |
| `user.entity.User` | 🟢 已完成 |
| `user.mapper.UserMapper` | 🟢 已完成 |
| `user.vo.AdminUserItemResponse` | 🟢 已完成 |
| `user.service.UserQueryService` | 🟢 已完成 |
| `auth.config` | 🟢 已建立 |
| `auth.dto` | 🟢 已建立 |
| `auth.mapper` | 🟢 已建立 |
| `auth.security`（Jwt/RefreshToken/Cookie） | 🟢 已完成 |
| `auth.service` 基础类 | 🟢 已建立 |
| `auth.CurrentUserService` | 🟢 已完成 |
| `auth.AuthService` | 🟢 **已完成**（login/register/refresh/logout） |
| `auth.AuthController` | 🟢 **已完成**（5 个核心端点就绪） |
| `JwtAuthenticationFilter` | 🟢 **已完成**（已手写过滤与白名单） |
| `auth.model.vo` | 🟢 已完成（AuthTokensResponse、CurrentUserProfileResponse） |
| `/api/auth/me` | 🟢 已完成并通过联调 |
| 全链路接口联调验证 | 🟢 已通过联调与前端验证 |
| group（群组与知识库） | 🟡 **进行中**（4张表结构、枚举、Entity/DTO/VO、Mapper+XML、GroupMembershipService 已就绪并通过编译） |
| document（文档管理与分片上传） | ⏳ 后续 |
| ingestion（ETL流水线） | ⏳ 后续 |
| engine（PGvector向量混合检索） | ⏳ 后续 |
| qa（知识库问答） | ⏳ 后续 |
| assistant（AI助手） | ⏳ 后续 |
| metrics（计量统计） | ⏳ 后续 |

## 12. 开发纪律

每完成一个核心模块或阶段，执行：

```bash
./gradlew clean build
```

如果编译失败：
1. 先解决当前错误；
2. 不继续堆新的业务代码；
3. 编译通过后再进入下一步。

---

## 当前进度节点

**已完成：`group` 模块基础设施全量就绪：4 张表初始化 → 4 个枚举 → 4 个 Entity + 2 个 DTO + 4 个 VO → 2 个 Mapper 接口与 XML (`GroupMembershipMapper`, `GroupJoinRequestMapper`) → `GroupMembershipService`（权限守卫与可见性聚合），全量编译通过。**

**下一步：编写 `GroupManagementService`（群组创建、邀请、成员移除）与 `GroupJoinRequestService`（申请审批流程），随后推进 Controller 层端点暴露与接口测试。**