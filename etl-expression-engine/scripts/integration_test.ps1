# ETL Expression Engine 自动化集成测试脚本 (PowerShell版本)
# 执行完整的HTTP请求和会话管理验证用例

param(
    [string]$Host = "localhost",
    [int]$Port = 8080,
    [switch]$Verbose,
    [switch]$Export
)

$BaseUrl = "http://${Host}:${Port}"
$SessionId = $null
$Results = @()
$TestCounts = @{
    Total   = 0
    Passed  = 0
    Failed  = 0
    Skipped = 0
}

function Write-Log {
    param(
        [string]$Message,
        [string]$Level = "INFO"
    )
    
    $timestamp = Get-Date -Format "HH:mm:ss.fff"
    $color = switch ($Level) {
        "INFO"    { "Blue" }
        "SUCCESS" { "Green" }
        "ERROR"   { "Red" }
        "WARN"    { "Yellow" }
        "HEADER"  { "Cyan" }
        default   { "White" }
    }
    
    Write-Host "[$timestamp] [$Level] " -NoNewline -ForegroundColor $color
    Write-Host $Message
}

function Invoke-ApiRequest {
    param(
        [string]$Method,
        [string]$Endpoint,
        [string]$Body = $null,
        [hashtable]$Headers = @{}
    )
    
    $url = "${BaseUrl}${Endpoint}"
    $contentType = "text/plain"
    
    $requestHeaders = @{
        "Content-Type" = $contentType
    }
    
    if ($SessionId) {
        $requestHeaders["X-Session-Id"] = $SessionId
    }
    
    foreach ($key in $Headers.Keys) {
        $requestHeaders[$key] = $Headers[$key]
    }
    
    try {
        if ($Body) {
            $response = Invoke-RestMethod -Uri $url -Method $Method -Body $Body -Headers $requestHeaders -TimeoutSec 60
        } else {
            $response = Invoke-RestMethod -Uri $url -Method $Method -Headers $requestHeaders -TimeoutSec 60
        }
        return @{ Status = 200; Response = $response }
    }
    catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode) {
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $reader.BaseStream.Position = 0
                $responseBody = $reader.ReadToEnd() | ConvertFrom-Json
                return @{ Status = $statusCode; Response = $responseBody }
            }
            catch {
                return @{ Status = $statusCode; Response = @{ error = $_.Exception.Message } }
            }
        }
        return @{ Status = 0; Response = @{ error = $_.Exception.Message } }
    }
}

function Test-Case {
    param(
        [string]$Name,
        [string]$Category,
        [scriptblock]$TestScript
    )
    
    $startTime = Get-Date
    $result = @{
        Name     = $Name
        Category = $Category
        Passed   = $false
        Message  = ""
        Duration = 0
    }
    
    try {
        $testResult = & $TestScript
        $result.Passed = $testResult.Passed
        $result.Message = $testResult.Message
        if ($testResult.SessionId) {
            $script:SessionId = $testResult.SessionId
        }
    }
    catch {
        $result.Message = "异常: $_"
    }
    
    $result.Duration = [int]((Get-Date) - $startTime).TotalMilliseconds
    $script:Results += $result
    
    $script:TestCounts.Total++
    if ($result.Passed) {
        $script:TestCounts.Passed++
        Write-Log "✓ $Name ($($result.Duration)ms)" "SUCCESS"
    }
    else {
        $script:TestCounts.Failed++
        Write-Log "✗ $Name : $($result.Message)" "ERROR"
    }
}

# 测试用例

function Test-HealthCheck {
    $result = Invoke-ApiRequest -Method "POST" -Endpoint "/etl/expression/session/new"
    
    if ($result.Status -eq 200 -and $result.Response.success) {
        return @{
            Passed    = $true
            Message   = "服务正常运行"
            SessionId = $result.Response.sessionId
        }
    }
    return @{
        Passed  = $false
        Message = "服务响应异常: status=$($result.Status)"
    }
}

function Test-BasicExpression {
    $result = Invoke-ApiRequest -Method "POST" -Endpoint "/etl/expression/execute" -Body "1 + 2 + 3"
    
    if ($result.Status -eq 200 -and $result.Response.success -and $result.Response.finalResult -eq 6) {
        return @{
            Passed  = $true
            Message = "结果: $($result.Response.finalResult)"
        }
    }
    return @{
        Passed  = $false
        Message = "表达式执行失败"
    }
}

function Test-VariableAssignment {
    $result = Invoke-ApiRequest -Method "POST" -Endpoint "/etl/expression/execute" -Body "a = 100; b = a + 50"
    
    if ($result.Status -eq 200 -and $result.Response.success) {
        $vars = $result.Response.contextVars
        if ($vars.a -eq 100 -and $vars.b -eq 150) {
            return @{
                Passed  = $true
                Message = "a=$($vars.a), b=$($vars.b)"
            }
        }
    }
    return @{
        Passed  = $false
        Message = "变量赋值失败"
    }
}

function Test-ConditionalExpression {
    $result = Invoke-ApiRequest -Method "POST" -Endpoint "/etl/expression/execute" -Body "score = 85; score >= 60 ? 'pass' : 'fail'"
    
    if ($result.Status -eq 200 -and $result.Response.success -and $result.Response.finalResult -eq "pass") {
        return @{
            Passed  = $true
            Message = "结果: $($result.Response.finalResult)"
        }
    }
    return @{
        Passed  = $false
        Message = "条件表达式失败"
    }
}

function Test-SqlQuery {
    $result = Invoke-ApiRequest -Method "POST" -Endpoint "/etl/expression/execute" -Body 'sqlValue("SELECT count(*) FROM etl_config")'
    
    if ($result.Status -eq 200 -and $result.Response.success) {
        return @{
            Passed  = $true
            Message = "结果: $($result.Response.finalResult)"
        }
    }
    return @{
        Passed  = $false
        Message = "SQL查询失败"
    }
}

function Test-HttpGet {
    $result = Invoke-ApiRequest -Method "POST" -Endpoint "/etl/expression/execute" -Body "http('https://httpbin.org/get').queryVariable('test', 'value').get().statusCode()"
    
    if ($result.Status -eq 200 -and $result.Response.success -and $result.Response.finalResult -eq 200) {
        return @{
            Passed  = $true
            Message = "状态码: $($result.Response.finalResult)"
        }
    }
    return @{
        Passed  = $false
        Message = "HTTP GET失败"
    }
}

function Test-HttpPost {
    $body = "http('https://httpbin.org/post').contentType('application/json').bodyJson({'name':'test'}).post().statusCode()"
    $result = Invoke-ApiRequest -Method "POST" -Endpoint "/etl/expression/execute" -Body $body
    
    if ($result.Status -eq 200 -and $result.Response.success -and $result.Response.finalResult -eq 200) {
        return @{
            Passed  = $true
            Message = "状态码: $($result.Response.finalResult)"
        }
    }
    return @{
        Passed  = $false
        Message = "HTTP POST失败"
    }
}

function Test-BasicAuth {
    $result = Invoke-ApiRequest -Method "POST" -Endpoint "/etl/expression/execute" -Body "http('https://httpbin.org/basic-auth/user/pass').basicAuth('user', 'pass').get().statusCode()"
    
    if ($result.Status -eq 200 -and $result.Response.success -and $result.Response.finalResult -eq 200) {
        return @{
            Passed  = $true
            Message = "认证成功"
        }
    }
    return @{
        Passed  = $false
        Message = "Basic认证失败"
    }
}

function Test-ErrorHandling {
    $result = Invoke-ApiRequest -Method "POST" -Endpoint "/etl/expression/execute" -Body ""
    
    if ($result.Status -eq 200 -and -not $result.Response.success) {
        return @{
            Passed  = $true
            Message = "空表达式正确拦截"
        }
    }
    return @{
        Passed  = $false
        Message = "错误处理失败"
    }
}

function Test-UnsafeExpression {
    $result = Invoke-ApiRequest -Method "POST" -Endpoint "/etl/expression/execute" -Body "import java.lang.Runtime"
    
    if ($result.Status -eq 200 -and -not $result.Response.success) {
        return @{
            Passed  = $true
            Message = "不安全表达式正确拦截"
        }
    }
    return @{
        Passed  = $false
        Message = "安全检查失败"
    }
}

function Test-GetSession {
    $result = Invoke-ApiRequest -Method "GET" -Endpoint "/etl/expression/session/$SessionId"
    
    if ($result.Status -eq 200 -and $result.Response.success) {
        return @{
            Passed  = $true
            Message = "变量数: $($result.Response.variableCount)"
        }
    }
    return @{
        Passed  = $false
        Message = "获取会话失败"
    }
}

function Test-DestroySession {
    $result = Invoke-ApiRequest -Method "DELETE" -Endpoint "/etl/expression/session/$SessionId"
    
    if ($result.Status -eq 200 -and $result.Response.success) {
        $script:SessionId = $null
        return @{
            Passed  = $true
            Message = "会话已销毁"
        }
    }
    return @{
        Passed  = $false
        Message = "销毁会话失败"
    }
}

function Test-Performance {
    $result = Invoke-ApiRequest -Method "POST" -Endpoint "/etl/performance/complex-test"
    
    if ($result.Status -eq 200) {
        $tests = $result.Response.tests
        $passed = ($tests | Where-Object { $_.success }).Count
        return @{
            Passed  = $true
            Message = "通过: $passed / $($tests.Count)"
        }
    }
    return @{
        Passed  = $false
        Message = "性能测试失败"
    }
}

function Test-StressTest {
    $result = Invoke-ApiRequest -Method "POST" -Endpoint "/etl/performance/stress-test?concurrentUsers=5&requestsPerUser=3"
    
    if ($result.Status -eq 200) {
        $rate = $result.Response.results.successRate
        $tps = $result.Response.throughput.requestsPerSecond
        return @{
            Passed  = $true
            Message = "成功率: $rate, TPS: $tps"
        }
    }
    return @{
        Passed  = $false
        Message = "压力测试失败"
    }
}

# 主程序

Write-Log "============================================================" "HEADER"
Write-Log "ETL Expression Engine 集成测试" "HEADER"
Write-Log "目标服务器: $BaseUrl" "HEADER"
Write-Log "测试时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" "HEADER"
Write-Log "============================================================" "HEADER"
Write-Host ""

Write-Log ">>> 基础验证" "HEADER"
Test-Case -Name "服务健康检查" -Category "基础验证" -TestScript { Test-HealthCheck }

Write-Log ">>> 表达式执行" "HEADER"
Test-Case -Name "基础表达式" -Category "表达式执行" -TestScript { Test-BasicExpression }
Test-Case -Name "变量赋值" -Category "表达式执行" -TestScript { Test-VariableAssignment }
Test-Case -Name "条件表达式" -Category "表达式执行" -TestScript { Test-ConditionalExpression }

Write-Log ">>> SQL功能" "HEADER"
Test-Case -Name "SQL查询" -Category "SQL功能" -TestScript { Test-SqlQuery }

Write-Log ">>> HTTP功能" "HEADER"
Test-Case -Name "HTTP GET" -Category "HTTP功能" -TestScript { Test-HttpGet }
Test-Case -Name "HTTP POST" -Category "HTTP功能" -TestScript { Test-HttpPost }
Test-Case -Name "Basic认证" -Category "HTTP功能" -TestScript { Test-BasicAuth }

Write-Log ">>> 错误处理" "HEADER"
Test-Case -Name "空表达式处理" -Category "错误处理" -TestScript { Test-ErrorHandling }
Test-Case -Name "不安全表达式处理" -Category "错误处理" -TestScript { Test-UnsafeExpression }

Write-Log ">>> 会话管理" "HEADER"
Test-Case -Name "获取会话信息" -Category "会话管理" -TestScript { Test-GetSession }

Write-Log ">>> 性能测试" "HEADER"
Test-Case -Name "复杂测试套件" -Category "性能测试" -TestScript { Test-Performance }
Test-Case -Name "压力测试" -Category "性能测试" -TestScript { Test-StressTest }

Write-Log ">>> 清理验证" "HEADER"
Test-Case -Name "销毁会话" -Category "会话管理" -TestScript { Test-DestroySession }

# 汇总报告
Write-Host ""
Write-Log "============================================================" "HEADER"
Write-Log "测试汇总报告" "HEADER"
Write-Log "============================================================" "HEADER"
Write-Host ""

Write-Host "  总测试数: $($TestCounts.Total)"
Write-Host "  通过: $($TestCounts.Passed)" -ForegroundColor Green
Write-Host "  失败: $($TestCounts.Failed)" -ForegroundColor Red
$rate = if ($TestCounts.Total -gt 0) { "{0:N1}" -f ($TestCounts.Passed / $TestCounts.Total * 100) } else { "N/A" }
Write-Host "  通过率: $rate%"
Write-Host ""

$failedTests = $Results | Where-Object { -not $_.Passed }
if ($failedTests) {
    Write-Log "失败测试详情:" "ERROR"
    foreach ($test in $failedTests) {
        Write-Host "  - $($test.Name): $($test.Message)"
    }
    Write-Host ""
}

$totalDuration = ($Results | Measure-Object -Property Duration -Sum).Sum
Write-Host "  总耗时: ${totalDuration}ms"
Write-Host ""

Write-Log "============================================================" "HEADER"

if ($TestCounts.Failed -eq 0) {
    Write-Log "✓ 所有测试通过!" "SUCCESS"
    exit 0
}
else {
    Write-Log "✗ $($TestCounts.Failed) 个测试失败" "ERROR"
    exit 1
}
