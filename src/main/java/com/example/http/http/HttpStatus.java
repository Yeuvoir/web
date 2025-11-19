package com.example.http.http;

/**
 * HTTP状态码枚举
 * 
 * 这个枚举定义了HTTP协议中常用的状态码，包括状态码数字和原因短语。
 * 每个状态码都有特定的含义，用于表示HTTP请求的处理结果。
 * 
 * HTTP状态码分类（RFC2616）：
 * - 2xx：成功 - 请求已成功接收、理解、并接受
 * - 3xx：重定向 - 需要后续操作才能完成请求
 * - 4xx：客户端错误 - 请求包含语法错误或无法完成请求
 * - 5xx：服务器错误 - 服务器未能处理合法的请求
 * 
 * 本项目只包含了最常用的状态码，可以根据需要扩展。
 * 
 * 使用示例：
 * HttpResponse response = new HttpResponse()
 *     .status(HttpStatus.OK)                    // 200 成功
 *     .status(HttpStatus.NOT_FOUND)             // 404 未找到
 *     .status(HttpStatus.INTERNAL_SERVER_ERROR); // 500 服务器错误
 * 
 * int code = HttpStatus.OK.code();              // 200
 * String reason = HttpStatus.OK.reason();       // "OK"
 * String formatted = HttpStatus.OK.format();    // "200 OK"
 */
public enum HttpStatus {
    
    // ========== 2xx 成功状态码 ==========
    
    /**
     * 200 OK
     * 
     * 请求已成功，请求所希望的响应头或数据体将随此响应返回。
     * 
     * 使用场景：
     * - GET请求成功返回资源
     * - POST请求成功处理
     * - PUT请求成功更新资源
     */
    OK(200, "OK"),
    
    // ========== 3xx 重定向状态码 ==========
    
    /**
     * 301 Moved Permanently
     * 
     * 请求的资源已经永久的移动到了新的URI。
     * 浏览器会自动跳转到新的URI，并且在后续请求中直接使用新URI。
     * 
     * 使用场景：
     * - 网站改版，URL结构变化
     * - 域名变更
     * - HTTP到HTTPS的重定向
     */
    MOVED_PERMANENTLY(301, "Moved Permanently"),
    
    /**
     * 302 Found
     * 
     * 请求的资源现在临时从不同的URI响应请求。
     * 浏览器会自动跳转到新的URI，但后续请求仍使用原URI。
     * 
     * 使用场景：
     * - 临时页面跳转
     * - 登录后跳转到原请求页面
     * - A/B测试中的流量分发
     */
    FOUND(302, "Found"),
    
    /**
     * 304 Not Modified
     * 
     * 资源未修改，可以使用缓存的版本。
     * 客户端发送了条件请求（如If-Modified-Since），服务器判断资源未变化。
     * 
     * 使用场景：
     * - 静态资源缓存控制
     * - 减少不必要的数据传输
     * - 提高页面加载速度
     */
    NOT_MODIFIED(304, "Not Modified"),
    
    // ========== 4xx 客户端错误状态码 ==========
    
    /**
     * 401 Unauthorized
     * 
     * 请求要求身份验证。
     * 客户端必须提供认证信息才能访问资源。
     * 
     * 使用场景：
     * - 需要登录才能访问的页面
     * - API接口需要认证
     * - 会话过期需要重新登录
     */
    UNAUTHORIZED(401, "Unauthorized"),
    
    /**
     * 404 Not Found
     * 
     * 服务器上没有找到请求的资源。
     * 这是最常见的HTTP错误状态码。
     * 
     * 使用场景：
     * - URL路径错误
     * - 资源已被删除
     * - 链接失效
     */
    NOT_FOUND(404, "Not Found"),
    
    /**
     * 405 Method Not Allowed
     * 
     * 请求方法不被允许。
     * 服务器支持该资源，但不支持请求的HTTP方法。
     * 
     * 使用场景：
     * - 对静态资源使用POST方法
     * - API接口不支持某些HTTP方法
     * - RESTful API中方法使用错误
     */
    METHOD_NOT_ALLOWED(405, "Method Not Allowed"),

    /**
     * 409 Conflict
     * 
     * 请求与服务器当前状态冲突。
     * 
     * 使用场景：
     * - 注册时用户名已存在
     * - 资源状态冲突
     */
    CONFLICT(409, "Conflict"),

    /**
     * 422 Unprocessable Entity
     * 
     * 请求格式正确，但是由于含有语义错误，无法响应。
     * 
     * 使用场景：
     * - 上传文件时文件名非法
     * - 提交的数据验证失败
     */
    UNPROCESSABLE_ENTITY(422, "Unprocessable Entity"),
    
    // ========== 5xx 服务器错误状态码 ==========
    
    /**
     * 500 Internal Server Error
     * 
     * 服务器内部错误，无法完成请求。
     * 这是一个通用的服务器错误状态码。
     * 
     * 使用场景：
     * - 程序异常未捕获
     * - 数据库连接失败
     * - 配置错误
     * - 资源不足
     */
    INTERNAL_SERVER_ERROR(500, "Internal Server Error");

    // ========== 枚举属性和方法 ==========
    
    /** HTTP状态码数字，如200、404等 */
    private final int code;
    
    /** HTTP原因短语，如"OK"、"Not Found"等 */
    private final String reason;

    /**
     * 构造HTTP状态码枚举
     * 
     * @param code 状态码数字
     * @param reason 原因短语
     */
    HttpStatus(int code, String reason) {
        this.code = code;
        this.reason = reason;
    }

    /**
     * 获取状态码数字
     * 
     * @return 状态码数字，如200、404等
     */
    public int code() { return code; }
    
    /**
     * 获取原因短语
     * 
     * @return 原因短语，如"OK"、"Not Found"等
     */
    public String reason() { return reason; }

    /**
     * 获取格式化的状态行
     * 
     * 返回"code reason"格式的字符串，用于HTTP响应行。
     * 
     * @return 格式化的状态行，如"200 OK"、"404 Not Found"等
     */
    public String format() { return code + " " + reason; }
}
