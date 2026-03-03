package org.example.trans_java_demo.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.multipart.MultipartResolver;

import java.io.*;

@Configuration
public class WebConfig {

    /**
     * 禁用 Spring 的 MultipartResolver
     * 通过不定义 multipartResolver bean，Spring 将不会自动解析 multipart 请求
     */
    @Bean(name = "multipartResolver")
    public MultipartResolver multipartResolver() {
        // 返回一个什么都不做的 MultipartResolver
        return new NoOpMultipartResolver();
    }

    /**
     * 注册请求 body 缓存 Filter
     * 优先级设置为最高，确保在所有其他 Filter 之前执行
     */
    @Bean
    public FilterRegistrationBean<CachedBodyFilter> cachedBodyFilter() {
        FilterRegistrationBean<CachedBodyFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new CachedBodyFilter());
        registrationBean.addUrlPatterns("/proxy/*");
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registrationBean;
    }

    /**
     * 什么都不做的 MultipartResolver
     * 让 Spring 认为没有 multipart 需要解析
     */
    public static class NoOpMultipartResolver implements MultipartResolver {
        @Override
        public boolean isMultipart(HttpServletRequest request) {
            // 始终返回 false，告诉 Spring 这不是 multipart 请求
            return false;
        }

        @Override
        public org.springframework.web.multipart.MultipartHttpServletRequest resolveMultipart(HttpServletRequest request) {
            throw new UnsupportedOperationException("Multipart resolution is disabled");
        }

        @Override
        public void cleanupMultipart(org.springframework.web.multipart.MultipartHttpServletRequest request) {
            // 不需要清理
        }
    }

    /**
     * 缓存请求 body 的 Filter
     * 将 InputStream 内容缓存到内存中，允许多次读取
     */
    public static class CachedBodyFilter implements Filter {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            if (request instanceof HttpServletRequest) {
                CachedBodyHttpServletRequest cachedRequest =
                    new CachedBodyHttpServletRequest((HttpServletRequest) request);
                chain.doFilter(cachedRequest, response);
            } else {
                chain.doFilter(request, response);
            }
        }
    }

    /**
     * 可重复读取 body 的 HttpServletRequest 包装类
     */
    public static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private byte[] cachedBody;

        public CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            InputStream inputStream = request.getInputStream();
            this.cachedBody = inputStream.readAllBytes();
        }

        @Override
        public ServletInputStream getInputStream() {
            return new CachedBodyServletInputStream(this.cachedBody);
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream()));
        }
    }

    /**
     * 从缓存的字节数组创建 ServletInputStream
     */
    public static class CachedBodyServletInputStream extends ServletInputStream {
        private final ByteArrayInputStream inputStream;

        public CachedBodyServletInputStream(byte[] cachedBody) {
            this.inputStream = new ByteArrayInputStream(cachedBody);
        }

        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read() {
            return inputStream.read();
        }
    }
}
