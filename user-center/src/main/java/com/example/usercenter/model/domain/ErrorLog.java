package com.example.usercenter.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 前端错误日志实体类
 *
 * <p>用于把前端「JS 错误 / 未捕获 Promise / Vue 组件异常 / 资源加载失败」的批量上报落库，
 * 形成可查询、可统计的错误闭环（原先仅写日志，无法回溯）。</p>
 *
 * @TableName error_log
 */
@TableName(value = "error_log")
@Data
public class ErrorLog {

    /** 日志ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 错误类型：js-error / unhandled-rejection / vue-error / resource-error / manual */
    private String errorType;

    /** 错误消息 */
    private String message;

    /** 错误堆栈 */
    private String stack;

    /** 错误来源文件 */
    private String source;

    /** 行号 */
    private Integer lineno;

    /** 列号 */
    private Integer colno;

    /** Vue 组件信息 */
    private String componentInfo;

    /** Vue 组件名 */
    private String componentName;

    /** 资源标签名 */
    private String tagName;

    /** 发生错误的用户ID（未登录为空） */
    private Long userId;

    /** 页面 URL */
    private String url;

    /** User Agent */
    private String userAgent;

    /** 客户端上报时间戳（毫秒） */
    private Long clientTime;

    /** 服务端落库时间 */
    private Date createTime;
}
