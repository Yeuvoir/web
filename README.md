# Simple Java Socket HTTP

纯 Java Socket 实现的迷你 HTTP 服务器与客户端（含 Swing GUI 客户端），满足作业要求。

## 功能概要

# Simple Java Socket HTTP

纯 Java Socket 实现的迷你 HTTP 服务器与客户端（含 Swing GUI 客户端），通过底层 Socket 通信模拟 HTTP 协议的核心功能。

HTTP（超文本传输协议）是基于请求-响应模式的应用层协议，主要由以下部分组成：
- **请求（Request）**
    - 请求行：包含方法（GET/POST等）、路径、协议版本（如 `GET /index.html HTTP/1.1`）
    - 头部（Headers）：键值对形式的元数据（如 `Host: localhost:8080`、`Content-Type: text/html`）
    - 空行：分隔头部和体部
    - 体部（Body）：可选，用于 POST 等方法传递数据（如表单参数）
- **响应（Response）**
    - 状态行：包含协议版本、状态码、原因短语（如 `HTTP/1.1 200 OK`）
    - 头部（Headers）：与请求头部格式一致（如 `Content-Length: 1024`）
    - 空行：分隔头部和体部
    - 体部（Body）：服务器返回的数据（如 HTML 内容、图片二进制数据）

- **GET 请求示例**
  ```
  GET /hello.txt HTTP/1.1
  Host: localhost:8080
  User-Agent: SimpleSocketClient/1.0
  Connection: keep-alive
  ```

- **200 响应示例**
  ```
  HTTP/1.1 200 OK
  Content-Type: text/plain; charset=UTF-8
  Content-Length: 30
  Server: SimpleSocketServer/1.0
  Connection: keep-alive

  Hello Socket HTTP!
  这是一个纯文本文件
  ```

- **301 重定向响应示例**
  ```
  HTTP/1.1 301 Moved Permanently
  Location: /new
  Content-Length: 0
  Connection: keep-alive
  ```


## 项目结构与文件功能

### 核心文件清单
| 文件路径 | 功能概述 |
|----------|----------|
| `Main.java` | 程序入口，解析命令行参数并启动服务器/客户端 |
| `SimpleHttpServer.java` | HTTP 服务器主类，负责监听端口、管理连接和线程池 |
| `SimpleHttpWorker.java` | 处理单个客户端连接的请求，解析请求并生成响应 |
| `http/HttpRequest.java` | 封装 HTTP 请求，解析方法、路径、头部、表单参数等 |
| `http/HttpResponse.java` | 构建 HTTP 响应，包含状态码、头部和体部，支持序列化 |
| `HttpClientGui.java` | 客户端 GUI 实现，支持发送请求、处理响应和重定向 |
| `src/main/resources/public/` | 静态资源目录（HTML、图片、文本文件等） |


### 详细功能说明

#### 1. `Main.java`
程序入口类，负责解析命令行参数并启动对应模式（服务器/客户端）。

- **核心函数**
    - `main(String[] args)`：主入口，根据参数判断启动服务器或客户端
    - `startServerMode(String[] args)`：启动服务器模式，解析端口号（默认8080）
    - `startClientMode(String[] args)`：启动客户端 GUI，支持指定服务器端口
    - `parsePortNumber(String portStr)`：解析端口号，验证范围（1-65535）


#### 2. `SimpleHttpServer.java`
HTTP 服务器主类，基于 ServerSocket 监听端口，使用线程池处理并发连接。

- **核心函数**
    - `start()`：启动服务器，循环接受客户端连接
    - `handleNewConnection(Socket clientSocket)`：处理新连接，配置 Socket 并提交到线程池
    - `configureSocket(Socket socket)`：配置 Socket 选项（超时、TCP 无延迟等）
    - `stop()`：停止服务器，关闭线程池


#### 3. `SimpleHttpWorker.java`
处理单个客户端连接的工作类（实现 Runnable），负责解析 HTTP 请求并生成响应。

- **核心函数**
    - `run()`：线程入口，循环处理连接上的多个请求（支持 Keep-Alive）
    - `parseRequest(InputStream in)`：解析请求行、头部和体部，生成 HttpRequest 对象
    - `processRequest(HttpRequest request)`：处理请求，路由到对应处理器（静态资源、注册/登录等）
    - `sendResponse(OutputStream out, HttpResponse response, boolean keepAlive)`：发送响应到客户端
    - `handle(HttpRequest req)`：核心路由逻辑，处理静态资源、重定向、注册/登录、文件上传等


#### 4. `http/HttpRequest.java`
封装 HTTP 请求的工具类，提供便捷的请求信息访问方法。

- **核心函数**
    - `setStartLine(String method, String uri, String version)`：设置请求行，拆分路径和查询字符串
    - `addHeader(String name, String value)`：添加请求头部（自动转为小写键）
    - `headerFirst(String name)`：获取指定头部的第一个值
    - `setBody(byte[] body)`：设置请求体，自动解析表单参数（application/x-www-form-urlencoded）
    - `form(String key)`：获取表单参数值
    - `cookie(String name)`：获取 Cookie 值


#### 5. `http/HttpResponse.java`
构建 HTTP 响应的工具类，支持链式调用，负责生成响应字节流。

- **核心函数**
    - `status(HttpStatus status)`：设置响应状态码（链式调用）
    - `header(String name, String value)`：添加响应头部（链式调用）
    - `bodyText(String text, String contentType)`：设置文本响应体，自动处理编码和长度
    - `toBytes(boolean keepAlive)`：将响应转换为字节数组，包含状态行、头部和体部


#### 6. `HttpClientGui.java`
Swing 实现的客户端 GUI，支持发送 GET/POST 请求，处理重定向、Cookie 和缓存。

- **核心函数**
    - `sendHttpWithBytes(...)`：发送 HTTP 请求，返回头部和体部字节数据
    - 处理逻辑：
        - 解析 URL 并建立 Socket 连接（支持连接池复用）
        - 构建请求头和体部，发送到服务器
        - 解析响应，处理重定向（301/302）、缓存（304）和 Cookie
        - 根据服务器指令关闭或复用连接


#### 7. 静态资源文件
- `index.html`：默认首页，包含测试链接（文本、图片、重定向示例）
- `hello.txt`：纯文本测试文件，验证 text/plain MIME 类型
- `test.png`：图片文件，验证二进制文件传输

## 功能特点
- 支持 HTTP/1.1 长连接（Keep-Alive）
- 处理静态资源（HTML、文本、图片等）
- 支持 301 永久重定向和 302 临时重定向
- 实现用户注册/登录功能（基于 Session 和 Cookie）
- 支持文件上传（保存到静态资源目录）
- 客户端支持缓存（If-Modified-Since 头部）和 Cookie 持久化

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
