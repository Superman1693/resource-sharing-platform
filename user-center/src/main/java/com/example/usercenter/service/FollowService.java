package com.example.usercenter.service;

import com.example.usercenter.common.PageResult;
import com.example.usercenter.model.domain.User;

public interface FollowService {
    void follow(Long userId, Long followUserId);
    void unfollow(Long userId, Long followUserId);
    boolean isFollowing(Long userId, Long targetUserId);
    PageResult<User> getFollowingList(Long userId, int page, int pageSize);
    PageResult<User> getFollowerList(Long userId, int page, int pageSize);
    long getFollowingCount(Long userId);
    long getFollowerCount(Long userId);
}
