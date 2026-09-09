package com.example.usercenter.filter;

import com.example.usercenter.utils.XssUtils;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class XssHttpServletRequestWrapper extends HttpServletRequestWrapper {

    public XssHttpServletRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getParameter(String name) {
        return XssUtils.clean(super.getParameter(name));
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) return null;
        return Arrays.stream(values).map(XssUtils::clean).toArray(String[]::new);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        // 请求体（JSON / multipart）不做 HTML 清洗：
        // Jsoup 会把 JSON 当 HTML 解析、破坏其结构（转义引号、标签化、规范化空白），
        // 导致 Jackson 反序列化失败（如 "Illegal unquoted character (CTRL-CHAR, code 10)"）。
        // XSS 防护由前端渲染层 DOMPurify 兜底（NoteDetail 等已接入），后端不在 body 层做 HTML 清洗。
        return super.getInputStream();
    }
}
