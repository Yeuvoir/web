package com.example.http;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 简易HTTP服务器主类
 * 
 * 这个类实现了一个基于Java Socket的HTTP服务器，具有以下特性：
 * - 监听指定端口，等待客户端连接
 * - 使用线程池处理并发连接，提高性能
 * - 支持HTTP长连接（Keep-Alive）
 * - 每个连接由SimpleHttpWorker处理，可以处理多个HTTP请求
 * 
 * 工作原理：
 * 1. 创建ServerSocket监听指定端口
 * 2. 循环接受客户端连接
 * 3. 为每个连接创建SimpleHttpWorker任务
 * 4. 提交到线程池中异步处理
 * 
 */
public class SimpleHttpServer {
    
    // 服务器监听的端口号
    private final int port;
    
    // 控制服务器运行状态的标志，volatile确保多线程可见性
    private volatile boolean running = true;
    
    // 线程池，用于处理并发连接
    private final ExecutorService threadPool;
    
    // Socket读取超时时间（毫秒）
    private static final int SOCKET_TIMEOUT = 10000;

    /**
     * 创建HTTP服务器实例
     * 
     * @param port 服务器要监听的端口号
     */
    public SimpleHttpServer(int port) {
        this.port = port;
        
        // 计算线程池大小
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        int threadPoolSize = availableProcessors * 2 + 2;
        
        // 创建固定大小的线程池
        this.threadPool = Executors.newFixedThreadPool(threadPoolSize);
        
        System.out.println("HTTP服务器初始化完成");
        System.out.println("- 监听端口: " + port);
        System.out.println("- 线程池大小: " + threadPoolSize + " (CPU核心数: " + availableProcessors + ")");
        System.out.println("- Socket超时: " + SOCKET_TIMEOUT + "ms");
    }

    /**
     * 启动HTTP服务器
     * 
     * 这个方法会阻塞当前线程，直到服务器停止运行。
     * 服务器会持续监听端口，接受客户端连接并处理请求。
     */
    public void start() {
        System.out.println("========================================");
        System.out.println("HTTP服务器启动中...");
        System.out.println("服务器地址: http://localhost:" + port);
        System.out.println("按 Ctrl+C 停止服务器");
        System.out.println("========================================");
        
        // 使用try-with-resources确保ServerSocket正确关闭
        try (ServerSocket serverSocket = createServerSocket()) {
            
            // 主循环：持续接受客户端连接
            while (running) {
                try {
                    // 等待客户端连接（这里会阻塞直到有连接到来）
                    Socket clientSocket = serverSocket.accept();
                    
                    // 处理新连接
                    handleNewConnection(clientSocket);
                    
                } catch (IOException e) {
                    // 如果服务器正在运行，打印错误信息
                    if (running) {
                        System.err.println("接受连接时发生错误: " + e.getMessage());
                    }
                }
            }
            
        } catch (IOException e) {
            System.err.println("服务器启动失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 确保线程池正确关闭
            shutdownServer();
        }
    }
    
    /**
     * 创建并配置ServerSocket
     * 
     * @return 配置好的ServerSocket实例
     * @throws IOException 如果创建Socket失败
     */
    private ServerSocket createServerSocket() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("ServerSocket创建成功，端口: " + port);
        return serverSocket;
    }
    
    /**
     * 处理新的客户端连接
     * 
     * @param clientSocket 客户端Socket连接
     */
    private void handleNewConnection(Socket clientSocket) {
        try {
            // 记录新连接信息
            String clientAddress = clientSocket.getRemoteSocketAddress().toString();
            System.out.println("[服务器] 接受新连接: " + clientAddress);
            
            // 配置Socket选项
            configureSocket(clientSocket);
            
            // 创建工作线程处理这个连接
            SimpleHttpWorker worker = new SimpleHttpWorker(clientSocket);
            
            // 提交到线程池异步处理
            threadPool.submit(worker);
            
        } catch (Exception e) {
            System.err.println("处理新连接时发生错误: " + e.getMessage());
            
            // 如果配置失败，关闭连接
            try {
                clientSocket.close();
            } catch (IOException ignore) {
                // 忽略关闭错误
            }
        }
    }
    
    /**
     * 配置客户端Socket选项
     * 
     * @param socket 要配置的Socket
     */
    private void configureSocket(Socket socket) {
        try {
            // 启用TCP Keep-Alive，这有助于检测死连接
            socket.setKeepAlive(true);
            
            // 设置Socket读取超时，防止客户端长时间无响应
            socket.setSoTimeout(SOCKET_TIMEOUT);
            
            // 启用TCP无延迟，提高小数据包传输效率
            socket.setTcpNoDelay(true);
            
        } catch (Exception e) {
            System.err.println("IN configureSocket() 配置Socket选项时发生错误: " + e.getMessage());
            // 配置失败不影响继续处理连接
        }
    }
    
    /**
     * 停止HTTP服务器
     * 
     * 这个方法会设置running标志为false，并关闭线程池。
     * 注意：这不会立即停止服务器，而是让主循环自然退出。
     */
    public void stop() {
        System.out.println("正在停止HTTP服务器...");
        running = false;
        shutdownServer();
    }
    
    /**
     * 关闭服务器资源
     */
    private void shutdownServer() {
        System.out.println("正在关闭线程池...");
        
        // 优雅关闭线程池
        threadPool.shutdown();
        
        try {
            // 等待线程池关闭，最多等待30秒
            if (!threadPool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)) {
                System.err.println("线程池未能在30秒内关闭，强制关闭");
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            System.err.println("等待线程池关闭时被中断");
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("HTTP服务器已停止");
    }
    
    /**
     * 获取服务器监听的端口
     * 
     * @return 端口号
     */
    public int getPort() {
        return port;
    }
    
    /**
     * 检查服务器是否正在运行
     * 
     * @return true如果服务器正在运行
     */
    public boolean isRunning() {
        return running;
    }
}
