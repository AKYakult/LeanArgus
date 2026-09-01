# MyArgus 项目学习进度

> 目标：参照 Argus-backend 参考项目，从 0 用 Spring Boot + MyBatis-Plus + JWT 实现登录认证模块，不引入 Spring Security，手写 Filter 校验 JWT。

## 技术选型

| 项目        | 选择                                                                       | 说明                                                                                                                                |
|-------------|----------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| 构建工具    | Gradle Kotlin DSL (`build.gradle.kts`)                                     |                                                                                                                                     |
| Spring Boot | 4.1.1                                                                      | 比参考项目（3.5.0）新一个大版本，跟着学最新写法                                                                                     |
| Java        | 21                                                                         |                                                                                                                                     |
| 数据库      | PostgreSQL（Docker 部署）                                                  |                                                                                                                                     |
| ORM         | MyBatis-Plus                                                               | 用 `mybatis-plus-spring-boot4-starter:3.5.17`（不是 boot3-starter，Boot4 下 boot3-starter 会报 `factoryBeanObjectType` 兼容性错误） |
| 密码加密    | `spring-security-crypto`（仅引入 BCrypt 工具，不引入完整 Spring Security） |                                                                                                                                     |
| JWT         | `jjwt` 0.12.6（api + impl + jackson 三件套）| 不指定版本 建议用新的                                                                                                               | |
| 鉴权方式    | 手写 `JwtAuthenticationFilter`，不用 Spring Security                       |                                                                                                                                     |

## 整体路线图

1. ✅ 项目骨架打通：依赖 + 数据库连接 + 建表
2. ✅ `User` 实体 + Mapper（MyBatis-Plus），测试接口验证能查到数据
3. ⬜ 密码加密（BCrypt）+ 编写真正的注册逻辑
4. ⬜ 登录接口：校验用户名密码，返回 JWT
5. ⬜ JWT 工具类：签发 token、解析 token
6. ⬜ `JwtAuthenticationFilter`：拦截请求、解析 token、注入当前用户上下文
7. ⬜ 受保护接口 `/me`，验证整条链路
8. ⬜（可选）刷新令牌、统一异常处理、统一响应体

## 当前进度详情

### Step 1：项目骨架 + 数据库连接（已完成）

- `build.gradle.kts` 依赖齐全：web、validation、lombok、`spring-security-crypto`、jjwt 三件套、`mybatis-plus-spring-boot4-starter`、postgresql 驱动。
- `application.yaml` 配置数据源，端口按自己 docker 映射的实际端口来（不一定是默认 5432）。
- 用参考项目自带的 `schema.sql` 精简出 `users` + `user_refresh_tokens` 两张表，放在 `src/main/resources/sql/schema_temp.sql`。
- 配置自动建表：

  ```yaml
  spring:
    sql:
      init:
        mode: always
        continue-on-error: false
        schema-locations:
          - classpath:sql/schema_temp.sql
  ```

  原理：语句都是 `CREATE TABLE IF NOT EXISTS`，重复执行不会报错也不会清空数据，等价于"没有这张表就自动建"。
- 踩坑记录：新建的资源文件如果没有被 Rebuild，不会出现在 `build/resources/main` 下，Spring Boot 找不到文件也不会报错，只是静默跳过——遇到"表没建出来"先检查 `build` 目录下文件在不在，以及 IDEA 数据库面板有没有点刷新。

### Step 2：User 实体 + Mapper（已完成）

- 包结构（对照参考项目习惯，按模块分层）：

  ```
  com.example.myargus
  ├── user
  │   ├── entity
  │   │   └── User.java
  │   └── mapper
  │       └── UserMapper.java
  └── MyArgusApplication.java（加了 @MapperScan("com.example.myargus.**.mapper")）
  ```

- `User` 实体先用 `String` 存 `systemRole`/`status`（暂不用枚举，降低第一遍的复杂度，后面再升级）。
- `UserMapper` 继承 `BaseMapper<User>`，不用写 SQL 就有基础增删改查。
- 手动插入一条测试用户，写 `/test/users` 接口用 `userMapper.selectList(null)` 验证查询链路，验证通过。

## 下一步：Step 3

- 引入 `BCryptPasswordEncoder` 做密码加密。
- 写真正的用户注册逻辑（替代现在手动 SQL 插入测试数据）。
