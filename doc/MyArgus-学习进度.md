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

1. ✅ 项目骨架打通：依赖配置 + 数据库连接 + schema 自动建表[cite: 2]
2. ✅ `User` 实体 + Mapper（MyBatis-Plus），测试接口验证通过[cite: 2]
3. 🟡 密码加密 + 基础设施（统一响应与全局异常） + 用户注册逻辑（进行中）
  - ✅ 注册 `PasswordEncoder` (BCrypt)[cite: 2, 5]
  - ✅ 基于 Java Record 封装统一响应体 `ApiResponse<T>`[cite: 5]
  - ✅ 建立 `common.exception` 异常体系与 `GlobalExceptionHandler`[cite: 5]
  - ✅ 编写 `RegisterRequest` DTO（带 Bean Validation 注解）[cite: 5]
  - ⬜ 编写 `PasswordHasher` + `PasswordPolicyValidator`
  - ⬜ 编写 `AuthService` 实现注册逻辑（查重、加密、写库）
  - ⬜ 编写 `AuthController` 暴露 `POST /api/auth/register` 接口并进行 Postman 验证
4. ⬜ 登录接口：校验用户名密码，返回 JWT
5. ⬜ JWT 工具类：签发 token、解析 token（基于 JJWT 0.12.x）
6. ⬜ `JwtAuthenticationFilter`：拦截请求、解析 token、注入全局当前用户上下文（`UserContext`）
7. ⬜ 受保护接口 `/me`，验证整条鉴权拦截闭环链路
8. ⬜（可选）刷新令牌（Refresh Token）、角色权限校验拓展

---

## 当前已完成详情

### Step 1：项目骨架与数据库连接[cite: 2]
- `build.gradle.kts` 配置完整：涵盖 web、validation、lombok、`spring-security-crypto`、jjwt 0.12.6、`mybatis-plus-spring-boot4-starter`、postgresql 驱动[cite: 2]。
- `application.yaml` 配置 PostgreSQL 数据源，配合 `schema_temp.sql` 自动建表（`users` + `user_refresh_tokens`）[cite: 2, 5]。

### Step 2：User 实体与数据访问层[cite: 2]
- 模块包分层：`com.example.myargus.user`[cite: 2, 5]。
- `User` 实体完整映射 `users` 表字段，`UserMapper` 继承 MyBatis-Plus 的 `BaseMapper<User>`[cite: 2, 5]。
- 通过 `/test/users` 测试接口完成数据库连通性及 CRUD 读写验证[cite: 2, 5]。

### Step 3（前半部分）：通用基础设施与入参规范[cite: 5]
- **统一响应封装 (`common.api.ApiResponse<T>`)**[cite: 5]：
  - 基于 Java 21 Record 特性定义[cite: 2, 5]：
    ```java
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApiResponse<T>(boolean success, T data, String message){}
    ```
  - 提供 `ApiResponse.ok(data)`、`ApiResponse.ok()` 与 `ApiResponse.error(msg)` 静态工厂方法[cite: 5]。
- **全局异常拦截体系 (`common.exception`)**[cite: 5]：
  - 定义 `BusinessException` (400)、`UnauthorizedException` (401)、`ForbiddenException` (403)[cite: 5]。
  - 实现 `GlobalExceptionHandler`，完整覆盖自定义业务异常、参数校验（`MethodArgumentNotValidException`）、JSON 格式反序列化异常（`HttpMessageNotReadableException`）、文件大小超限异常（`MaxUploadSizeExceededException`）以及 500 系统未知兜底异常[cite: 5]。
- **安全配置与 DTO**[cite: 5]：
  - `common.config.SecurityConfig` 注册 `BCryptPasswordEncoder`[cite: 5]。
  - `auth.model.dto.RegisterRequest` 配置 `@NotBlank` 与 `@Size` 约束注解[cite: 5]。

---

## 缺少的组件与详细待办清单

为了从当前状态推进并完成整个登录认证与受保护接口拦截闭环，仍需补充以下文件：

### 阶段一：补齐用户注册业务（Step 3 完结）

1. **`auth/service/PasswordHasher.java`**
  - **职责**：封装 `BCryptPasswordEncoder`，提供统一的 `hash(rawPassword)` 和 `matches(raw, encoded)` 工具方法，隔离底层加密实现细节。
2. **`auth/service/PasswordPolicyValidator.java`**
  - **职责**：实现密码安全策略校验（校验密码长度 6-32 位、字符复杂度等），不符合规则时直接抛出 `BusinessException`。
3. **`auth/service/AuthService.java`**
  - **职责**：认证核心服务类，实现 `register(RegisterRequest req)` 方法。执行密码合规检查 -> MyBatis-Plus 检查用户名唯一性 -> 密码加密 -> 赋予默认角色（`USER`）与状态（`ACTIVE`） -> 插入数据库。
4. **`auth/controller/AuthController.java`**
  - **职责**：认证控制层，暴露 `POST /api/auth/register`，使用 `@Valid` 激活入参校验并统一返回 `ApiResponse.ok()`。

---

### 阶段二：登录接口与 JWT 签发（Step 4 & Step 5）

1. **`auth/model/dto/LoginRequest.java`**
  - **职责**：登录接口入参 DTO，包含 `username` 与 `password` 字段校验。
2. **`auth/model/vo/AuthTokensResponse.java`**
  - **职责**：登录成功出参 VO，封装 `accessToken`、`tokenType` ("Bearer") 以及过期时间等字段。
3. **`auth/security/JwtAccessTokenService.java`**
  - **职责**：基于 **JJWT 0.12.x** API 封装令牌管理：
    - 从 `application.yaml` 读取 `jwt.secret` 与 `jwt.expiration` 配置。
    - `generateToken(User user)`：将 `userId`、`username`、`role` 放入 Claims 并使用 HMAC-SHA256 签名生成 Access Token。
    - `parseToken(String token)`：解析并校验 Token 的有效性、签名真实性与过期时间；校验失败时抛出 `UnauthorizedException`。
4. **扩展 `AuthService` 与 `AuthController`**
  - 在 `AuthService` 中实现 `login(LoginRequest req)`：验证用户是否存在 -> `passwordHasher.matches` 校验密码 -> 调用 `JwtAccessTokenService` 签发 Token 并返回。
  - 在 `AuthController` 中新增 `POST /api/auth/login` 路由。

---

### 阶段三：无状态鉴权过滤器与用户上下文注入（Step 6 & Step 7）

1. **`common/security/AuthenticatedUser.java`**
  - **职责**：当前登录用户的数据载体（POJO/Record），包含 `Long userId`、`String username`、`String systemRole` 等轻量身份信息。
2. **`common/security/UserContext.java`**
  - **职责**：基于 `ThreadLocal<AuthenticatedUser>` 封装全局用户上下文，提供 `set(user)`、`get()`、`clear()` 静态方法，使后续业务模块无需传参即可随处获取当前登录用户。
3. **`auth/security/JwtAuthenticationFilter.java`**
  - **职责**：继承 `OncePerRequestFilter` 实现纯手写拦截器：
    - 检查 HTTP 请求头的 `Authorization` 字段是否以 `Bearer ` 开头。
    - 提取 Token 并调用 `JwtAccessTokenService.parseToken()`。
    - 将解析出来的用户信息封装为 `AuthenticatedUser` 存入 `UserContext`。
    - 在 `finally` 块中调用 `UserContext.clear()`，防止线程池复用导致的上下文污染与内存泄漏。
4. **`common/config/FilterConfig.java`**
  - **职责**：使用 `FilterRegistrationBean` 将 `JwtAuthenticationFilter` 注册进 Servlet 容器，并配置路径映射及白名单放行策略（如放行 `/api/auth/login`、`/api/auth/register`、`/test/**`）。
5. **受保护接口测试验证 (`/api/auth/me`)**
  - **`auth/model/vo/CurrentUserProfileResponse.java`**：用户信息展示 VO。
  - 在 `AuthController` 中新增受保护接口 `GET /api/auth/me`，直接从 `UserContext.get()` 提取登录信息并返回，完成全链路验证。