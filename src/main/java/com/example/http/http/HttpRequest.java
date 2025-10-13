package com.example.http.http;

import java.util.*;

/**
 * HTTP请求封装类
 * 
 * 这个类封装了一个完整的HTTP请求，包含请求行、头部和请求体。
 * 支持HTTP/1.1协议的基本功能，包括表单参数解析和Cookie处理。
 * 
 * HTTP请求格式（RFC2616）：
 * Request-Line = Method SP Request-URI SP HTTP-Version CRLF
 * *(header CRLF)
 * CRLF
 * [message-body]
 * 
 * 主要功能：
 * - 解析和存储HTTP请求行（方法、路径、协议版本）
 * - 管理HTTP头部信息（支持多值头部）
 * - 处理请求体数据
 * - 自动解析表单参数（application/x-www-form-urlencoded）
 * - 解析Cookie头部
 * 
 * 使用示例：
 * HttpRequest request = new HttpRequest();
 * request.setStartLine("GET", "/index.html", "HTTP/1.1");
 * request.addHeader("Host", "localhost:8080");
 * request.addHeader("User-Agent", "SimpleClient/1.0");
 * 
 * String method = request.method();           // 获取HTTP方法
 * String path = request.path();               // 获取请求路径
 * String userAgent = request.headerFirst("User-Agent"); // 获取头部值
 * String username = request.form("username"); // 获取表单参数
 * String sessionId = request.cookie("SID");   // 获取Cookie值
 */
public class HttpRequest {
    
    // ========== 基本HTTP请求字段 ==========
    
    /** HTTP方法：GET、POST、PUT、DELETE等 */
    private String method;
    
    /** 请求路径：不包含查询字符串的纯路径部分 */
    private String path;
    
    /** 查询字符串：URL中?后面的部分，如"name=value&id=123" */
    private String queryString;
    
    /** HTTP协议版本，如"HTTP/1.1" */
    private String version;
    
    /** HTTP头部字段：使用LinkedHashMap保持插入顺序，支持多值头部 */
    private final Map<String, List<String>> headers = new LinkedHashMap<>();
    
    /** 请求体数据：原始字节数组 */
    private byte[] body = new byte[0];

    // ========== 解析后的便利字段 ==========
    
    /** 表单参数：解析application/x-www-form-urlencoded格式的请求体 */
    private final Map<String, String> formParams = new HashMap<>();
    
    /** Cookie参数：延迟解析Cookie头部，提高性能 */
    private Map<String, String> cookies;

    // ========== 基本访问器方法 ==========
    
    /**
     * 获取HTTP方法
     * @return HTTP方法字符串，如"GET"、"POST"
     */
    public String method() { return method; }
    
    /**
     * 获取请求路径
     * @return 请求路径，不包含查询字符串
     */
    public String path() { return path; }
    
    /**
     * 获取查询字符串
     * @return 查询字符串，如"name=value&id=123"，可能为null
     */
    public String queryString() { return queryString; }
    
    /**
     * 获取HTTP协议版本
     * @return 协议版本，如"HTTP/1.1"
     */
    public String version() { return version; }
    
    /**
     * 获取所有HTTP头部
     * @return 头部Map的只读视图
     */
    public Map<String, List<String>> headers() { return headers; }
    
    /**
     * 获取请求体数据
     * @return 请求体的原始字节数组
     */
    public byte[] body() { return body; }

    // ========== 请求设置方法 ==========
    
    /**
     * 设置HTTP请求行
     * 
     * @param method HTTP方法，如"GET"、"POST"
     * @param uri 请求URI，可能包含查询字符串
     * @param version HTTP协议版本，如"HTTP/1.1"
     */
    public void setStartLine(String method, String uri, String version) {
        this.method = method;
        this.version = version;
        
        // 拆分URI中的路径和查询字符串
        // 例如："/login?username=admin&password=123"
        // 路径："/login"，查询字符串："username=admin&password=123"
        int questionMarkIndex = uri.indexOf('?');
        if (questionMarkIndex >= 0) {
            this.path = uri.substring(0, questionMarkIndex);
            this.queryString = uri.substring(questionMarkIndex + 1);
        } else {
            this.path = uri;
            this.queryString = null;
        }
    }

    /**
     * 添加HTTP头部字段
     * 
     * 支持同名的多个头部值，这在HTTP协议中是合法的。
     * 头部名称会自动转换为小写以便于查找。
     * 
     * @param name 头部名称，如"Content-Type"、"User-Agent"
     * @param value 头部值，会自动去除首尾空白字符
     */
    public void addHeader(String name, String value) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        headers.computeIfAbsent(normalizedName, k -> new ArrayList<>()).add(value.trim());
    }

    // ========== 头部访问方法 ==========
    
    /**
     * 获取指定名称的所有头部值
     * 
     * @param name 头部名称（不区分大小写）
     * @return 头部值列表，如果不存在则返回空列表
     */
    public List<String> headerValues(String name) {
        String normalizedName = name.toLowerCase(Locale.ROOT);
        return headers.getOrDefault(normalizedName, Collections.emptyList());
    }

    /**
     * 获取指定名称的第一个头部值
     * 
     * 这是最常用的头部访问方法，因为大多数HTTP头部只有一个值。
     * 
     * @param name 头部名称（不区分大小写）
     * @return 第一个头部值，如果不存在则返回null
     */
    public String headerFirst(String name) {
        List<String> values = headerValues(name);
        return values.isEmpty() ? null : values.get(0);
    }

    // ========== 请求体处理方法 ==========
    
    /**
     * 设置请求体数据
     * 
     * 如果Content-Type是application/x-www-form-urlencoded，
     * 会自动解析表单参数。
     * 
     * @param body 请求体的原始字节数组
     */
    public void setBody(byte[] body) {
        this.body = body != null ? body : new byte[0];
        
        // 自动解析表单数据
        String contentType = headerFirst("content-type");
        if (contentType != null && 
            contentType.startsWith("application/x-www-form-urlencoded")) {
            
            String formString = new String(this.body, java.nio.charset.StandardCharsets.UTF_8);
            parseForm(formString);
        }
    }

    /**
     * 解析表单参数字符串
     * 
     * 格式：key1=value1&key2=value2&key3=value3
     * 支持URL编码，会自动解码。
     * 
     * @param formString 表单参数字符串
     */
    private void parseForm(String formString) {
        String[] pairs = formString.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue; // 跳过空参数
            }
            
            int equalIndex = pair.indexOf('=');
            String key, value;
            
            if (equalIndex >= 0) {
                // 有值的情况：key=value
                key = urlDecode(pair.substring(0, equalIndex));
                value = urlDecode(pair.substring(equalIndex + 1));
            } else {
                // 只有键没有值的情况：key
                key = urlDecode(pair);
                value = "";
            }
            
            formParams.put(key, value);
        }
    }

    /**
     * URL解码工具方法
     * 
     * 使用UTF-8编码进行解码，如果解码失败则返回原字符串。
     * 
     * @param encoded 编码后的字符串
     * @return 解码后的字符串
     */
    private String urlDecode(String encoded) {
        try {
            return java.net.URLDecoder.decode(encoded, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 解码失败时返回原字符串
            return encoded;
        }
    }

    // ========== 表单参数访问方法 ==========
    
    /**
     * 获取指定名称的表单参数值
     * 
     * @param key 参数名称
     * @return 参数值，如果不存在则返回null
     */
    public String form(String key) { 
        return formParams.get(key); 
    }
    
    /**
     * 获取所有表单参数
     * 
     * @return 表单参数Map的只读视图
     */
    public Map<String,String> formAll() { 
        return Collections.unmodifiableMap(formParams); 
    }

    // ========== Cookie处理方法 ==========
    
    /**
     * 解析并返回所有Cookie
     * 
     * 使用延迟解析模式，只有在第一次调用时才解析Cookie头部。
     * Cookie头部格式：name1=value1; name2=value2; name3=value3
     * 
     * @return Cookie名称到值的映射
     */
    public Map<String,String> cookies() {
        // 延迟解析：只有在第一次访问时才解析
        if (cookies == null) {
            cookies = new HashMap<>();
            String cookieHeader = headerFirst("cookie");
            
            if (cookieHeader != null) {
                // 按分号分割Cookie，注意可能包含空格
                String[] parts = cookieHeader.split(";\\s*");
                
                for (String part : parts) {
                    int equalIndex = part.indexOf('=');
                    if (equalIndex > 0) {
                        String name = part.substring(0, equalIndex).trim();
                        String value = part.substring(equalIndex + 1).trim();
                        cookies.put(name, value);
                    }
                }
            }
        }
        return cookies;
    }

    /**
     * 获取指定名称的Cookie值
     * 
     * @param name Cookie名称
     * @return Cookie值，如果不存在则返回null
     */
    public String cookie(String name) { 
        return cookies().get(name); 
    }
}
