package com.example.http.http;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP响应封装类
 * 
 * 这个类用于构建和表示HTTP响应，支持链式调用的构建模式。
 * 提供了完整的HTTP响应功能，包括状态行、头部和响应体。
 * 
 * HTTP响应格式（RFC2616）：
 * Status-Line = HTTP-Version SP Status-Code SP Reason-Phrase CRLF
 * *(header CRLF)
 * CRLF
 * [message-body]
 * 
 * 主要功能：
 * - 设置HTTP状态码和原因短语
 * - 添加HTTP响应头部
 * - 设置响应体数据（支持文本和二进制）
 * - 自动处理Content-Length头部
 * - 支持HTTP Keep-Alive连接控制
 * - 链式构建API，便于使用
 * 
 * 使用示例：
 * HttpResponse response = new HttpResponse()
 *     .status(HttpStatus.OK)
 *     .header("Content-Type", "text/html; charset=UTF-8")
 *     .header("Server", "SimpleServer/1.0")
 *     .bodyText("<h1>Hello World</h1>", "text/html; charset=UTF-8");
 * 
 * byte[] responseData = response.toBytes(true); // 支持Keep-Alive
 */
public class HttpResponse {
    
    /** HTTP状态码，默认为200 OK */
    private HttpStatus status = HttpStatus.OK;
    
    /** HTTP响应头部，使用LinkedHashMap保持插入顺序 */
    private final Map<String, String> headers = new LinkedHashMap<>();
    
    /** 响应体数据，原始字节数组 */
    private byte[] body = new byte[0];

    // ========== 链式构建方法 ==========
    
    /**
     * 设置HTTP状态码
     * 
     * @param status HTTP状态枚举值
     * @return 当前HttpResponse实例，支持链式调用
     */
    public HttpResponse status(HttpStatus status) { 
        this.status = status; 
        return this; 
    }
    
    /**
     * 添加HTTP响应头部
     * 
     * @param name 头部名称
     * @param value 头部值
     * @return 当前HttpResponse实例，支持链式调用
     */
    public HttpResponse header(String name, String value) { 
        headers.put(name, value); 
        return this; //即链式调用
    }
    
    /**
     * 设置响应体数据
     * 
     * @param data 响应体的原始字节数组
     * @return 当前HttpResponse实例，支持链式调用
     */
    public HttpResponse body(byte[] data) { 
        this.body = data != null ? data : new byte[0]; 
        return this; 
    }
    
    /**
     * 设置文本响应体
     * 
     * 自动设置Content-Type和Content-Length头部。
     * 使用UTF-8编码进行文本转换。
     * 
     * @param text 文本内容
     * @param contentType 内容类型，如"text/html; charset=UTF-8"
     * @return 当前HttpResponse实例，支持链式调用
     */
    public HttpResponse bodyText(String text, String contentType) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        header("Content-Type", contentType);
        header("Content-Length", String.valueOf(bytes.length));
        this.body = bytes;
        return this;
    }

    // ========== 响应序列化方法 ==========
    
    /**
     * 将HTTP响应转换为字节数组
     * 
     * 这个方法会构建完整的HTTP响应，包括：
     * 1. 状态行（HTTP/1.1 200 OK）
     * 2. 所有响应头部
     * 3. 空行（头部和体部分隔）
     * 4. 响应体数据
     * 
     * 会自动处理以下头部：
     * - Connection: 根据keepAlive参数设置
     * - Content-Length: 自动计算并设置
     * 
     * @param keepAlive 是否保持连接（HTTP Keep-Alive）
     * @return 完整HTTP响应的字节数组
     */
    //本类中的主函数，
    public byte[] toBytes(boolean keepAlive) {
        // 构建响应头部字符串
        StringBuilder headerBuilder = new StringBuilder();
        
        // 状态行：HTTP/1.1 200 OK
        headerBuilder.append("HTTP/1.1 ").append(status.format()).append("\r\n");
        
        // 设置Connection头部
        if (keepAlive) {
            headers.putIfAbsent("Connection", "keep-alive");
        } else {
            headers.put("Connection", "close");
        }
        
        // 自动设置Content-Length（如果尚未设置）
        headers.putIfAbsent("Content-Length", String.valueOf(body.length));
        
        // 添加所有头部
        headers.forEach((name, value) -> 
            headerBuilder.append(name).append(": ").append(value).append("\r\n"));
        //lambda表达式，匿名函数作为函数参数
        // 头部结束标记
        headerBuilder.append("\r\n");
        
        // 转换为字节数组
        byte[] headerBytes = headerBuilder.toString().getBytes(StandardCharsets.US_ASCII);
        
        // 合并头部和体部
        byte[] fullResponse = new byte[headerBytes.length + body.length];
        System.arraycopy(headerBytes, 0, fullResponse, 0, headerBytes.length);
        System.arraycopy(body, 0, fullResponse, headerBytes.length, body.length);
        
        return fullResponse;
    }
}
