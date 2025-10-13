# Simple Java Socket HTTP

纯 Java Socket 实现的迷你 HTTP 服务器与客户端（含 Swing GUI 客户端），满足作业要求。

## 功能概要

### 服务器端
- **HTTP 方法**：支持 GET / POST
- **状态码**：200 / 301 / 302 / 304 / 401 / 404 / 405 / 500
- **长连接**：支持 `Connection: keep-alive`，同一连接可处理多个请求
- **MIME 类型**：text/html, text/plain, image/png, image/jpeg 等（可扩展）
- **静态文件服务**：`/` -> index.html，其他路径映射到 `src/main/resources/public`
- **上传下载目录**：上传文件和 GET 下载文件保存在 `WEB/local/`（与静态资源分离）
- **重定向示例**：`/old` -> 301 -> `/new`，`/temp` -> 302 -> `/`
- **条件缓存**：支持 `If-Modified-Since` 返回 304
- **用户认证**：
  - POST /register (body: username=...&password=...)
  - POST /login (body: username=...&password=...)
  - POST /logout (清除会话)
  - Cookie-based session (SID)
- **文件上传**：POST /upload (multipart/form-data)，文件名白名单 `[a-zA-Z0-9._-]`

### GUI 客户端
- **基本请求**：支持 GET/POST，自定义请求体
- **自动重定向**：301/302 自动跟随（最多 5 次）
- **条件缓存**：304 缓存提示，本地保存 Last-Modified
- **注册登录**：GUI 按钮直接注册/登录，显示登录状态
- **文件上传**：选择文件上传（限制 5MB），自动发送 multipart 请求
- **自动保存**：GET 200 响应自动保存到 `local/` 目录（支持二进制文件）
- **会话管理**：自动携带 Cookie，支持注销

## 运行方式 (Maven)

**编译打包：**
```bash
mvn clean package
```

**启动服务器：**
```bash
java -jar target/simple-http-socket-1.0-SNAPSHOT.jar server 8080[可选]
```

**启动客户端 GUI：**
```bash
java -jar target/simple-http-socket-1.0-SNAPSHOT.jar client
```

|TODO|
|---|
|具体演示步骤|
|详细技术方案|