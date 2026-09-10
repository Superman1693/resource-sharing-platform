package com.example.usercenter.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 请求耗时诊断 Filter：记录每个请求 URL + 总耗时，慢请求（≥200ms）WARN 提醒
 * 临时诊断用——定位 500ms 卡顿根因后可移除
 * @author zy
 */
@Component
@Slf4j
@Order(0)
public class TimingFilter implements Filter {

    /** 慢请求阈值（ms），超过则 WARN */
    private static final long SLOW_THRESHOLD = 200;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, jakarta.servlet.ServletException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(req, res);
        } finally {
            long cost = System.currentTimeMillis() - start;
            String uri = (req instanceof HttpServletRequest)
                    ? ((HttpServletRequest) req).getRequestURI()
                    : "?";
            String qs = (req instanceof HttpServletRequest)
                    ? ((HttpServletRequest) req).getQueryString()
                    : null;
            String full = qs != null ? uri + "?" + qs : uri;
            if (cost >= SLOW_THRESHOLD) {
                log.warn("[慢请求] {} -> {}ms", full, cost);
            } else {
                log.info("[请求] {} -> {}ms", full, cost);
            }
        }
    }
}
