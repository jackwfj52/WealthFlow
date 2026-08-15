package org.jack.wealthflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置：允许前端开发服务器（Vite）与桌面端（Tauri）直连后端 API。
 * 开发环境也可以走 Vite proxy（同源），此处配置用于直连场景。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:5173",
                        "http://127.0.0.1:5173",
                        // Tauri 桌面端（Windows 生产环境 WebView2 使用 tauri.localhost）
                        "http://tauri.localhost",
                        "https://tauri.localhost",
                        "tauri://localhost")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
