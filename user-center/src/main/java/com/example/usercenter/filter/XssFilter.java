package com.example.usercenter.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * XSS放跨站脚本攻击过滤器
 */
@Component
@Order(1)
public class XssFilter implements Filter {

    /**
     * 核心过滤方法，每次请求都会触发
     * @param request 通用请求
     * @param response 响应请求
     * @param chain 过滤器链，放行请求
     * @throws IOException
     * @throws ServletException
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        //将过滤清洗后的请求向后传递，继续走后续流程
        chain.doFilter(new XssHttpServletRequestWrapper((HttpServletRequest) request), response);
    }
}
