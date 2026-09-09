package com.example.usercenter.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.usercenter.common.ErrorCode;
import com.example.usercenter.common.PageResult;
import com.example.usercenter.exception.BusinessException;
import com.example.usercenter.mapper.UserFollowMapper;
import com.example.usercenter.mapper.UserMapper;
import com.example.usercenter.model.domain.User;
import com.example.usercenter.model.domain.UserFollow;
import com.example.usercenter.service.FollowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FollowServiceImpl extends ServiceImpl<UserFollowMapper, UserFollow> implements FollowService {

    private final UserFollowMapper userFollowMapper;
    private final UserMapper userMapper;

    @Override
    public void follow(Long userId, Long followUserId) {
        if (userId.equals(followUserId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能关注自己");
        }
        if (isFollowing(userId, followUserId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "已关注该用户");
        }
        UserFollow follow = new UserFollow();
        follow.setUserId(userId);
        follow.setFollowUserId(followUserId);
        follow.setCreateTime(new Date());
        this.save(follow);
    }

    @Override
    public void unfollow(Long userId, Long followUserId) {
        this.remove(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getUserId, userId)
                .eq(UserFollow::getFollowUserId, followUserId));
    }

    @Override
    public boolean isFollowing(Long userId, Long targetUserId) {
        return this.count(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getUserId, userId)
                .eq(UserFollow::getFollowUserId, targetUserId)) > 0;
    }

    @Override
    public PageResult<User> getFollowingList(Long userId, int page, int pageSize) {
        IPage<UserFollow> followPage = this.page(new Page<>(page, pageSize),
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getUserId, userId)
                        .orderByDesc(UserFollow::getCreateTime));
        List<Long> userIds = followPage.getRecords().stream()
                .map(UserFollow::getFollowUserId).collect(Collectors.toList());
        if (userIds.isEmpty()) return new PageResult<>(0, page, pageSize, Collections.emptyList());
        List<User> users = userMapper.selectByIds(userIds);
        users.forEach(u -> { u.setUserPassword(null); u.setPhone(null); u.setEmail(null); });
        return new PageResult<>(followPage.getTotal(), page, pageSize, users);
    }

    @Override
    public PageResult<User> getFollowerList(Long userId, int page, int pageSize) {
        IPage<UserFollow> followPage = this.page(new Page<>(page, pageSize),
                new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowUserId, userId)
                        .orderByDesc(UserFollow::getCreateTime));
        List<Long> userIds = followPage.getRecords().stream()
                .map(UserFollow::getUserId).collect(Collectors.toList());
        if (userIds.isEmpty()) return new PageResult<>(0, page, pageSize, Collections.emptyList());
        List<User> users = userMapper.selectByIds(userIds);
        users.forEach(u -> { u.setUserPassword(null); u.setPhone(null); u.setEmail(null); });
        return new PageResult<>(followPage.getTotal(), page, pageSize, users);
    }

    @Override
    public long getFollowingCount(Long userId) {
        return this.count(new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getUserId, userId));
    }

    @Override
    public long getFollowerCount(Long userId) {
        return this.count(new LambdaQueryWrapper<UserFollow>().eq(UserFollow::getFollowUserId, userId));
    }
}
