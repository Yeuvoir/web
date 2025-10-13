package com.example.http.user;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class UserService {
    
    /** 用户存储：用户名 -> 密码的映射，使用ConcurrentHashMap确保线程安全 */
    private final Map<String, String> users = new ConcurrentHashMap<>();

    public boolean register(String username, String password) {
        // 参数验证：用户名和密码都不能为空或空白
        if (isBlank(username) || isBlank(password)) {
            return false;
        }
        
        // 如果用户名已存在，返回false；否则存储并返回true
        return users.putIfAbsent(username, password) == null;
    }

    public boolean login(String username, String password) {
        // 参数验证：用户名和密码都不能为空或空白
        if (isBlank(username) || isBlank(password)) {
            return false;
        }
        
        return Objects.equals(users.get(username), password);
    }

    /**
     * 检查字符串是否为空白
     */
    private boolean isBlank(String str) { 
        return str == null || str.trim().isEmpty(); 
    }
}
