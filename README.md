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
  ```http
  GET /hello.txt HTTP/1.1
  Host: localhost:8080
  User-Agent: SimpleSocketClient/1.0
  Connection: keep-alive
  ```

- **200 响应示例**
  ```http
  HTTP/1.1 200 OK
  Content-Type: text/plain; charset=UTF-8
  Content-Length: 30
  Server: SimpleSocketServer/1.0
  Connection: keep-alive

  Hello Socket HTTP!
  这是一个纯文本文件
  ```

- **301 重定向响应示例**
  ```http
  HTTP/1.1 301 Moved Permanently
  Location: /new
  Content-Length: 0
  Connection: keep-alive
  ```
  - 测试方式：访问``http://localhost:8080/old``资源
- **302 临时重定向响应示例**
  ```http
  HTTP/1.1 302 Found
  Location: /temp-redirect
  Content-Length: 0
  Server: SimpleSocketServer/1.0
  Connection: keep-alive
  ```
  - 测试方式：访问``http://localhost:8080/temp``资源

- **304 未修改响应示例**
  ```http
  HTTP/1.1 304 Not Modified
  ETag: "abc123"
  Date: Mon, 15 Nov 2023 10:00:00 GMT
  Server: SimpleSocketServer/1.0
  Connection: keep-alive
  ```
  - 测试方式：重复请求相同资源（已经缓存）



- **401 未授权响应示例**
  ```http
  HTTP/1.1 401 Unauthorized
  WWW-Authenticate: Basic realm="Restricted Area"
  Content-Type: text/plain; charset=UTF-8
  Content-Length: 20
  Server: SimpleSocketServer/1.0
  Connection: keep-alive

  需要身份验证访问
  ```

- **404 未找到响应示例**
  ```http
  HTTP/1.1 404 Not Found
  Content-Type: text/plain; charset=UTF-8
  Content-Length: 25
  Server: SimpleSocketServer/1.0
  Connection: keep-alive

  请求的资源不存在
  ```
- **405 方法不允许响应示例**
  ```http
  HTTP/1.1 405 Method Not Allowed
  Allow: GET, POST
  Content-Type: text/plain; charset=UTF-8
  Content-Length: 25
  Server: SimpleSocketServer/1.0
  Connection: keep-alive

  不支持的HTTP请求方法
  ```
  - 测试方式：使用没有实现的``DELET``方法
- **409 User重复注册**
  ```http
  HTTP/1.1 409 Conflict
  Content-Type: text/plain; charset=UTF-8
  Content-Length: 44
  Connection: keep-alive

  注册失败(可能已存在或参数错误)
  ```
- **422 上传文件非法命名**
  ```http
  HTTP/1.1 422 Unprocessable Entity
  Content-Type: text/plain; charset=UTF-8
  Content-Length: 15
  Connection: keep-alive

  非法文件名
  ```
- **500 服务器内部错误响应示例**
  ```http
  HTTP/1.1 500 Internal Server Error
  Content-Type: text/plain; charset=UTF-8
  Content-Length: 35
  Server: SimpleSocketServer/1.0
  Connection: keep-alive

  服务器内部错误，请稍后重试
  ```

## 项目结构与文件功能
##  项目结构

```
src/main/java/com/example/http/
├── Main.java                
├── SimpleHttpServer.java    
├── SimpleHttpWorker.java    
├── HttpClientGui.java       
├── http/                    
│   ├── HttpRequest.java     
│   └── HttpResponse.java    
└── user/                    
    └── UserService.java     

src/main/resources/public/   
local
```
### 核心文件清单
| 文件路径                         | 功能概述                         |
|------------------------------|------------------------------|
| `Main.java`                  | 程序入口，解析命令行参数并启动服务器/客户端       |
| `SimpleHttpServer.java`      | HTTP 服务器主类，负责监听端口、管理连接和线程池   |
| `SimpleHttpWorker.java`      | 处理单个客户端连接的请求，解析请求并生成响应       |
| `http/HttpRequest.java`      | 封装 HTTP 请求，解析方法、路径、头部、表单参数等  |
| `http/HttpResponse.java`     | 构建 HTTP 响应，包含状态码、头部和体部，支持序列化 |
| `HttpClientGui.java`         | 客户端 GUI 实现，支持发送请求、处理响应和重定向   |
| `src/main/resources/public/` | 静态资源目录（HTML、图片、文本文件等）        |
| `local`                      | 客户端的资源（HTML、图片、文本文件等）                       |


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

##  功能特性

### HTTP 服务器
- **多线程处理**：使用线程池处理并发连接，高效稳定。
- **HTTP 协议支持**：支持 HTTP/1.1 协议，包括长连接（Keep-Alive）。
- **静态资源服务**：支持 HTML、JSON、文本等静态文件的访问。
- **RESTful 风格**：支持 GET、POST 等基本请求方法。
- **无依赖**：仅使用 Java 标准库，无任何第三方依赖。

### HTTP 客户端 (GUI)
- **图形化界面**：基于 Swing 的直观操作界面。
- **请求构建**：支持自定义 HTTP 方法、路径、Header 和 Body。
- **连接池**：内置连接池管理，支持复用 TCP 连接。
- **实时日志**：清晰展示请求报文和响应报文详情。

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
java -jar target/simple-http-socket-1.0-SNAPSHOT.jar client 8080[可选]
```
具体演示步骤

## 1.注册

### 请求报文
```http
POST /register HTTP/1.1
Host: localhost
User-Agent: SimpleSocketClient/1.0
Accept: */*
Content-Type: application/x-www-form-urlencoded
Content-Length: 27
Connection: keep-alive
username=test&password=test
```

#### 请求行: 
POST /register HTTP/1.1

方法: POST - 提交数据到服务器

路径: /register - 请求的资源路径

版本: HTTP/1.1 - HTTP协议版本

#### 请求头:

Host: localhost - 目标主机

User-Agent: SimpleSocketClient/1.0 - 客户端信息

Accept: */* - 可接受任何类型的响应

Content-Type: application/x-www-form-urlencoded - 请求体格式

Content-Length: 27 - 请求体长度

Connection: keep-alive - 保持连接

#### 空行: 分隔头部和主体

#### 请求体: 
username=test&password=test

表单数据，包含用户名和密码
### 响应报文
```
HTTP/1.1 200 OK
Content-Type: text/plain; charset=UTF-8
Content-Length: 12
Connection: keep-alive
```
状态行: HTTP/1.1 200 OK

版本: HTTP/1.1 - HTTP协议版本

状态码: 200 - 表示请求成功

状态短语: OK - 状态码的文本描述

响应头:

Content-Type: text/plain; charset=UTF-8

内容类型: 纯文本

字符编码: UTF-8（支持中文）

Content-Length: 12

响应体长度：12字节（"注册成功"在UTF-8编码下占12字节）

Connection: keep-alive - 保持连接，可复用

空行: 分隔头部和主体

响应体: 注册成功

服务器返回的实际内容

纯文本格式，告知用户注册操作成功

## 2.登录

请求报文与login大同小异

响应报文多了一行Set-Cookie: SID=b63675ed-f809-4437-aa1f-09d099a91e21; Path=/; HttpOnly

在客户端存储数据，用于维持HTTP状态，让服务器能够识别用户。

Cookie名称和值：

SID=b63675ed-f809-4437-aa1f-09d099a91e21

SID - Cookie名称

b63675ed-f809-4437-aa1f-09d099a91e21 - Cookie值，这是一个UUID格式的字符串

属性：

Path=/ - Cookie的作用路径

HttpOnly - 安全属性 防止javascript读取
## 上传文件
#### 请求报文
```http
POST /upload/test.pdf HTTP/1.1
Host: localhost
User-Agent: SimpleSocketClient/1.0
Accept: */*
Content-Type: multipart/form-data; boundary=----WSOCK1763355422477
Content-Length: 1407
Cookie: SID=afcd8746-c316-42d5-8ab7-1d4a33f34c2b
Connection: keep-alive
------WSOCK1763355422477
Content-Disposition: form-data; name="file"; filename="test.pdf"
Content-Type: application/octet-stream
[文件内容: 1243 bytes]
```

上传文件到服务器的 /upload/test.pdf 路径

使用multipart格式，边界字符串为 ----WSOCK1763355422477

------WSOCK1763355422477--
#### 响应报文
 与上面大同小异
## GET
#### 请求报文
```
GET /test.pdf HTTP/1.1
Host: localhost
User-Agent: SimpleSocketClient/1.0
Accept: */*
If-Modified-Since: Mon, 17 Nov 2025 12:57:02 CST
Cookie: SID=4d7ae61a-5af4-4f1d-a789-b3f3170be723
Connection: keep-alive
```
#### 响应报文
```
HTTP/1.1 304 Not Modified
Date: Mon, 17 Nov 2025 23:11:03 CST
Last-Modified: Mon, 17 Nov 2025 12:57:02 CST
Server: SimpleSocketServer/1.0
Connection: keep-alive
Content-Length: 0
```