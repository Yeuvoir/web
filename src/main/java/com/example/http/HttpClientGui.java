package com.example.http;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 简单 Swing GUI 客户端壳
 * 界面为AI生成，修改前应确定清楚触发逻辑
 */
public class HttpClientGui {

    // 长连接缓存：host:port -> Socket
    private static final Map<String, Socket> connectionPool = new HashMap<>();

    // 内部类：封装 HTTP 响应数据
    private static class HttpResponseData {
        String headers;
        byte[] bodyBytes;
        String fullText;
        boolean connectionClosed; // 标记连接是否被服务器关闭
        
        HttpResponseData(String headers, byte[] bodyBytes, String fullText) {
            this.headers = headers;
            this.bodyBytes = bodyBytes;
            this.fullText = fullText;
            this.connectionClosed = false;
        }
    }

    

    // 内部类：封装 HTTP 请求和响应对
    private static class HttpRequestResponsePair {
        String request;
        String response;
        
        HttpRequestResponsePair(String request, String response) {
            this.request = request;
            this.response = response;
        }
    }

    /**
     * 设置带颜色的文本
     */
    private static void setColoredText(JTextPane textPane, String text, Color color) {
        textPane.setText(text);
        StyledDocument doc = textPane.getStyledDocument();
        Style style = textPane.addStyle("ColoredStyle", null);
        StyleConstants.setForeground(style, color);
        doc.setCharacterAttributes(0, doc.getLength(), style, false);
    }

    public static void launch() {
        SwingUtilities.invokeLater(HttpClientGui::createAndShow);
    }
    
    public static void launch(int defaultPort) {
        SwingUtilities.invokeLater(() -> createAndShow(defaultPort));
    }

    private static void createAndShow() {
        createAndShow(8080);
    }
    
    private static void createAndShow(int defaultPort) {
        JFrame frame = new JFrame("Simple HTTP Client (Socket) - Request/Response Display");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 800);

    JTextField urlField = new JTextField("http://localhost:" + defaultPort + "/");
        JComboBox<String> methodBox = new JComboBox<>(new String[]{"GET", "POST"});
        JTextArea requestArea = new JTextArea(8, 40);
        JTextArea responseArea = new JTextArea();
        responseArea.setEditable(false);
        JButton sendBtn = new JButton("发送");
        
        // 创建带颜色的文本区域用于显示请求和响应
        JTextPane requestDisplayArea = new JTextPane();
        requestDisplayArea.setEditable(false);
        requestDisplayArea.setBackground(new Color(240, 248, 255)); // 淡蓝色背景
        JScrollPane requestScrollPane = new JScrollPane(requestDisplayArea);
        requestScrollPane.setBackground(new Color(230, 240, 250)); // 更深的蓝色背景
        requestScrollPane.getViewport().setBackground(new Color(240, 248, 255)); // 视口背景色
        
        JTextPane responseDisplayArea = new JTextPane();
        responseDisplayArea.setEditable(false);
        responseDisplayArea.setBackground(new Color(255, 240, 240)); // 淡红色背景
        JScrollPane responseScrollPane = new JScrollPane(responseDisplayArea);
        responseScrollPane.setBackground(new Color(250, 230, 230)); // 更深的红色背景
        responseScrollPane.getViewport().setBackground(new Color(255, 240, 240)); // 视口背景色
    // 上传组件
    JTextField uploadField = new JTextField(18);
    uploadField.setEditable(false);
    JButton chooseBtn = new JButton("选择文件");
    JButton uploadBtn = new JButton("上传");
    JLabel uploadStatus = new JLabel("未选择");

    // 认证区域组件
    JTextField userField = new JTextField(8);
    JPasswordField passField = new JPasswordField(8);
    JButton regBtn = new JButton("注册");
    JButton loginBtn = new JButton("登录");
    JButton logoutBtn = new JButton("注销");
    JLabel sessionLabel = new JLabel("未登录");

        // 简易缓存：url -> Last-Modified
    Map<String,String> cacheLastModified = new HashMap<>();
    Map<String,String> cookieStore = new HashMap<>(); // 保存服务端返回Cookie

        sendBtn.addActionListener((ActionEvent e) -> {
            String method = (String) methodBox.getSelectedItem();
            String url = urlField.getText().trim();
            String body = requestArea.getText();
            final String currentUser = userField.getText().trim(); // 获取当前用户名
            sendBtn.setEnabled(false);
            requestDisplayArea.setText("请求中...");
            responseDisplayArea.setText("等待响应...");
            new SwingWorker<HttpRequestResponsePair,Void>() {
                @Override protected HttpRequestResponsePair doInBackground() {
                    try { 
                        // 验证URL格式
                        if (url == null || url.trim().isEmpty()) {
                            throw new IllegalArgumentException("URL不能为空");
                        }
                        
                        // 构建请求文本
                        URI uri;
                        try {
                            uri = URI.create(url.trim());
                        } catch (Exception e) {
                            throw new IllegalArgumentException("URL格式无效: " + e.getMessage());
                        }
                        
                        // 验证必要的URL组件
                        if (uri.getHost() == null) {
                            throw new IllegalArgumentException("URL中缺少主机名");
                        }
                        String host = uri.getHost();
                        int port = uri.getPort() == -1 ? 80 : uri.getPort();
                        String path = uri.getRawPath();
                        if (path == null || path.isEmpty()) path = "/";
                        if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();

                        StringBuilder reqText = new StringBuilder();
                        reqText.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
                        reqText.append("Host: ").append(host).append("\r\n");
                        reqText.append("User-Agent: SimpleSocketClient/1.0\r\n");
                        reqText.append("Accept: */*\r\n");
                        
                        // 缓存支持
                        String last = cacheLastModified.get(url);
                        if (last != null) {
                            reqText.append("If-Modified-Since: ").append(last).append("\r\n");
                        }
                        
                        byte[] bodyBytes = new byte[0];
                        if ("POST".equalsIgnoreCase(method) && body != null && !body.isEmpty()) {
                            bodyBytes = body.getBytes(StandardCharsets.UTF_8);
                            reqText.append("Content-Type: application/x-www-form-urlencoded\r\n");
                            reqText.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
                        }
                        
                        // Cookie
                        if (!cookieStore.isEmpty()) {
                            StringBuilder cb = new StringBuilder();
                            cookieStore.forEach((k,v)-> {
                                if (cb.length()>0) cb.append("; ");
                                cb.append(k).append("=").append(v);
                            });
                            reqText.append("Cookie: ").append(cb).append("\r\n");
                        }
                        reqText.append("Connection: keep-alive\r\n\r\n");
                        
                        String requestText = reqText.toString();
                        if (bodyBytes.length > 0) {
                            requestText += new String(bodyBytes, StandardCharsets.UTF_8);
                        }
                        
                        // 发送请求并获取响应
                        HttpResponseData respData = sendHttpWithBytes(method, url, body, cacheLastModified, cookieStore, 0);
                        
                        // GET 请求成功且状态码 200 时，保存响应体到本地
                        if ("GET".equalsIgnoreCase(method) && respData.fullText.contains(" 200 ")) {
                            saveResponseToLocal(url, respData.headers, respData.bodyBytes);
                        }
                        
                        return new HttpRequestResponsePair(requestText, respData.fullText);
                    }
                    catch (Exception ex){ 
                        System.err.println("=== 请求构建失败详情 ===");
                        System.err.println("方法: " + method);
                        System.err.println("URL: " + url);
                        System.err.println("异常类型: " + ex.getClass().getSimpleName());
                        System.err.println("异常消息: " + ex.getMessage());
                        ex.printStackTrace();
                        System.err.println("========================");
                        return new HttpRequestResponsePair("请求构建失败: " + ex.getMessage(), "请求失败: " + ex.getMessage()); 
                    }
                }
                @Override protected void done() {
                    try { 
                        HttpRequestResponsePair pair = get();
                        setColoredText(requestDisplayArea, pair.request, new Color(0, 0, 139)); // 深蓝色文字
                        setColoredText(responseDisplayArea, pair.response, new Color(139, 0, 0)); // 深红色文字);
                        
                        // 检查是否是POST登录请求并更新状态
                        if ("POST".equalsIgnoreCase(method) && url.contains("/login") && pair.response.contains("登录成功")) {
                            sessionLabel.setText("已登录:" + currentUser);
                        }
                    } catch (Exception ex) { 
                        setColoredText(requestDisplayArea, "请求异常: " + ex.getMessage(), Color.RED);
                        setColoredText(responseDisplayArea, "响应异常: " + ex.getMessage(), Color.RED);
                    }
                    sendBtn.setEnabled(true);
                }
            }.execute();
        });

    regBtn.addActionListener(e -> asyncAuth(true, urlField.getText().trim(), userField.getText().trim(), new String(passField.getPassword()), requestDisplayArea, responseDisplayArea, cookieStore, sessionLabel, regBtn, loginBtn, logoutBtn));
    loginBtn.addActionListener(e -> asyncAuth(false, urlField.getText().trim(), userField.getText().trim(), new String(passField.getPassword()), requestDisplayArea, responseDisplayArea, cookieStore, sessionLabel, regBtn, loginBtn, logoutBtn));
    logoutBtn.addActionListener(e -> doLogout(urlField.getText().trim(), requestDisplayArea, responseDisplayArea, cookieStore, sessionLabel, logoutBtn));
    chooseBtn.addActionListener(e -> {
        JFileChooser fc = new JFileChooser();
        int r = fc.showOpenDialog(frame);
        if (r == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            uploadField.setText(f.getAbsolutePath());
            uploadStatus.setText(f.getName() + " (" + f.length() + "B)");
        }
    });
    uploadBtn.addActionListener(e -> doUpload(urlField.getText().trim(), uploadField.getText().trim(), cookieStore, requestDisplayArea, responseDisplayArea, uploadStatus));

        // 顶部控制面板
        JPanel top = new JPanel(new BorderLayout(5,5));
    JPanel north = new JPanel(new BorderLayout(5,5));
    JPanel row1Left = new JPanel(new FlowLayout(FlowLayout.LEFT,5,0));
    row1Left.add(new JLabel("Method"));
    row1Left.add(methodBox);
    row1Left.add(new JLabel("URL"));
    row1Left.add(urlField);
    north.add(row1Left, BorderLayout.CENTER);
    JPanel row1Right = new JPanel(new FlowLayout(FlowLayout.RIGHT,5,0));
    row1Right.add(sendBtn);
    north.add(row1Right, BorderLayout.EAST);
    JPanel authPanel = new JPanel(new FlowLayout(FlowLayout.LEFT,5,0));
    authPanel.add(new JLabel("用户"));
    authPanel.add(userField);
    authPanel.add(new JLabel("密码"));
    authPanel.add(passField);
    authPanel.add(regBtn);
    authPanel.add(loginBtn);
    authPanel.add(logoutBtn);
    authPanel.add(sessionLabel);
    north.add(authPanel, BorderLayout.SOUTH);
        top.add(north, BorderLayout.NORTH);
        
    // 请求体输入面板
    JPanel requestInputPanel = new JPanel(new BorderLayout(5,5));
    requestInputPanel.setBorder(BorderFactory.createTitledBorder("请求体 (POST数据)"));
        requestInputPanel.add(new JScrollPane(requestArea), BorderLayout.CENTER);
        
    // 上传面板
    JPanel uploadPanel = new JPanel(new FlowLayout(FlowLayout.LEFT,5,0));
    uploadPanel.add(new JLabel("上传"));
    uploadPanel.add(uploadField);
    uploadPanel.add(chooseBtn);
    uploadPanel.add(uploadBtn);
    uploadPanel.add(uploadStatus);
    requestInputPanel.add(uploadPanel, BorderLayout.SOUTH);
    
    top.add(requestInputPanel, BorderLayout.CENTER);

        // 创建请求和响应显示区域
        JPanel requestDisplayPanel = new JPanel(new BorderLayout(5,5));
        requestDisplayPanel.setBorder(BorderFactory.createTitledBorder("HTTP请求报文"));
        requestDisplayPanel.setBackground(new Color(230, 240, 250)); // 更深的蓝色背景
        requestDisplayPanel.add(requestScrollPane, BorderLayout.CENTER);
        
        JPanel responseDisplayPanel = new JPanel(new BorderLayout(5,5));
        responseDisplayPanel.setBorder(BorderFactory.createTitledBorder("HTTP响应报文"));
        responseDisplayPanel.setBackground(new Color(250, 230, 230)); // 更深的红色背景
        responseDisplayPanel.add(responseScrollPane, BorderLayout.CENTER);
        
        // 创建水平分割面板来显示请求和响应
        JSplitPane horizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, requestDisplayPanel, responseDisplayPanel);
        horizontalSplit.setDividerLocation(450); // 设置分割位置
        
        // 创建垂直分割面板，顶部是控制区，底部是请求响应显示区
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, horizontalSplit);
        mainSplit.setDividerLocation(300);

        frame.setLayout(new BorderLayout());
        frame.add(mainSplit, BorderLayout.CENTER);
        frame.setVisible(true);
    }

    /**
     * 发送 HTTP 请求，处理最多5次重定向。
     */
    private static String sendHttp(String method, String url, String body, Map<String,String> cache, Map<String,String> cookies, int redirectCount) throws Exception {
        if (redirectCount > 5) {
            return "超过最大重定向次数";
        }
        URI uri = URI.create(url);
        String host = uri.getHost();
        int port = uri.getPort() == -1 ? 80 : uri.getPort();
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();

    StringBuilder req = new StringBuilder();
        req.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
        req.append("Host: ").append(host).append("\r\n");
        req.append("User-Agent: SimpleSocketClient/1.0\r\n");
        req.append("Accept: */*\r\n");
        // 缓存支持 If-Modified-Since
        String last = cache.get(url);
        if (last != null) {
            req.append("If-Modified-Since: ").append(last).append("\r\n");
        }
        byte[] bodyBytes = new byte[0];
        if ("POST".equalsIgnoreCase(method) && body != null && !body.isEmpty()) {
            bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            req.append("Content-Type: application/x-www-form-urlencoded\r\n");
            req.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
        }
        // 附带 Cookie
        if (!cookies.isEmpty()) {
            StringBuilder cb = new StringBuilder();
            cookies.forEach((k,v)-> {
                if (cb.length()>0) cb.append("; ");
                cb.append(k).append("=").append(v);
            });
            req.append("Cookie: ").append(cb).append("\r\n");
        }
        req.append("Connection: keep-alive\r\n\r\n");

        try (Socket socket = new Socket()) {
                System.out.println("[客户端] 创建新 Socket 连接 -> " + host + ":" + port);
                socket.connect(new java.net.InetSocketAddress(host, port), 4000); // 连接超时 4s
                socket.setSoTimeout(5000); // 读超时 5s
            OutputStream out = socket.getOutputStream();
            out.write(req.toString().getBytes(StandardCharsets.US_ASCII));
            if (bodyBytes.length > 0) out.write(bodyBytes);
            out.flush();
            InputStream in = socket.getInputStream();
            // 读取头部
            ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
            int state = 0; // 连续 \r\n\r\n 判断
            while (true) {
                int b = in.read();
                if (b == -1) break;
                headerBuf.write(b);
                // 状态机检测头结束
                if (state == 0 && b == '\r') state = 1; else if (state == 1 && b == '\n') state = 2;
                else if (state == 2 && b == '\r') state = 3; else if (state == 3 && b == '\n') break; else state = 0;
                if (headerBuf.size() > 32_768) throw new IOException("Header too large");
            }
            String head = headerBuf.toString(StandardCharsets.US_ASCII);
            String[] lines = head.split("\r\n");
            // 解析 Content-Length
            int contentLength = -1;
            for (String l : lines) {
                if (l.toLowerCase().startsWith("content-length:")) {
                    try { contentLength = Integer.parseInt(l.substring(15).trim()); } catch (NumberFormatException ignore) {}
                }
            }
            byte[] bodyResp = new byte[0];
            if (contentLength > 0) {
                bodyResp = in.readNBytes(contentLength);
            } else {
                // 无 Content-Length：尝试读到超时或关闭（短小响应）
                socket.setSoTimeout(800); // 缩短
                ByteArrayOutputStream rest = new ByteArrayOutputStream();
                try {
                    byte[] tmp = new byte[1024];
                    int r;
                    while ((r = in.read(tmp)) != -1) {
                        rest.write(tmp,0,r);
                        if (rest.size() > 1024*1024) break; // 上限1MB
                    }
                } catch (IOException timeout) {
                    // ignore read timeout
                }
                bodyResp = rest.toByteArray();
            }
            String raw = head + new String(bodyResp, StandardCharsets.UTF_8);
            // 解析状态行
            if (lines.length > 0) {
                String statusLine = lines[0];
                if (statusLine.contains(" 301 ") || statusLine.contains(" 302 ")) {
                    // 找 Location
                    String loc = null;
                    for (String l : lines) {
                        if (l.toLowerCase().startsWith("location:")) {
                            loc = l.substring(9).trim();
                            break;
                        }
                    }
                    if (loc != null) {
                        if (!loc.startsWith("http")) {
                            // 相对 -> 拼
                            String prefix = uri.getScheme() + "://" + host + (uri.getPort()==-1 ? "" : (":" + uri.getPort()));
                            loc = prefix + loc;
                        }
                        return raw + "\n\n-- 自动跟随 -> " + loc + " --\n" +
                                sendHttp(method, loc, body, cache, cookies, redirectCount + 1);
                    }
                } else if (statusLine.contains(" 304 ")) {
                    return raw + "\n(使用缓存的 Last-Modified: " + last + ")";
                }
            }
            // 抽取 Last-Modified 更新缓存
            for (String l : lines) {
                if (l.toLowerCase().startsWith("last-modified:")) {
                    cache.put(url, l.substring(14).trim());
                }
            }
            // 解析 Set-Cookie
            for (String l : lines) {
                if (l.toLowerCase().startsWith("set-cookie:")) {
                    String val = l.substring(11).trim();
                    String pair = val.split(";",2)[0];
                    int eq = pair.indexOf('=');
                    if (eq>0) {
                        String ck = pair.substring(0,eq).trim();
                        String cv = pair.substring(eq+1).trim();
                        cookies.put(ck, cv);
                    }
                }
            }
            return raw;
        }
    }

    /**
     * 改进版 sendHttp：返回分离的头部和字节体，用于正确保存二进制文件
     */
    private static HttpResponseData sendHttpWithBytes(String method, String url, String body, Map<String,String> cache, Map<String,String> cookies, int redirectCount) throws Exception {
        if (redirectCount > 5) {
            String msg = "超过最大重定向次数";
            return new HttpResponseData(msg, new byte[0], msg);
        }
        URI uri = URI.create(url);
        String host = uri.getHost();
        int port = uri.getPort() == -1 ? 80 : uri.getPort();
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) path = "/";
        if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();

        StringBuilder req = new StringBuilder();
        req.append(method).append(' ').append(path).append(" HTTP/1.1\r\n");
        req.append("Host: ").append(host).append("\r\n");
        req.append("User-Agent: SimpleSocketClient/1.0\r\n");
        req.append("Accept: */*\r\n");
        String last = cache.get(url);
        if (last != null) {
            req.append("If-Modified-Since: ").append(last).append("\r\n");
        }
        byte[] bodyBytes = new byte[0];
        if ("POST".equalsIgnoreCase(method) && body != null && !body.isEmpty()) {
            bodyBytes = body.getBytes(StandardCharsets.UTF_8);
            req.append("Content-Type: application/x-www-form-urlencoded\r\n");
            req.append("Content-Length: ").append(bodyBytes.length).append("\r\n");
        }
        if (!cookies.isEmpty()) {
            StringBuilder cb = new StringBuilder();
            cookies.forEach((k,v)-> {
                if (cb.length()>0) cb.append("; ");
                cb.append(k).append("=").append(v);
            });
            req.append("Cookie: ").append(cb).append("\r\n");
        }
        req.append("Connection: keep-alive\r\n\r\n");

        String connKey = host + ":" + port;
        Socket socket = connectionPool.get(connKey);
        
        // 更严格的连接检查
        boolean connectionValid = false;
        if (socket != null) {
            try {
                // 检查连接的基本状态
                if (!socket.isClosed() && socket.isConnected() && !socket.isInputShutdown() && !socket.isOutputShutdown()) {
                    // 尝试发送一个测试字节来验证连接是否真的可用
                    socket.getOutputStream().flush();
                    connectionValid = true;
                    System.out.println("[客户端]  复用现有连接 -> " + connKey);
                } else {
                    System.out.println("[客户端] 连接状态异常，移除: " + connKey);
                    try {
                        socket.close();
                    } catch (IOException ignored) {}
                    connectionPool.remove(connKey);
                    socket = null;
                }
            } catch (IOException e) {
                System.out.println("[客户端] 连接测试失败，移除: " + connKey + " - " + e.getMessage());
                try {
                    socket.close();
                } catch (IOException ignored) {}
                connectionPool.remove(connKey);
                socket = null;
            }
        }
        
        // 如果没有可用连接，创建新连接
        if (socket == null || !connectionValid) {
            socket = new Socket();
            System.out.println("[客户端]  创建新 Socket 连接 -> " + connKey);
            socket.connect(new java.net.InetSocketAddress(host, port), 4000);
            socket.setSoTimeout(5000);
            connectionPool.put(connKey, socket);
        }
        
        try {
            OutputStream out = socket.getOutputStream();
            out.write(req.toString().getBytes(StandardCharsets.US_ASCII));
            if (bodyBytes.length > 0) out.write(bodyBytes);
            out.flush();
            InputStream in = socket.getInputStream();
            
            // 读取头部
            ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
            int state = 0;
            while (true) {
                int b = in.read();
                if (b == -1) break;
                headerBuf.write(b);
                if (state == 0 && b == '\r') state = 1; else if (state == 1 && b == '\n') state = 2;
                else if (state == 2 && b == '\r') state = 3; else if (state == 3 && b == '\n') break; else state = 0;
                if (headerBuf.size() > 32_768) throw new IOException("Header too large");
            }
            String head = headerBuf.toString(StandardCharsets.US_ASCII);
            String[] lines = head.split("\r\n");
            
            // 解析 Content-Length
            int contentLength = -1;
            for (String l : lines) {
                if (l.toLowerCase().startsWith("content-length:")) {
                    try { contentLength = Integer.parseInt(l.substring(15).trim()); } catch (NumberFormatException ignore) {}
                }
            }
            
            byte[] bodyResp = new byte[0];
            if (contentLength > 0) {
                bodyResp = in.readNBytes(contentLength);
            } else {
                socket.setSoTimeout(800);
                ByteArrayOutputStream rest = new ByteArrayOutputStream();
                try {
                    byte[] tmp = new byte[1024];
                    int r;
                    while ((r = in.read(tmp)) != -1) {
                        rest.write(tmp,0,r);
                        if (rest.size() > 1024*1024) break;
                    }
                } catch (IOException timeout) {}
                bodyResp = rest.toByteArray();
            }
            
            String raw = head + new String(bodyResp, StandardCharsets.UTF_8);
            
            // 处理重定向
            if (lines.length > 0) {
                String statusLine = lines[0];
                if (statusLine.contains(" 301 ") || statusLine.contains(" 302 ")) {
                    String loc = null;
                    for (String l : lines) {
                        if (l.toLowerCase().startsWith("location:")) {
                            loc = l.substring(9).trim();
                            break;
                        }
                    }
                    if (loc != null) {
                        if (!loc.startsWith("http")) {
                            String prefix = uri.getScheme() + "://" + host + (uri.getPort()==-1 ? "" : (":" + uri.getPort()));
                            loc = prefix + loc;
                        }
                        HttpResponseData nextResp = sendHttpWithBytes(method, loc, body, cache, cookies, redirectCount + 1);
                        String combined = raw + "\n\n-- 自动跟随 -> " + loc + " --\n" + nextResp.fullText;
                        return new HttpResponseData(nextResp.headers, nextResp.bodyBytes, combined);
                    }
                } else if (statusLine.contains(" 304 ")) {
                    String fullMsg = raw + "\n(使用缓存的 Last-Modified: " + last + ")";
                    return new HttpResponseData(head, bodyResp, fullMsg);
                }
            }
            
            // 更新缓存
            for (String l : lines) {
                if (l.toLowerCase().startsWith("last-modified:")) {
                    cache.put(url, l.substring(14).trim());
                }
            }
            
            // 解析 Cookie
            for (String l : lines) {
                if (l.toLowerCase().startsWith("set-cookie:")) {
                    String val = l.substring(11).trim();
                    String pair = val.split(";",2)[0];
                    int eq = pair.indexOf('=');
                    if (eq>0) {
                        String ck = pair.substring(0,eq).trim();
                        String cv = pair.substring(eq+1).trim();
                        cookies.put(ck, cv);
                    }
                }
            }
            
            // 检查服务器是否要求关闭连接
            boolean shouldClose = false;
            for (String l : lines) {
                if (l.toLowerCase().startsWith("connection:")) {
                    String val = l.substring(11).trim().toLowerCase();
                    if (val.equals("close")) {
                        shouldClose = true;
                        System.out.println("[客户端] 服务器要求关闭连接: " + connKey);
                        break;
                    }
                }
            }
            
            // 如果服务器要求关闭，则关闭并移除连接
            if (shouldClose) {
                connectionPool.remove(connKey);
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
            
            HttpResponseData result = new HttpResponseData(head, bodyResp, raw);
            result.connectionClosed = shouldClose;
            return result;
        } catch (IOException e) {
            // 连接出错，移除并关闭
            System.out.println("[客户端] 连接异常，移除: " + connKey + " - " + e.getMessage());
            connectionPool.remove(connKey);
            try {
                if (socket != null) socket.close();
            } catch (IOException ignored) {}
            throw e;
        }
    }

    private static void asyncAuth(boolean register, String baseUrl, String user, String pass,
                                JTextPane requestDisplayArea, JTextPane responseDisplayArea, Map<String,String> cookies, JLabel sessionLabel,
                                JButton regBtn, JButton loginBtn, JButton logoutBtn) {
        if (user.isEmpty() || pass.isEmpty()) {
            setColoredText(responseDisplayArea, "用户名或密码不能为空", Color.RED);
            return;
        }
        
        // 构建正确的URL：确保使用默认的/register和/login路由
        String url;
        try {
            URI uri = URI.create(baseUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getPath();
            
            // 如果baseUrl包含路径，只取scheme、host、port部分
            String base = scheme + "://" + host;
            if (port != -1 && port != 80) {
                base += ":" + port;
            }
            
            // 确保以/结尾
            if (!base.endsWith("/")) {
                base += "/";
            }
            
            String endpoint = register ? "register" : "login";
            url = base + endpoint;
        } catch (Exception e) {
            // 如果URL解析失败，使用简单拼接
            if (!baseUrl.endsWith("/")) baseUrl += "/";
            String endpoint = register ? "register" : "login";
            url = baseUrl + endpoint;
        }
        final String finalUrl = url;
        
        String body = "username=" + encode(user) + "&password=" + encode(pass);
    regBtn.setEnabled(false); loginBtn.setEnabled(false); logoutBtn.setEnabled(false);
        setColoredText(requestDisplayArea, (register?"注册":"登录") + "中...", Color.BLUE);
        setColoredText(responseDisplayArea, "等待响应...", new Color(139, 0, 0));
        final String fUser = user;
        new SwingWorker<HttpRequestResponsePair,Void>() {
            @Override protected HttpRequestResponsePair doInBackground() {
                try { 
                    // 构建请求文本
                    URI uri = URI.create(finalUrl);
                    String host = uri.getHost();
                    int port = uri.getPort() == -1 ? 80 : uri.getPort();
                    String path = uri.getRawPath();
                    if (path == null || path.isEmpty()) path = "/";
                    if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();

                    StringBuilder reqText = new StringBuilder();
                    reqText.append("POST ").append(path).append(" HTTP/1.1\r\n");
                    reqText.append("Host: ").append(host).append("\r\n");
                    reqText.append("User-Agent: SimpleSocketClient/1.0\r\n");
                    reqText.append("Accept: */*\r\n");
                    reqText.append("Content-Type: application/x-www-form-urlencoded\r\n");
                    reqText.append("Content-Length: ").append(body.getBytes(StandardCharsets.UTF_8).length).append("\r\n");
                    
                    // 附带 Cookie
                    if (!cookies.isEmpty()) {
                        StringBuilder cb = new StringBuilder();
                        cookies.forEach((k,v)-> {
                            if (cb.length()>0) cb.append("; ");
                            cb.append(k).append("=").append(v);
                        });
                        reqText.append("Cookie: ").append(cb).append("\r\n");
                    }
                    reqText.append("Connection: keep-alive\r\n\r\n");
                    reqText.append(body);
                    
                    String requestText = reqText.toString();
                    String responseText = sendHttp("POST", finalUrl, body, new HashMap<>(), cookies, 0);
                    
                    return new HttpRequestResponsePair(requestText, responseText);
                }
                catch (Exception ex){ 
                    String errorMsg = (register?"注册":"登录") + "失败: " + ex.getMessage() + " (检查服务器是否启动 / 端口 / 防火墙)";
                    return new HttpRequestResponsePair("请求构建失败: " + ex.getMessage(), errorMsg); 
                }
            }
            @Override protected void done() {
                try {
                    HttpRequestResponsePair pair = get();
                    setColoredText(requestDisplayArea, pair.request, new Color(0, 0, 139)); // 深蓝色文字
                    setColoredText(responseDisplayArea, pair.response, new Color(139, 0, 0)); // 深红色文字
                    
                    if (!register && pair.response.contains("登录成功")) {
                        sessionLabel.setText("已登录:" + fUser);
                    }
                } catch (Exception ex) {
                    setColoredText(requestDisplayArea, "请求异常: " + ex.getMessage(), Color.RED);
                    setColoredText(responseDisplayArea, "响应异常: " + ex.getMessage(), Color.RED);
                } finally {
                    regBtn.setEnabled(true); loginBtn.setEnabled(true); logoutBtn.setEnabled(true);
                }
            }
        }.execute();
    }

    private static void doLogout(String baseUrl, JTextPane requestDisplayArea, JTextPane responseDisplayArea, Map<String,String> cookies, JLabel sessionLabel, JButton logoutBtn) {
        if (!baseUrl.endsWith("/")) baseUrl += "/";
        String url = baseUrl + "logout";
        final String finalUrl = url;
        logoutBtn.setEnabled(false);
        setColoredText(requestDisplayArea, "注销中...", Color.BLUE);
        setColoredText(responseDisplayArea, "等待响应...", new Color(139, 0, 0));
        new SwingWorker<HttpRequestResponsePair,Void>() {
            @Override protected HttpRequestResponsePair doInBackground() {
                try { 
                    // 构建请求文本
                    URI uri = URI.create(finalUrl);
                    String host = uri.getHost();
                    int port = uri.getPort() == -1 ? 80 : uri.getPort();
                    String path = uri.getRawPath();
                    if (path == null || path.isEmpty()) path = "/";
                    if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();

                    StringBuilder reqText = new StringBuilder();
                    reqText.append("POST ").append(path).append(" HTTP/1.1\r\n");
                    reqText.append("Host: ").append(host).append("\r\n");
                    reqText.append("User-Agent: SimpleSocketClient/1.0\r\n");
                    reqText.append("Accept: */*\r\n");
                    
                    // 附带 Cookie
                    if (!cookies.isEmpty()) {
                        StringBuilder cb = new StringBuilder();
                        cookies.forEach((k,v)-> {
                            if (cb.length()>0) cb.append("; ");
                            cb.append(k).append("=").append(v);
                        });
                        reqText.append("Cookie: ").append(cb).append("\r\n");
                    }
                    reqText.append("Content-Length: 0\r\n");
                    reqText.append("Connection: keep-alive\r\n\r\n");
                    
                    String requestText = reqText.toString();
                    String responseText = sendHttp("POST", finalUrl, "", new HashMap<>(), cookies, 0);
                    
                    return new HttpRequestResponsePair(requestText, responseText);
                }
                catch (Exception ex){ 
                    return new HttpRequestResponsePair("请求构建失败: " + ex.getMessage(), "注销失败: " + ex.getMessage()); 
                }
            }
            @Override protected void done() {
                try {
                    HttpRequestResponsePair pair = get();
                    setColoredText(requestDisplayArea, pair.request, new Color(0, 0, 139)); // 深蓝色文字
                    setColoredText(responseDisplayArea, pair.response, new Color(139, 0, 0)); // 深红色文字
                    
                    // 清除本地会话 Cookie
                    cookies.remove("SID");
                    sessionLabel.setText("未登录");
                } catch (Exception ex) {
                    setColoredText(requestDisplayArea, "请求异常: " + ex.getMessage(), Color.RED);
                    setColoredText(responseDisplayArea, "响应异常: " + ex.getMessage(), Color.RED);
                } finally {
                    logoutBtn.setEnabled(true);
                }
            }
        }.execute();
    }

    private static String encode(String s) {
        try { return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8); } catch (Exception e) { return s; }
    }


    private static void doUpload(String baseUrl, String filePath, Map<String,String> cookies,
                                JTextPane requestDisplayArea, JTextPane responseDisplayArea, JLabel uploadStatus) {
        if (filePath == null || filePath.isEmpty()) {
            setColoredText(responseDisplayArea, "请先选择文件", Color.RED);
            return;
        }
        File f = new File(filePath);
        if (!f.exists() || !f.isFile()) {
            setColoredText(responseDisplayArea, "文件不存在", Color.RED);
            return;
        }
        long max = 5L * 1024 * 1024; // 5MB 限制
        if (f.length() > max) {
            setColoredText(responseDisplayArea, "文件过大，最大5MB", Color.RED);
            return;
        }
        
        // 构建正确的URL：确保使用默认的/upload路由
        String url;
        try {
            URI uri = URI.create(baseUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getPath();
            
            // 如果baseUrl包含路径，只取scheme、host、port部分
            String base = scheme + "://" + host;
            if (port != -1 && port != 80) {
                base += ":" + port;
            }
            
            // 确保以/结尾
            if (!base.endsWith("/")) {
                base += "/";
            }
            
            url = base + "upload";
        } catch (Exception e) {
            // 如果URL解析失败，使用简单拼接
            if (!baseUrl.endsWith("/")) baseUrl += "/";
            url = baseUrl + "upload";
        }
        uploadStatus.setText("上传中...");
        setColoredText(requestDisplayArea, "上传中...", Color.BLUE);
        setColoredText(responseDisplayArea, "等待响应...", new Color(139, 0, 0));
        final String finalUrl = url;
        new SwingWorker<HttpRequestResponsePair,Void>() {
            @Override protected HttpRequestResponsePair doInBackground() {
                try { 
                    // 构建multipart请求文本用于显示
                    URI uri = URI.create(finalUrl);
                    String host = uri.getHost();
                    int port = uri.getPort() == -1 ? 80 : uri.getPort();
                    String path = uri.getRawPath();
                    if (path == null || path.isEmpty()) path = "/";
                    path += "/"+f.getName();
                    String boundary = "----WSOCK" + System.currentTimeMillis();
                    String fileName = f.getName();
                    String mime = guessMime(fileName);
                    
                    StringBuilder reqText = new StringBuilder();
                    reqText.append("POST ").append(path).append(" HTTP/1.1\r\n");
                    reqText.append("Host: ").append(host).append("\r\n");
                    reqText.append("User-Agent: SimpleSocketClient/1.0\r\n");
                    reqText.append("Accept: */*\r\n");
                    reqText.append("Content-Type: multipart/form-data; boundary=").append(boundary).append("\r\n");
                    
                    // 计算实际的请求体大小
                    ByteArrayOutputStream bodyCalc = new ByteArrayOutputStream();
                    bodyCalc.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.US_ASCII));
                    bodyCalc.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
                    bodyCalc.write(("Content-Type: " + mime + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                    byte[] fileBytes = readAllBytes(f);
                    bodyCalc.write(fileBytes);
                    bodyCalc.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                    bodyCalc.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
                    
                    reqText.append("Content-Length: ").append(bodyCalc.size()).append("\r\n");
                    
                    // 附带 Cookie
                    if (!cookies.isEmpty()) {
                        StringBuilder cb = new StringBuilder();
                        cookies.forEach((k,v)-> {
                            if (cb.length()>0) cb.append("; ");
                            cb.append(k).append("=").append(v);
                        });
                        reqText.append("Cookie: ").append(cb).append("\r\n");
                    }
                    reqText.append("Connection: keep-alive\r\n\r\n");
                    
                    // 构建完整的请求头部（不包含请求体）
                    String requestHeaders = reqText.toString();
                    
                    // 添加请求体预览，不展示实际文件内容
                    String requestPreview = requestHeaders + 
                        "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n" +
                        "Content-Type: " + mime + "\r\n\r\n" +
                        "[文件内容: " + fileBytes.length + " bytes]\r\n" +
                        "--" + boundary + "--\r\n";
                    
                    String responseText = sendMultipart(finalUrl, f, cookies, requestHeaders, bodyCalc.toByteArray());
                    return new HttpRequestResponsePair(requestPreview, responseText);
                }
                catch (Exception ex){ 
                    return new HttpRequestResponsePair("上传请求构建失败: " + ex.getMessage(), "上传失败: " + ex.getMessage()); 
                }
            }
            @Override protected void done() {
                try {
                    HttpRequestResponsePair pair = get();
                    setColoredText(requestDisplayArea, pair.request, new Color(0, 0, 139)); // 深蓝色文字
                    setColoredText(responseDisplayArea, pair.response, new Color(139, 0, 0)); // 深红色文字
                    
                    if (pair.response.contains("200 OK") && pair.response.contains("上传成功")) {
                        uploadStatus.setText("完成:" + f.getName());
                    } else {
                        uploadStatus.setText("失败");
                    }
                } catch (Exception e) {
                    setColoredText(requestDisplayArea, "上传请求异常: " + e.getMessage(), Color.RED);
                    setColoredText(responseDisplayArea, "上传响应异常: " + e.getMessage(), Color.RED);
                    uploadStatus.setText("异常");
                }
            }
        }.execute();
    }

    private static String guessMime(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    private static String sendMultipart(String url, File file, Map<String,String> cookies, String request,byte[] bodyBytes) throws Exception {
        URI uri = URI.create(url);
        String host = uri.getHost();
        int port = uri.getPort() == -1 ? 80 : uri.getPort();

        String connKey = host + ":" + port;
        Socket socket = connectionPool.get(connKey);
        
        // 严格检查并测试连接
        boolean connectionValid = false;
        if (socket != null) {
            try {
                // 检查连接的基本状态
                if (!socket.isClosed() && socket.isConnected() && !socket.isInputShutdown() && !socket.isOutputShutdown()) {
                    // 尝试发送一个测试字节来验证连接是否真的可用
                    socket.getOutputStream().flush();
                    connectionValid = true;
                    System.out.println("[客户端]  复用现有连接 -> " + connKey);
                } else {
                    System.out.println("[客户端] 连接状态异常，移除: " + connKey);
                    try {
                        socket.close();
                    } catch (IOException ignored) {}
                    connectionPool.remove(connKey);
                    socket = null;
                }
            } catch (IOException e) {
                System.out.println("[客户端] 连接测试失败，移除: " + connKey + " - " + e.getMessage());
                try {
                    socket.close();
                } catch (IOException ignored) {}
                connectionPool.remove(connKey);
                socket = null;
            }
        }
        
        // 如果没有可用连接，创建新连接
        if (socket == null || !connectionValid) {
            socket = new Socket();
            System.out.println("[客户端]  创建新 Socket 连接 -> " + connKey);
            socket.connect(new java.net.InetSocketAddress(host, port), 4000);
            socket.setSoTimeout(6000);
            connectionPool.put(connKey, socket);
        }
        
        try {
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.write(bodyBytes);
            out.flush();
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
            int state = 0;
            while (true) {
                int b = in.read(); if (b == -1) break; headerBuf.write(b);
                if (state == 0 && b == '\r') state = 1; else if (state == 1 && b == '\n') state = 2; else if (state == 2 && b == '\r') state = 3; else if (state == 3 && b == '\n') break; else state = 0;
                if (headerBuf.size() > 32_768) throw new IOException("Header too large");
            }
            String head = headerBuf.toString(StandardCharsets.US_ASCII);
            String[] lines = head.split("\r\n");
            int contentLength = -1;
            for (String l : lines) {
                if (l.toLowerCase().startsWith("content-length:")) { try { contentLength = Integer.parseInt(l.substring(15).trim()); } catch (NumberFormatException ignore) {} }
                if (l.toLowerCase().startsWith("set-cookie:")) {
                    String val = l.substring(11).trim(); String pair = val.split(";",2)[0]; int eq = pair.indexOf('=');
                    if (eq>0) { cookies.put(pair.substring(0,eq).trim(), pair.substring(eq+1).trim()); }
                }
            }
            byte[] bodyResp = new byte[0]; if (contentLength > 0) bodyResp = in.readNBytes(contentLength);
            
            // 检查服务器是否要求关闭连接
            boolean shouldClose = false;
            for (String l : lines) {
                if (l.toLowerCase().startsWith("connection:")) {
                    String val = l.substring(11).trim().toLowerCase();
                    if (val.equals("close")) {
                        shouldClose = true;
                        System.out.println("[客户端] 服务器要求关闭连接: " + connKey);
                        break;
                    }
                }
            }
            
            // 如果服务器要求关闭，则关闭并移除连接
            if (shouldClose) {
                connectionPool.remove(connKey);
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
            
            return head + new String(bodyResp, StandardCharsets.UTF_8);
        } catch (IOException e) {
            // 连接出错，移除并关闭
            System.out.println("[客户端] 连接异常，移除: " + connKey + " - " + e.getMessage());
            connectionPool.remove(connKey);
            try {
                if (socket != null) socket.close();
            } catch (IOException ignored) {}
            throw e;
        }
    }

    private static byte[] readAllBytes(File f) throws IOException {
        try (InputStream in = new FileInputStream(f); ByteArrayOutputStream bout = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int r; while ((r = in.read(buf)) != -1) bout.write(buf,0,r); return bout.toByteArray();
        }
    }

    /**
     * 保存 GET 响应内容到本地 local 目录
     */
    private static void saveResponseToLocal(String url, String headers, byte[] bodyBytes) {
        try {
            // 从 URL 提取文件名
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (path == null || path.isEmpty() || path.equals("/")) {
                path = "/index.html";
            }
            String filename = path.substring(path.lastIndexOf('/') + 1);
            if (filename.isEmpty()) filename = "index.html";
            
            // 文件名安全处理
            filename = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
            
            // 创建 local 目录
            File localDir = new File("local");
            if (!localDir.exists()) {
                localDir.mkdirs();
            }
            
            // 保存文件
            File outputFile = new File(localDir, filename);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(bodyBytes);
            }
            
            System.out.println("文件已保存: " + outputFile.getAbsolutePath() + " (" + bodyBytes.length + " bytes)");
            
        } catch (Exception e) {
            System.err.println("保存文件失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
