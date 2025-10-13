package com.example.http;

/**
 * 程序启动入口类
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsageInstructions();
            return;
        }
        //处理大小写
        String mode = args[0].toLowerCase();
        switch (mode) {
            case "server" -> startServerMode(args);
            case "client" -> startClientMode(args);
            default -> handleUnknownMode(mode);
        }
    }
    
    /**
     * 打印程序使用说明
     */
    private static void printUsageInstructions() {
        System.out.println("========================================");
        System.out.println("    简易HTTP服务器与客户端");
        System.out.println("========================================");
        System.out.println("使用方法:");
        System.out.println("  启动服务器: java -jar app.jar server [端口号]");
        System.out.println("  启动客户端: java -jar app.jar client [端口号]");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  java -jar app.jar server 8080  # 在8080端口启动服务器");
        System.out.println("  java -jar app.jar client       # 启动图形化客户端(默认端口8080)");
        System.out.println("  java -jar app.jar client 9090  # 启动客户端，默认目标服务器端口9090");
        System.out.println("========================================");
    }
    
    /**
     * 启动服务器模式
     * 
     * @param args 命令行参数，args[1]为端口号
     */
    private static void startServerMode(String[] args) {
        // 默认端口号
        int port = 8080;
        
        // 如果用户提供再修改
        if (args.length > 1) {
            port = parsePortNumber(args[1]);
        }
        
        System.out.println("正在启动HTTP服务器，端口: " + port);
        
        // 创建服务器并启动
        SimpleHttpServer server = new SimpleHttpServer(port);
        server.start();
    }
    
    /**
     * 启动客户端GUI模式
     * 
     * @param args 命令行参数，args[1]为服务器端口号
     */
    private static void startClientMode(String[] args) {
        // 默认端口
        int defaultPort = 8080;
        
        if (args.length > 1) {
            defaultPort = parsePortNumber(args[1]);
        }
        
        System.out.println("正在启动HTTP客户端GUI...");
        System.out.println("默认目标服务器端口: " + defaultPort);
        
        // 启动客户端GUI，传入默认端口
        HttpClientGui.launch(defaultPort);
    }
    
    /**
     * 处理未知的运行模式
     * 
     * @param mode 用户提供的错误模式
     */
    private static void handleUnknownMode(String mode) {
        System.err.println("错误: 未知的运行模式 '" + mode + "'");
        System.err.println("支持的模式: server, client");
        printUsageInstructions();
    }
    
    /**
     * 解析端口号字符串
     * 
     * @param portStr 端口号字符串
     * @return 解析成功的端口号，如果解析失败则返回默认值8080
     */
    private static int parsePortNumber(String portStr) {
        try {
            int port = Integer.parseInt(portStr);
            
            // 验证端口号范围 (1-65535)
            if (port < 1 || port > 65535) {
                System.err.println("警告: 端口号 " + port + " 超出有效范围(1-65535)，使用默认端口8080");
                return 8080;
            }
            
            return port;
        } catch (NumberFormatException e) {
            System.err.println("警告: 无法解析端口号 '" + portStr + "'，使用默认端口8080");
            return 8080;
        }
    }
}
