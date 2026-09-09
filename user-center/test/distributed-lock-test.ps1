# 分布式锁测试脚本 (PowerShell)
# 使用方法: .\distributed-lock-test.ps1

$BASE_URL = "http://localhost:8080/api"
$TOKEN = ""

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   分布式锁功能测试" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# 1. 登录获取 token
Write-Host "[1] 登录获取 Token" -ForegroundColor Yellow
try {
    $loginResponse = Invoke-RestMethod -Uri "$BASE_URL/user/login" `
        -Method Post `
        -ContentType "application/json" `
        -Body '{"userAccount":"test","userPassword":"123456"}'

    if ($loginResponse.code -eq 0) {
        $TOKEN = $loginResponse.data.token
        Write-Host "登录成功! Token: $($TOKEN.Substring(0, 20))..." -ForegroundColor Green
    } else {
        Write-Host "登录失败: $($loginResponse.message)" -ForegroundColor Red
        Write-Host "请手动设置 TOKEN 变量" -ForegroundColor Red
        exit 1
    }
} catch {
    Write-Host "登录请求失败: $_" -ForegroundColor Red
    Write-Host "请确保项目已启动，并检查用户名密码" -ForegroundColor Red
    exit 1
}
Write-Host ""

# 通用测试函数
function Test-ConcurrentRequests {
    param(
        [string]$TestName,
        [string]$Method,
        [string]$Url,
        [string]$Body = "",
        [int]$Count = 3
    )

    Write-Host "[$TestName]" -ForegroundColor Yellow
    Write-Host "发送 $Count 个并发请求..." -ForegroundColor Gray

    $jobs = @()

    for ($i = 1; $i -le $Count; $i++) {
        $job = Start-Job -ScriptBlock {
            param($url, $method, $token, $body, $index)

            try {
                $headers = @{
                    "Authorization" = "Bearer $token"
                    "Content-Type" = "application/json"
                }

                $params = @{
                    Uri = $url
                    Method = $method
                    Headers = $headers
                }

                if ($body) {
                    $params.Body = $body
                }

                $response = Invoke-RestMethod @params
                return "请求 $index : code=$($response.code) message=$($response.message)"
            } catch {
                return "请求 $index : 错误 - $_"
            }
        } -ArgumentList "$BASE_URL$Url", $Method, $TOKEN, $Body, $i

        $jobs += $job
    }

    # 等待所有任务完成
    $jobs | ForEach-Object {
        $result = Receive-Job -Job $_ -Wait
        Write-Host $result
        Remove-Job -Job $_
    }

    Write-Host ""
}

# ============================================
# 测试用例
# ============================================

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   开始测试分布式锁" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

# 测试1: 笔记点赞
Write-Host "--------------------------------------------" -ForegroundColor Gray
Test-ConcurrentRequests -TestName "测试1: 笔记点赞 (并发3个)" `
    -Method "Post" -Url "/note/like/1" -Count 3

Start-Sleep -Seconds 1

# 测试2: 评论点赞
Write-Host "--------------------------------------------" -ForegroundColor Gray
Test-ConcurrentRequests -TestName "测试2: 评论点赞 (并发3个)" `
    -Method "Post" -Url "/comment/like/1" -Count 3

Start-Sleep -Seconds 1

# 测试3: 关注用户
Write-Host "--------------------------------------------" -ForegroundColor Gray
Test-ConcurrentRequests -TestName "测试3: 关注用户 (并发3个)" `
    -Method "Post" -Url "/follow/add" -Body '{"userId": 2}' -Count 3

Start-Sleep -Seconds 1

# 测试4: 添加评论
Write-Host "--------------------------------------------" -ForegroundColor Gray
Test-ConcurrentRequests -TestName "测试4: 添加评论 (并发3个)" `
    -Method "Post" -Url "/comment/add" -Body '{"noteId": 1, "content": "测试评论"}' -Count 3

Start-Sleep -Seconds 1

# 测试5: 发送私信
Write-Host "--------------------------------------------" -ForegroundColor Gray
Test-ConcurrentRequests -TestName "测试5: 发送私信 (并发3个)" `
    -Method "Post" -Url "/message/send" -Body '{"receiverId": 2, "content": "测试私信"}' -Count 3

Start-Sleep -Seconds 1

# 测试6: 加入星球
Write-Host "--------------------------------------------" -ForegroundColor Gray
Test-ConcurrentRequests -TestName "测试6: 加入星球 (并发3个)" `
    -Method "Post" -Url "/star/join/1" -Count 3

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   测试完成" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "如果看到以下情况，说明分布式锁工作正常:" -ForegroundColor Green
Write-Host "  1. 只有1个请求成功（返回 code=0）" -ForegroundColor Green
Write-Host "  2. 其他请求返回错误码 42900（操作过于频繁）" -ForegroundColor Green
Write-Host ""
Write-Host "提示: 锁会在 3-5 秒后自动过期，届时可以再次操作" -ForegroundColor Yellow
