package com.example.http.http;

import java.util.HashMap;
import java.util.Map;

/**
 * 简单 MIME 类型映射。可根据扩展名查找 Content-Type。
 * 至少包含三种：text/html, text/plain, image/png。
 */
public final class MimeTypes {
    private static final Map<String, String> MAP = new HashMap<>();
    static {
        MAP.put(".html", "text/html; charset=UTF-8");
        MAP.put(".htm", "text/html; charset=UTF-8");
        MAP.put(".txt", "text/plain; charset=UTF-8");
        MAP.put(".png", "image/png");
        MAP.put(".jpg", "image/jpeg");
        MAP.put(".jpeg", "image/jpeg");
        MAP.put(".gif", "image/gif");
        MAP.put(".pdf", "application/pdf");
        // 可扩展更多
    }

    public static String get(String path) {
        int idx = path.lastIndexOf('.');
        if (idx >= 0) {
            return MAP.getOrDefault(path.substring(idx).toLowerCase(), "application/octet-stream");
        }
        return "application/octet-stream";
    }
}
