#!/bin/bash

# 分布式锁测试脚本
# 使用方法: bash distributed-lock-test.sh

BASE_URL="http://localhost:8080/api"
TOKEN=""

# 颜色定义
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=========================================="
echo "   分布式锁功能测试"
echo "=========================================="
echo ""

# 1. 登录获取 token
echo -e "${YELLOW}1. 登录获取 Token${NC}"
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/user/login" \
  -H "Content-Type: application/json" \
  -d '{"userAccount":"test","userPassword":"123456"}')

echo "登录响应: $LOGIN_RESPONSE"

# 从响应中提取 token（需要 jq 或手动解析）
if command -v jq &> /dev/null; then
    TOKEN=$(echo $LOGIN_RESPONSE | jq -r '.data.token // empty')
fi

if [ -z "$TOKEN" ]; then
    echo -e "${RED}登录失败或无法解析 token，请手动设置 TOKEN 变量${NC}"
    echo "请在脚本中手动设置 TOKEN='your_token_here'"
    exit 1
fi

echo -e "${GREEN}登录成功! Token: ${TOKEN:0:20}...${NC}"
echo ""

# 测试函数：并发请求
test_concurrent() {
    local test_name="$1"
    local method="$2"
    local url="$3"
    local data="$4"
    local count="${5:-3}"

    echo -e "${YELLOW}测试: $test_name${NC}"
    echo "发送 $count 个并发请求..."

    for i in $(seq 1 $count); do
        (
            RESPONSE=$(curl -s -X $method "$BASE_URL$url" \
              -H "Content-Type: application/json" \
              -H "Authorization: Bearer $TOKEN" \
              -d "$data" \
              -w "\n%{http_code}")
            HTTP_CODE=$(echo "$RESPONSE" | tail -1)
            BODY=$(echo "$RESPONSE" | head -1)
            echo "请求 $i: HTTP $HTTP_CODE - $BODY"
        ) &
    done

    wait
    echo ""
}

# ============================================
# 测试用例
# ============================================

echo "=========================================="
echo "   开始测试分布式锁"
echo "=========================================="
echo ""

# 测试1: 笔记点赞（并发）
echo -e "${YELLOW}[测试1] 笔记点赞 - 并发3个请求${NC}"
echo "预期: 只有1个成功，其他返回 42900 错误"
echo "---"
for i in 1 2 3; do
    (
        RESPONSE=$(curl -s -X POST "$BASE_URL/note/like/1" \
          -H "Authorization: Bearer $TOKEN" \
          -w "\n%{http_code}")
        HTTP_CODE=$(echo "$RESPONSE" | tail -1)
        BODY=$(echo "$RESPONSE" | head -1)
        echo "请求 $i: HTTP $HTTP_CODE - $BODY"
    ) &
done
wait
echo ""

# 测试2: 评论点赞（并发）
echo -e "${YELLOW}[测试2] 评论点赞 - 并发3个请求${NC}"
echo "预期: 只有1个成功，其他返回 42900 错误"
echo "---"
for i in 1 2 3; do
    (
        RESPONSE=$(curl -s -X POST "$BASE_URL/comment/like/1" \
          -H "Authorization: Bearer $TOKEN" \
          -w "\n%{http_code}")
        HTTP_CODE=$(echo "$RESPONSE" | tail -1)
        BODY=$(echo "$RESPONSE" | head -1)
        echo "请求 $i: HTTP $HTTP_CODE - $BODY"
    ) &
done
wait
echo ""

# 测试3: 关注用户（并发）
echo -e "${YELLOW}[测试3] 关注用户 - 并发3个请求${NC}"
echo "预期: 只有1个成功，其他返回 42900 错误"
echo "---"
for i in 1 2 3; do
    (
        RESPONSE=$(curl -s -X POST "$BASE_URL/follow/add" \
          -H "Content-Type: application/json" \
          -H "Authorization: Bearer $TOKEN" \
          -d '{"userId": 2}' \
          -w "\n%{http_code}")
        HTTP_CODE=$(echo "$RESPONSE" | tail -1)
        BODY=$(echo "$RESPONSE" | head -1)
        echo "请求 $i: HTTP $HTTP_CODE - $BODY"
    ) &
done
wait
echo ""

# 测试4: 添加评论（并发）
echo -e "${YELLOW}[测试4] 添加评论 - 并发3个请求${NC}"
echo "预期: 只有1个成功，其他返回 42900 错误"
echo "---"
for i in 1 2 3; do
    (
        RESPONSE=$(curl -s -X POST "$BASE_URL/comment/add" \
          -H "Content-Type: application/json" \
          -H "Authorization: Bearer $TOKEN" \
          -d "{\"noteId\": 1, \"content\": \"测试评论 $i\"}" \
          -w "\n%{http_code}")
        HTTP_CODE=$(echo "$RESPONSE" | tail -1)
        BODY=$(echo "$RESPONSE" | head -1)
        echo "请求 $i: HTTP $HTTP_CODE - $BODY"
    ) &
done
wait
echo ""

# 测试5: 发送私信（并发）
echo -e "${YELLOW}[测试5] 发送私信 - 并发3个请求${NC}"
echo "预期: 只有1个成功，其他返回 42900 错误"
echo "---"
for i in 1 2 3; do
    (
        RESPONSE=$(curl -s -X POST "$BASE_URL/message/send" \
          -H "Content-Type: application/json" \
          -H "Authorization: Bearer $TOKEN" \
          -d "{\"receiverId\": 2, \"content\": \"测试私信 $i\"}" \
          -w "\n%{http_code}")
        HTTP_CODE=$(echo "$RESPONSE" | tail -1)
        BODY=$(echo "$RESPONSE" | head -1)
        echo "请求 $i: HTTP $HTTP_CODE - $BODY"
    ) &
done
wait
echo ""

# 测试6: 加入星球（并发）
echo -e "${YELLOW}[测试6] 加入星球 - 并发3个请求${NC}"
echo "预期: 只有1个成功，其他返回 42900 错误"
echo "---"
for i in 1 2 3; do
    (
        RESPONSE=$(curl -s -X POST "$BASE_URL/star/join/1" \
          -H "Authorization: Bearer $TOKEN" \
          -w "\n%{http_code}")
        HTTP_CODE=$(echo "$RESPONSE" | tail -1)
        BODY=$(echo "$RESPONSE" | head -1)
        echo "请求 $i: HTTP $HTTP_CODE - $BODY"
    ) &
done
wait
echo ""

echo "=========================================="
echo "   测试完成"
echo "=========================================="
echo ""
echo -e "${GREEN}如果看到以下情况，说明分布式锁工作正常:${NC}"
echo "  1. 只有1个请求成功（返回业务成功）"
echo "  2. 其他请求返回错误码 42900（操作过于频繁）"
echo ""
echo -e "${YELLOW}提示: 锁会在 3-5 秒后自动过期，届时可以再次操作${NC}"
