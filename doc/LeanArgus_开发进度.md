# LeanArgus 后端开发进度

> 更新时间：2026-09-03
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
│   ├── mapper
│   │   └── UserRefreshTokenMapper
│   ├── model
│   │   ├── dto
│   │   │   ├── LoginRequest
│   │   │   └── RegisterRequest
│   │   └── entity
│   │       └── UserRefreshToken
│   ├── security
│   │   ├── JwtAccessTokenService
│   │   └── RefreshTokenService
│   └── service
│       ├── PasswordHasher
│       ├── PasswordPolicyValidator
│       └── RefreshTokenRecord
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

> 以上表示目前已经建立/实现的主要结构，不代表整个认证链路已经完整联通。

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

## 7. 当前最重要的认证链路

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
             JwtAuthenticationFilter
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

- `@Service`
- Spring Bean
- 构造器依赖注入
- MyBatis-Plus `BaseMapper`
- `LambdaQueryWrapper`
- `ThreadLocal`
- Java `record`
- DTO / Entity / VO
- Service / Mapper 分层
- JWT
- Access Token
- Refresh Token
- HTTP 401 / 403
- 用户状态与系统角色
- 数据库查询与业务层分离

## 10. 下一步：`AuthService`

下一步严格参考完整 `Argus` 项目实现：

```text
auth/service/AuthService.java
```

这是目前认证模块中比较复杂的一步。

### 登录

```text
LoginRequest
    ↓
AuthService
    ↓
查询用户
    ↓
验证密码
    ↓
检查账号状态
    ↓
撤销旧 Refresh Token
    ↓
生成 Access Token
    ↓
生成 Refresh Token
    ↓
返回登录结果
```

### 注册

```text
RegisterRequest
    ↓
AuthService
    ↓
检查 username
    ↓
检查 email
    ↓
密码策略检查
    ↓
密码 hash
    ↓
INSERT users
```

### Refresh Token

```text
Refresh Token
    ↓
验证
    ↓
查询数据库
    ↓
生成新的 Access Token
```

### Logout

```text
Logout
    ↓
撤销 Refresh Token
```

下一步重点解释：

- `@Transactional`
- 登录事务
- Refresh Token 生命周期
- 密码验证
- JWT 生成
- Access Token 与 Refresh Token 的区别
- `SELECT ... FOR UPDATE` 的作用
- 为什么需要数据库锁
- 登录失败应该抛什么异常

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
| `auth.security` | 🟢 基础服务已建立 |
| `auth.service` 基础类 | 🟢 已建立 |
| `auth.CurrentUserService` | 🟢 已完成 |
| `auth.AuthService` | 🟡 **下一步** |
| `auth.AuthController` | ⏳ 等 `AuthService` 后实现 |
| `JwtAuthenticationFilter` | ⏳ 后续 |
| `/api/auth/me` | ⏳ 后续 |
| Controller | ⏳ 后续 |
| group | ⏳ 后续 |
| document | ⏳ 后续 |
| ingestion | ⏳ 后续 |
| engine | ⏳ 后续 |
| qa | ⏳ 后续 |
| assistant | ⏳ 后续 |
| metrics | ⏳ 后续 |

## 12. 开发纪律

每完成一个核心类，执行：

```bash
./gradlew clean build
```

如果编译失败：

1. 先解决当前错误；
2. 不继续堆新的业务代码；
3. 编译通过后再进入下一步。

当前不要提前实现后面的 Controller、Group、Document 等模块。

**当前目标只有一个：把 `auth` 模块的认证链路按照参考项目完整跑通。**

---

## 当前进度节点

**已完成：`UserQueryService` → `AuthenticatedUser` / `UserContext` → `CurrentUserService`**

**下一步：`AuthService`**

当前不要跳到 Controller 或其他业务模块。

下一步严格按照 `Argus` 参考项目检查并实现 `AuthService`，先把注册/登录核心业务逻辑搞清楚，再继续 JWT Filter 闭环。
