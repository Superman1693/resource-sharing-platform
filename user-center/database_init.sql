-- 创建数据库
CREATE DATABASE IF NOT EXISTS yiya CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE yiya;

-- 创建用户表
CREATE TABLE IF NOT EXISTS `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `username` varchar(256) DEFAULT NULL COMMENT '用户昵称',
  `userAccount` varchar(256) NOT NULL COMMENT '账号',
  `avatarUrl` varchar(1024) DEFAULT NULL COMMENT '用户头像',
  `gender` tinyint DEFAULT NULL COMMENT '性别',
  `userPassword` varchar(512) NOT NULL COMMENT '密码',
  `phone` varchar(128) DEFAULT NULL COMMENT '电话',
  `email` varchar(512) DEFAULT NULL COMMENT '邮箱',
  `userStatus` int NOT NULL DEFAULT '0' COMMENT '状态 0 - 正常',
  `createTime` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updateTime` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `isDelete` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除',
  `userRole` int NOT NULL DEFAULT '0' COMMENT '用户角色 0 - 普通用户 1 - 管理员',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uni_userAccount` (`userAccount`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户';

-- 插入测试数据
INSERT INTO `user` (`username`, `userAccount`, `avatarUrl`, `gender`, `userPassword`, `phone`, `email`, `userStatus`, `userRole`) VALUES
('管理员', 'admin', 'https://cdn.jsdelivr.net/gh/Superman1693/images/logo.png', 1, 'e10adc3949ba59abbe56e057f20f883e', '13800138000', 'admin@example.com', 0, 1),
('测试用户', 'test', 'https://cdn.jsdelivr.net/gh/Superman1693/images/logo.png', 0, 'e10adc3949ba59abbe56e057f20f883e', '13800138001', 'test@example.com', 0, 0);

-- 注意：密码是 '123456' 经过MD5加密后的结果（加盐值：yiyi）
-- 实际密码：123456
-- 加密方式：MD5(SALT + password) = MD5('yiyi' + '123456')
