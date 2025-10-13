package com.example.http;

import com.example.http.http.*;
import com.example.http.user.UserService;

import java.io.*;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class SimpleHttpWorker implements Runnable {
    
    // 客户端Socket连接
    private final Socket socket;
    
    // 用户服务，处理注册和登录
    private static final UserService USER_SERVICE = new UserService();
    
    // 服务器名称，用于响应头
    private static final String SERVER_NAME = "SimpleSocketServer/1.0";
    
    // 静态资源目录路径
    // 所有静态文件（HTML、CSS、JS、图片等）都存放在这里
    // 上传的文件也会保存到这里，便于通过HTTP直接访问
    private static final Path PUBLIC_ROOT = Paths.get("src","main","resources","public");
    
    // 会话存储：sessionId -> 用户名
    // 使用ConcurrentHashMap确保线程安全
    private static final Map<String,String> SESSIONS = new java.util.concurrent.ConcurrentHashMap<>();
    
    // 会话Cookie的名称
    private static final String SESSION_COOKIE = "SID";
    
    // HTTP日期格式化器（RFC1123格式）
    // 使用ThreadLocal确保每个线程有自己的格式化器实例，避免线程安全问题
    private static final ThreadLocal<SimpleDateFormat> RFC_1123 = ThreadLocal.withInitial(() -> {
        SimpleDateFormat f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return f;
    });
    
    public SimpleHttpWorker(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        String clientAddress = socket.getRemoteSocketAddress().toString();
        System.out.println("[服务器] 开始处理连接: " + clientAddress);
        
        try (InputStream in = socket.getInputStream(); 
            OutputStream out = socket.getOutputStream()) {
            
            // HTTP/1.1默认保持连接，除非客户端明确要求关闭
            boolean keepAlive = true;
            int requestCount = 0;
            
            // 循环处理连接上的所有HTTP请求
            while (keepAlive && !socket.isClosed()) {
                try {
                    // 解析HTTP请求
                    HttpRequest request = parseRequest(in);
                    if (request == null) {
                        System.out.println("[服务器] 客户端关闭连接或发送无效数据: " + clientAddress);
                        break;
                    }
                    
                    requestCount++;
                    logRequest(request, requestCount, clientAddress);
                    
                    // 检查客户端是否要求关闭连接
                    keepAlive = shouldKeepConnectionAlive(request, keepAlive);
                    
                    // 处理请求并生成响应
                    HttpResponse response = processRequest(request);
                    
                    // 发送响应给客户端
                    sendResponse(out, response, keepAlive);
                    
                    // 检查是否需要关闭连接
                    keepAlive = shouldCloseConnection(response, keepAlive);
                    
                    logConnectionStatus(keepAlive, requestCount);
                    
                } catch (Exception e) {
                    System.err.println("[服务器] 处理请求时发生错误: " + e.getMessage());
                    sendErrorResponse(out, e);
                    break;
                }
            }
            
        } catch (IOException e) {
            System.err.println("[服务器] 连接处理异常,关闭当前worker连接: " + e.getMessage());
        } finally {
            closeConnection(clientAddress);
        }
    }
    
    /**
     * 记录请求信息
     */
    private void logRequest(HttpRequest request, int requestCount, String clientAddress) {
        System.out.println(String.format("[服务器] 处理请求 #%d - %s %s (连接: %s)", 
                requestCount, request.method(), request.path(), clientAddress));
    }
    
    /**
     * 判断是否应该保持连接
     */
    private boolean shouldKeepConnectionAlive(HttpRequest request, boolean currentKeepAlive) {
        String connectionHeader = request.headerFirst("connection");
        if (connectionHeader != null && connectionHeader.equalsIgnoreCase("close")) {
            System.out.println("[服务器] 客户端请求关闭连接");
            return false;
        }
        return currentKeepAlive;
    }
    
    /**
     * 处理HTTP请求
     */
    private HttpResponse processRequest(HttpRequest request) {
        try {
            return handle(request);
        } catch (Exception ex) {
            System.err.println("[服务器] 请求处理异常: " + ex.getMessage());
            return createErrorResponse(ex);
        }
    }
    
    /**
     * 如果响应错误，创建500错误码
     */
    private HttpResponse createErrorResponse(Exception ex) {
        return new HttpResponse()
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyText("服务器内部错误: " + ex.getMessage(), "text/plain; charset=UTF-8");
    }
    
    /**
     * 发送HTTP响应
     */
    private void sendResponse(OutputStream out, HttpResponse response, boolean keepAlive) throws IOException {
        try {
            byte[] responseBytes = response.toBytes(keepAlive);
            out.write(responseBytes);
            out.flush();
        } catch (IOException e) {
            System.err.println("[服务器] 发送响应失败: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * 发送错误响应
     */
    private void sendErrorResponse(OutputStream out, Exception e) {
        try {
            HttpResponse errorResponse = createErrorResponse(e);
            sendResponse(out, errorResponse, false);
        } catch (IOException ioException) {
            System.err.println("[服务器] 发送错误响应失败: " + ioException.getMessage());
        }
    }
    
    /**
     * 判断是否应该关闭连接
     */
    private boolean shouldCloseConnection(HttpResponse response, boolean currentKeepAlive) {
        // 这里简化处理，默认不主动关闭连接
        return currentKeepAlive;
    }
    
    /**
     * 记录连接状态
     */
    private void logConnectionStatus(boolean keepAlive, int requestCount) {
        if (keepAlive) {
            System.out.println("[服务器] 保持连接，等待下一个请求... (已处理: " + requestCount + " 个请求)");
        } else {
            System.out.println("[服务器] 将关闭连接 (总共处理: " + requestCount + " 个请求)");
        }
    }
    
    /**
     * 关闭连接
     */
    private void closeConnection(String clientAddress) {
        try {
            if (!socket.isClosed()) {
                socket.close();
                System.out.println("[服务器] 连接已关闭: " + clientAddress);
            }
        } catch (IOException e) {
            System.err.println("[服务器] 关闭连接时发生错误: " + e.getMessage());
        }
    }


    /**
     * 解析请求：读取起始行 + 头部 + 可选 body。
     */
    private HttpRequest parseRequest(InputStream in) throws IOException {
        // 读取请求行
        String start = readLine(in);
        if (start == null || start.isEmpty()) return null;
        String[] parts = start.split(" ");
        if (parts.length < 3) return null;
        HttpRequest req = new HttpRequest();
        req.setStartLine(parts[0], parts[1], parts[2]);
        // 读取头部
        String l;
        while ((l = readLine(in)) != null) {
            if (l.isEmpty()) break; // 头结束
            int c = l.indexOf(':');
            if (c > 0) {
                String name = l.substring(0,c).trim();
                String value = l.substring(c+1).trim();
                req.addHeader(name, value);
            }
        }
        int contentLength = 0;
        String cl = req.headerFirst("content-length");
        if (cl != null) {
            try { contentLength = Integer.parseInt(cl); } catch (NumberFormatException ignore) {}
        }
        if (contentLength > 0) {
            byte[] body = in.readNBytes(contentLength);
            req.setBody(body);
        }
        return req;
    }

    // 读取一行（不包含 CRLF），支持 CRLF 或 LF 结尾
    private String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int b;
        boolean seenCR = false;
        while ((b = in.read()) != -1) {
            if (b == '\r') {
                seenCR = true;
                continue;
            }
            if (b == '\n') {
                break;
            }
            if (seenCR) { // 上个是CR但此处不是LF，按行终止
                // 回退当前字节（简单处理：放弃回退，继续拼接；HTTP标准中CR必须跟LF，这里宽松）
            }
            bos.write(b);
        }
        if (b == -1 && bos.size()==0) return null;
        return bos.toString(StandardCharsets.US_ASCII);
    }

    private HttpResponse handle(HttpRequest req) throws IOException, ParseException {
        // 限制：只支持 HTTP/1.1
    // keep-alive 由外层控制
        String method = req.method();
        String path = decodePath(req.path());
        if (path == null || path.isEmpty()) path = "/";

    // 确保静态资源目录存在（上传会写入该目录）
    try { Files.createDirectories(PUBLIC_ROOT); } catch (IOException ignore) {}

        // 路由：用户接口
        if ("POST".equalsIgnoreCase(method) && "/register".equals(path)) {
            return handleRegister(req);
        }
        if ("POST".equalsIgnoreCase(method) && "/login".equals(path)) {
            return handleLogin(req);
        }
        if ("POST".equalsIgnoreCase(method) && path.startsWith("/upload")) {
            return handleUpload(req);
        }
        if ("POST".equalsIgnoreCase(method) && "/logout".equals(path)) {
            return handleLogout(req);
        }

        // 测试 500 错误的端点
        if ("/test500".equals(path)) {
            try {
                // 故意触发异常
                throw new ArithmeticException("/ by zero");
            } catch (Exception e) {
                return new HttpResponse().status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .bodyText("500 Internal Server Error: " + e.getClass().getSimpleName() +
                                " - " + e.getMessage(), "text/plain; charset=UTF-8");
            }
        }

        // 重定向示例
        if ("/old".equals(path)) {
            return redirect(HttpStatus.MOVED_PERMANENTLY, "/new");
        }
        if ("/temp".equals(path)) {
            return redirect(HttpStatus.FOUND, "/");
        }
        if ("/new".equals(path)) {
            Path file = PUBLIC_ROOT.resolve("new.html");
            if (Files.exists(file)) {
                return serveStatic(req, file, "new.html");
            }
        }

        // 仅支持 GET/POST 访问静态/简单动态
        if (!method.equalsIgnoreCase("GET") && !method.equalsIgnoreCase("POST")) {
            return new HttpResponse().status(HttpStatus.METHOD_NOT_ALLOWED)
                    .bodyText("Method Not Allowed", "text/plain; charset=UTF-8");
        }

        // 鉴权：除登录/注册/重定向入口外，静态资源需已登录
        String username = authenticate(req);
        if (username == null) {
            return new HttpResponse().status(HttpStatus.UNAUTHORIZED)
                    .header("WWW-Authenticate", "Cookie realm=\"Simple\"")
                    .bodyText("401 Unauthorized - 请先登录", "text/plain; charset=UTF-8");
        }

        // 根路径 -> index.html
        if ("/".equals(path)) {
            Path idx = PUBLIC_ROOT.resolve("index.html");
            if (Files.exists(idx)) {
                return serveStatic(req, idx, "index.html");
            }
        }
        // 其他静态文件
        String relativePath = path.substring(1);
        if (relativePath.isEmpty()) relativePath = "index.html";
        Path target = PUBLIC_ROOT.resolve(relativePath).normalize();
        if (Files.exists(target) && Files.isRegularFile(target)) {
            return serveStatic(req, target, target.getFileName().toString());
        }
        return new HttpResponse().status(HttpStatus.NOT_FOUND)
                .bodyText("Not Found", "text/plain; charset=UTF-8");
    }

    private String decodePath(String p) {
        try { return URLDecoder.decode(p, StandardCharsets.UTF_8); } catch (Exception e) { return p; }
    }

    private HttpResponse handleRegister(HttpRequest req) {
        String u = req.form("username");
        String p = req.form("password");
        boolean ok = USER_SERVICE.register(u, p);
        if (ok) {
            return new HttpResponse().status(HttpStatus.OK).bodyText("注册成功", "text/plain; charset=UTF-8");
        }
        return new HttpResponse().status(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyText("注册失败(可能已存在或参数错误)", "text/plain; charset=UTF-8");
    }

    private HttpResponse handleLogin(HttpRequest req) {
        String u = req.form("username");
        String p = req.form("password");
        boolean ok = USER_SERVICE.login(u, p);
        if (ok) {
            String sid = java.util.UUID.randomUUID().toString();
            SESSIONS.put(sid, u);
            return new HttpResponse().status(HttpStatus.OK)
                    .header("Set-Cookie", SESSION_COOKIE + "=" + sid + "; Path=/; HttpOnly")
                    .bodyText("登录成功", "text/plain; charset=UTF-8");
        }
        return new HttpResponse().status(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyText("登录失败(用户名或密码错误)", "text/plain; charset=UTF-8");
    }

    private HttpResponse handleUpload(HttpRequest req) {
        //先检查登录与否
        String user = authenticate(req);
        if (user == null) {
            return new HttpResponse().status(HttpStatus.UNAUTHORIZED)
                    .bodyText("未登录，无法上传", "text/plain; charset=UTF-8");
        }
        String ctype = req.headerFirst("content-type");
        if (ctype == null || !ctype.toLowerCase().startsWith("multipart/form-data")) {
            return new HttpResponse().status(HttpStatus.METHOD_NOT_ALLOWED)
                    .bodyText("需要 multipart/form-data", "text/plain; charset=UTF-8");
        }
        String boundary = null;
        for (String part : ctype.split(";")) {
            part = part.trim();
            if (part.startsWith("boundary=")) {
                boundary = part.substring(9);
                if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
                    boundary = boundary.substring(1, boundary.length()-1);
                }
            }
        }
        if (boundary == null) {
            return new HttpResponse().status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .bodyText("未找到 boundary", "text/plain; charset=UTF-8");
        }
        byte[] body = req.body();
        String delimiter = "--" + boundary;
        String endDelimiter = delimiter + "--";
        // 转为字符串 (仅解析头部与文件名；文件内容按字节截取)
        // 简易实现：按 boundary 分割，再对每个 part 解析
        String all = new String(body, StandardCharsets.ISO_8859_1); // 保留字节
        String[] sections = all.split(delimiter);
        List<String> saved = new ArrayList<>();
        for (String sec : sections) {
            sec = sec.trim();
            if (sec.isEmpty() || sec.equals("--") || sec.equals(endDelimiter)) continue;
            // 头部与内容分割 \r\n\r\n
            int idx = sec.indexOf("\r\n\r\n");
            if (idx < 0) continue;
            String headerPart = sec.substring(0, idx);
            String contentPart = sec.substring(idx + 4); // 之后可能以 -- 结束或 CRLF 结束
            // 去掉末尾多余 CRLF
            if (contentPart.endsWith("\r\n")) {
                contentPart = contentPart.substring(0, contentPart.length()-2);
            }
            // 解析 Content-Disposition
            String[] headerLines = headerPart.split("\r\n");
            String filename = null;
            for (String hl : headerLines) {
                String lower = hl.toLowerCase();
                if (lower.startsWith("content-disposition:")) {
                    // 例: Content-Disposition: form-data; name="file"; filename="a.png"
                    for (String kv : hl.split(";")) {
                        kv = kv.trim();
                        if (kv.startsWith("filename=")) {
                            filename = kv.substring(9).trim();
                            if (filename.startsWith("\"") && filename.endsWith("\"")) {
                                filename = filename.substring(1, filename.length()-1);
                            }
                        }
                    }
                }
            }
            if (filename == null || filename.isEmpty()) {
                continue; // 非文件字段
            }
            // 文件名校验
            if (!filename.matches("[a-zA-Z0-9._-]{1,64}")) {
                return new HttpResponse().status(HttpStatus.METHOD_NOT_ALLOWED)
                        .bodyText("非法文件名", "text/plain; charset=UTF-8");
            }
            // 保存文件到静态资源目录，以便通过 HTTP 直接访问
            try {
                Path target = PUBLIC_ROOT.resolve(filename).normalize();
                if (!target.startsWith(PUBLIC_ROOT)) {
                    return new HttpResponse().status(HttpStatus.METHOD_NOT_ALLOWED)
                            .bodyText("路径不允许", "text/plain; charset=UTF-8");
                }
                byte[] fileBytes = contentPart.getBytes(StandardCharsets.ISO_8859_1); // 原始字节
                Files.write(target, fileBytes);
                saved.add(filename + "(" + fileBytes.length + "B)");
            } catch (IOException e) {
                return new HttpResponse().status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .bodyText("写文件失败: " + e.getMessage(), "text/plain; charset=UTF-8");
            }
        }
        if (saved.isEmpty()) {
            return new HttpResponse().status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .bodyText("未解析到文件", "text/plain; charset=UTF-8");
        }
        // 构建返回的URL，格式为 /local/文件名
        StringBuilder responseText = new StringBuilder("上传成功:\n");
        for (String fileInfo : saved) {
            // 从 fileInfo 中提取文件名（去掉大小信息）
            String filename = fileInfo.split("\\(")[0];
            responseText.append("/local/").append(filename);
        }
        return new HttpResponse().status(HttpStatus.OK)
                .bodyText(responseText.toString(), "text/plain; charset=UTF-8");
    }

    private HttpResponse handleLogout(HttpRequest req) {
        String sid = req.cookie(SESSION_COOKIE);
        if (sid != null) {
            SESSIONS.remove(sid);
        }
        // 设置过期 Cookie 清除客户端
        return new HttpResponse().status(HttpStatus.OK)
                .header("Set-Cookie", SESSION_COOKIE + "=deleted; Path=/; Max-Age=0")
                .bodyText("已退出登录", "text/plain; charset=UTF-8");
    }

    private String authenticate(HttpRequest req) {
        String sid = req.cookie(SESSION_COOKIE);
        if (sid == null) return null;
        return SESSIONS.get(sid);
    }

    private HttpResponse redirect(HttpStatus status, String location) {
        return new HttpResponse().status(status)
                .header("Location", location)
                .bodyText(status.reason(), "text/plain; charset=UTF-8");
    }

    private HttpResponse serveStatic(HttpRequest req, Path file, String name) throws IOException, ParseException {
        // 条件 GET - If-Modified-Since
        String ifModifiedSince = req.headerFirst("if-modified-since");
        long lastModified = Files.getLastModifiedTime(file).toMillis();
        if (ifModifiedSince != null) {
            try {
                Date since = RFC_1123.get().parse(ifModifiedSince);
                // 四舍五入 1 秒
                if (Math.abs(lastModified - since.getTime()) < 1000) {
                    return new HttpResponse().status(HttpStatus.NOT_MODIFIED)
                            .header("Date", formatDate(System.currentTimeMillis()))
                            .header("Last-Modified", formatDate(lastModified))
                            .header("Server", SERVER_NAME)
                            .body(new byte[0]);
                }
            } catch (Exception ignore) {}
        }
        byte[] data = Files.readAllBytes(file);
        String mime = MimeTypes.get(name);
        return new HttpResponse()
                .status(HttpStatus.OK)
                .header("Content-Type", mime)
                .header("Date", formatDate(System.currentTimeMillis()))
                .header("Last-Modified", formatDate(lastModified))
                .header("Server", SERVER_NAME)
                .body(data);
    }

    private String formatDate(long time) {
        return RFC_1123.get().format(new Date(time));
    }
}
