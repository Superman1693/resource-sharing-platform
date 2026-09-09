package com.example.usercenter.utils;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.apache.commons.lang3.StringUtils;

/**
 * 用户输入内容消毒器，把用户输入的危险HTML/JS代码全部清除，只保留安全标签
 */
public class XssUtils {
    // 白名单：允许 Markdown 渲染后的安全 HTML 标签
    private static final Safelist SAFELIST = Safelist.relaxed()
            //增加支持Markdown语法：pre：段落代码块，code：行内代码，del：删除线，ins：下划线
        .addTags("pre", "code", "del", "ins")
            //允许代码高亮
        .addAttributes("code", "class")
            //禁止链接的使用
        .removeProtocols("a", "href", "ftp", "javascript");

    public static String clean(String input) {
        if (StringUtils.isBlank(input)) return input;
        return Jsoup.clean(input, SAFELIST);
    }
}
