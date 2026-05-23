#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ETL Expression Engine 自动化集成测试脚本
执行完整的HTTP请求和会话管理验证用例

使用方法:
    python integration_test.py [--host HOST] [--port PORT] [--verbose]

参数:
    --host      服务器地址 (默认: localhost)
    --port      服务器端口 (默认: 8080)
    --verbose   显示详细输出

示例:
    python integration_test.py --host localhost --port 8080 --verbose
"""

import argparse
import json
import sys
import time
from datetime import datetime
from typing import Dict, List, Optional, Tuple, Any
import urllib.request
import urllib.error
import ssl
import concurrent.futures


class Colors:
    """终端颜色常量"""
    RED = '\033[91m'
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    MAGENTA = '\033[95m'
    CYAN = '\033[96m'
    WHITE = '\033[97m'
    RESET = '\033[0m'
    BOLD = '\033[1m'


class TestResult:
    """测试结果"""
    def __init__(self, name: str, category: str):
        self.name = name
        self.category = category
        self.passed = False
        self.message = ""
        self.duration_ms = 0
        self.response = None
        self.error = None
    
    def to_dict(self) -> Dict:
        return {
            "name": self.name,
            "category": self.category,
            "passed": self.passed,
            "message": self.message,
            "duration_ms": self.duration_ms,
            "error": self.error
        }


class IntegrationTestRunner:
    """集成测试运行器"""
    
    def __init__(self, host: str, port: int, verbose: bool = False):
        self.host = host
        self.port = port
        self.verbose = verbose
        self.base_url = f"http://{host}:{port}"
        self.session_id: Optional[str] = None
        self.results: List[TestResult] = []
        self.test_counts = {
            "total": 0,
            "passed": 0,
            "failed": 0,
            "skipped": 0
        }
        
        ssl_context = ssl.create_default_context()
        ssl_context.check_hostname = False
        ssl_context.verify_mode = ssl.CERT_NONE
        
        self.ssl_context = ssl_context
    
    def log(self, message: str, level: str = "INFO"):
        """输出日志"""
        timestamp = datetime.now().strftime("%H:%M:%S.%f")[:-3]
        color = {
            "INFO": Colors.BLUE,
            "SUCCESS": Colors.GREEN,
            "ERROR": Colors.RED,
            "WARN": Colors.YELLOW,
            "HEADER": Colors.CYAN + Colors.BOLD
        }.get(level, Colors.WHITE)
        
        print(f"{color}[{timestamp}] [{level}] {message}{Colors.RESET}")
    
    def http_request(self, method: str, endpoint: str, 
                     data: Optional[str] = None,
                     headers: Optional[Dict[str, str]] = None) -> Tuple[int, Any]:
        """发送HTTP请求"""
        url = f"{self.base_url}{endpoint}"
        
        req_headers = {"Content-Type": "text/plain"}
        if headers:
            req_headers.update(headers)
        
        if self.session_id and "X-Session-Id" not in req_headers:
            req_headers["X-Session-Id"] = self.session_id
        
        req = urllib.request.Request(url, data=data.encode() if data else None, 
                                      headers=req_headers, method=method)
        
        try:
            with urllib.request.urlopen(req, timeout=60) as response:
                return response.status, json.loads(response.read().decode())
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read().decode()) if e.fp else {}
        except urllib.error.URLError as e:
            return 0, {"error": str(e)}
        except Exception as e:
            return 0, {"error": str(e)}
    
    def run_test(self, test_func) -> TestResult:
        """运行单个测试"""
        result = test_func()
        self.results.append(result)
        
        self.test_counts["total"] += 1
        if result.passed:
            self.test_counts["passed"] += 1
            self.log(f"✓ {result.name} ({result.duration_ms}ms)", "SUCCESS")
        else:
            self.test_counts["failed"] += 1
            self.log(f"✗ {result.name}: {result.message}", "ERROR")
        
        return result
    
    def test_health_check(self) -> TestResult:
        """测试服务健康检查"""
        result = TestResult("服务健康检查", "基础验证")
        start_time = time.time()
        
        try:
            status, response = self.http_request("GET", "/etl/expression/session/new")
            result.duration_ms = int((time.time() - start_time) * 1000)
            
            if status == 200 and response.get("success"):
                result.passed = True
                result.message = "服务正常运行"
                result.response = response
            else:
                result.message = f"服务响应异常: status={status}"
                result.response = response
        except Exception as e:
            result.duration_ms = int((time.time() - start_time) * 1000)
            result.error = str(e)
            result.message = f"连接失败: {e}"
        
        return result
    
    def test_create_session(self) -> TestResult:
        """测试创建会话"""
        result = TestResult("创建会话", "会话管理")
        start_time = time.time()
        
        try:
            status, response = self.http_request("POST", "/etl/expression/session/new")
            result.duration_ms = int((time.time() - start_time) * 1000)
            
            if status == 200 and response.get("success"):
                self.session_id = response.get("sessionId")
                result.passed = True
                result.message = f"会话创建成功: {self.session_id}"
                result.response = response
            else:
                result.message = f"会话创建失败: {response}"
                result.response = response
        except Exception as e:
            result.duration_ms = int((time.time() - start_time) * 1000)
            result.error = str(e)
            result.message = f"请求异常: {e}"
        
        return result
    
    def test_get_session_info(self) -> TestResult:
        """测试获取会话信息"""
        result = TestResult("获取会话信息", "会话管理")
        start_time = time.time()
        
        if not self.session_id:
            result.message = "跳过: 无可用会话"
            return result
        
        try:
            status, response = self.http_request("GET", f"/etl/expression/session/{self.session_id}")
            result.duration_ms = int((time.time() - start_time) * 1000)
            
            if status == 200 and response.get("success"):
                result.passed = True
                result.message = f"会话信息获取成功, 变量数: {response.get('variableCount', 0)}"
                result.response = response
            else:
                result.message = f"获取会话信息失败: {response}"
                result.response = response
        except Exception as e:
            result.duration_ms = int((time.time() - start_time) * 1000)
            result.error = str(e)
            result.message = f"请求异常: {e}"
        
        return result
    
    def test_basic_expression(self) -> TestResult:
        """测试基础表达式执行"""
        result = TestResult("基础表达式执行 (1+2+3)", "表达式执行")
        start_time = time.time()
        
        try:
            status, response = self.http_request("POST", "/etl/expression/execute", 
                                                  data="1 + 2 + 3")
            result.duration_ms = int((time.time() - start_time) * 1000)
            
            if status == 200 and response.get("success") and response.get("finalResult") == 6:
                result.passed = True
                result.message = f"表达式执行成功, 结果: {response.get('finalResult')}"
                result.response = response
            else:
                result.message = f"表达式执行失败: {response}"
                result.response = response
        except Exception as e:
            result.duration_ms = int((time.time() - start_time) * 1000)
            result.error = str(e)
            result.message = f"请求异常: {e}"
        
        return result
    
    def test_variable_assignment(self) -> TestResult:
        """测试变量赋值"""
        result = TestResult("变量赋值 (a=100; b=a+50)", "表达式执行")
        start_time = time.time()
        
        try:
            status, response = self.http_request("POST", "/etl/expression/execute",
                                                  data="a = 100; b = a + 50")
            result.duration_ms = int((time.time() - start_time) * 1000)
            
            if status == 200 and response.get("success"):
                context_vars = response.get("contextVars", {})
                if context_vars.get("a") == 100 and context_vars.get("b") == 150:
                    result.passed = True
                    result.message = f"变量赋值成功: a={context_vars.get('a')}, b={context_vars.get('b')}"
                else:
                    result.message = f"变量值不正确: {context_vars}"
                result.response = response
            else:
                result.message = f"表达式执行失败: {response}"
                result.response = response
        except Exception as e:
            result.duration_ms = int((time.time() - start_time) * 1000)
            result.error = str(e)
            result.message = f"请求异常: {e}"
        
        return result
    
    def test_conditional_expression(self) -> TestResult:
        """测试三元条件表达式"""
        result = TestResult("三元条件表达式", "表达式执行")
        start_time = time.time()
        
        try:
            status, response = self.http_request("POST", "/etl/expression/execute",
                                                  data="score = 85; score >= 60 ? 'pass' : 'fail'")
            result.duration_ms = int((time.time() - start_time) * 1000)
            
            if status == 200 and response.get("success") and response.get("finalResult") == "pass":
                result.passed = True
                result.message = f"条件表达式执行成功, 结果: {response.get('finalResult')}"
                result.response = response
            else:
                result.message = f"条件表达式执行失败: {response}"
                result.response = response
        except Exception as e:
            result.duration_ms = int((time.time() - start_time) * 1000)
            result.error = str(e)
            result.message = f"请求异常: {e}"
        
        return result
    
    def test_string_operations(self) -> TestResult:
        """测试字符串操作"""
        result = TestResult("字符串操作", "表达式执行")
        start_time = time.time()
        
        try:
            status, response = self.http_request("POST", "/etl/expression/execute",
                                                  data="name = 'ETL'; 'Hello, ' + name")
            result.duration_ms = int((time.time() - start_time) * 1000)
            
            if status == 200 and response.get("success") and response.get("finalResult") == "Hello, ETL":
                result.passed = True
                result.message = f"字符串操作成功: {response.get('finalResult')}"
                result.response = response
            else:
                result.message = f"字符串操作失败: {response}"
                result.response = response
        except Exception as e:
            result.duration_ms = int((time.time() - start_time) * 1000)
            result.error = str(e)
            result.message = f"请求异常: {e}"
        
        return result
    
    def test_sql_query(self) -> TestResult:
        """测试SQL查询"""
        result = TestResult("SQL查询 (SELECT count(*) FROM etl_config)", "SQL功能")
        start_time = time.time()
        
        try:
            status, response = self.http_request("POST", "/etl/expression/execute",
                                                  data='sqlValue("SELECT count(*) FROM etl_config")')
            result.duration_ms = int((time.time() - start_time) * 1000)
            
            if status == 200 and response.get("success"):
                result.passed = True
                result.message = f"SQL查询成功, 结果: {response.get('finalResult')}"
                result.response = response
            else:
                result.message = f"SQL查询失败: {response}"
                result.response = response
        except Exception as e:
            result.duration_ms = int((time.time() - start_time) * 1000)
            result.error = str(e)
            result.message = f"请求异常: {e}"
        
        return result
    
    def test_http_get_request(self) -> TestResult:
        """测试HTTP GET请求"""
        result = TestResult("HTTP GET请求", "HTTP功能")
        start_time = time.time()
        
        try:
            expression = "http('https://httpbin.org/get').queryVariable('test', 'integration').get().statusCode()"
            status, response = self.http_request("POST", "/etl/expression/execute", data=expression)
            result.duration_ms = int((time.time() - start_time) * 1000)
            
            if status == 200 and response.get("success") and response.get("finalResult") == 200:
                result.passed = True
                result.message = f"HTTP GET请求成功, 状态码: {response.get('finalResult')}"
                result.response = response
            else:
                result.message = f"HTTP GET请求失败: {response}"
                result.response = response
        except Exception as e:
            result.duration_ms = int((time.time() - start_time) * 1000)
            result.error = str(e)
            result.message = f"请求异常: {e}"
        
        return result
    
    def test_http_post_json(self) -> TestResult:
        """测试HTTP POST JSON请求"""
        result = TestResult("HTTP POST JSON请求", "HTTP功能")
        start_time = time.time()
        
        try:
            expression = '''
            response = http('https://httpbin.org/post')
                .contentType('application/json')
                .bodyJson({'name': 'IntegrationTest', 'timestamp': 123456})
                .post();
            response.statusCode()
            '''
            status, response = self.http_request("POST", "/etl/expression/execute", data=expression)
            result.duration_ms = int((time.time() - start_time) * 1000)
            
            if status == 200 and response.get("success") and response.get("finalResult") == 200:
                result.passed = True
                result.message = f"HTTP POST请求成功, 状态码: {response.get('finalResult')}"
                result.response = response
            else:
                result.message = f"HTTP POST请求失败: {response}"
                result.response = response
        except Exception as e:
            result.duration_ms = int((time.time() - start_time) * 1000)
            result.error = str(e)
            result.message = f"请求异常: {e}"
        
        return result
    
    def test_http_basic_auth(self) -> TestResult:
        """测试HTTP Basic认证"""
        result = TestResult("HTTP Basic认证", "HTTP功能")
        start_time = time.time()
        
        try:
            expression = "http('https://httpbin.org/basic-auth/test/pass').basicAuth('test', 'pass').get().statusCode()"
            status, response = self.http_request("POST", "/etl/expression/execute", data=expression)
            result.duration_ms = int((time.time() - start_time) * 1000)
            
            if status == 200 and response.get("success") and response.get("finalResult") == 200:
                result.passed = True
                result.message = f"Basic认证成功, 状态码: {response.get('finalResult')}"
                result.response = response
            else:
                result.message = f"Basic认证失败: {response}"
                result.response = response
        except Exception as e:
            result.duration_ms = int((time.time() - start_time) * 1000)
            result.error = str(e)
            result.message = f"请求异常: {e}"
        
        return result
    
    def test_http_timeout(self) -> TestResult:
        """测试HTTP超时设置"""
        result = TestResult("HTTP超时设置", "HTTP功能")
        start_time = time.time()
        
        try:
            expression = "http('https://httpbin.org/delay/1').timeout(5000).get().statusCode()"
            status, response = self.http_request("POST", "/etl/expression/execute", data=expression)
            result.duration_ms = int((time.time() - start_time) * 1000)
            
            if status == 200 and response.get("success"):
                result.passed = True
                result.message = f"超时设置测试通过, 状态码: {response.get('finalResult')}"
                result.response = response
            else:
                result.message = f"超时设置测试失败: {response}"
                result.response = response
        except Exception as e:
            result.duration_ms = int((time.time() - start_time) * 1000)
            result.error = str(e)
            result.message = f"请求异常: {e}"
        
        return result
    
    def test_error_handling_empty_expression(self) -> TestResult:
        """测试错误处理 - 空表达式"""
        result = TestResult("错误处理 - 空表达式", "错误处理")
        start_time = time.time()
        
        try:
            status, response = self.http_request("POST", "/etl/expression/execute", data="")
            result.duration_ms = int((time.time() - start_time) * 1000)
            
            if status == 200 and not response.get("success"):
                result.passed = True
                result.message = f"空表达式正确拦截: {response.get('errorMsg', '')[:50]}"
                result.response = response
            else:
                result.message = f"空表达式未正确处理: {response}"
                result.response = response
        except Exception as e:
            result.duration_ms = int((time.time() - start_time) * 1000)
            result.error = str(e)
            result.message = f"请求异常: {e}"
        
        return result
    
    def test_error_handling_unsafe_expression(self) -> TestResult:
        """测试错误处理 - 不安全表达式"""
        result = TestResult("错误处理 - 不安全表达式", "错误处理")
        start_time = time.time()
        
        try:
            status, response = self.http_request("POST", "/etl/expression/execute", 
                                                  data="import java.lang.Runtime")
            result.duration_ms = int((time.time() - start_time) * 1000)
            
            if status == 200 and not response.get("success") and "禁止" in response.get("errorMsg", ""):
                result.passed = True
                result.message = f"不安全表达式正确拦截"
                result.response = response
            else:
                result.message = f"不安全表达式未正确处理: {response}"
                result.response = response
        except Exception as e:
            result.duration_ms = int((time.time() - start_time) * 1000)
            result.error = str(e)
            result.message = f"请求异常: {e}"
        
        return result
    
    def test_error_handling_invalid_sql(self) -> TestResult:
        """测试错误处理 - 无效SQL"""
        result = TestResult("错误处理 - 无效SQL", "错误处理")
        start_time = time.time()
        
        try:
            status, response = self.http_request("POST", "/etl/expression/execute",
                                                  data='sql("INSERT INTO test VALUES (1)")')
            result.duration_ms = int((time.time() - start_time) * 1000)
            
            if status == 200 and not response.get("success"):
                result.passed = True
                result.message = f"无效SQL正确拦截"
                result.response = response
            else:
                result.message = f"无效SQL未正确处理: {response}"
                result.response = response
        except Exception as e:
            result.duration_ms = int((time.time() - start_time) * 1000)
            result.error = str(e)
            result.message = f"请求异常: {e}"
        
        return result
    
    def test_performance_complex_test(self) -> TestResult:
        """测试性能 - 复杂测试套件"""
        result = TestResult("性能测试 - 复杂测试套件", "性能测试")
        start_time = time.time()
        
        try:
            status, response = self.http_request("POST", "/etl/performance/complex-test")
            result.duration_ms = int((time.time() - start_time) * 1000)
            
            if status == 200:
                tests = response.get("tests", [])
                passed_count = sum(1 for t in tests if t.get("success"))
                result.passed = True
                result.message = f"复杂测试完成: {passed_count}/{len(tests)} 通过, 总耗时: {response.get('totalDurationMs')}ms"
                result.response = {"tests": tests, "totalDurationMs": response.get("totalDurationMs")}
            else:
                result.message = f"性能测试失败: {response}"
                result.response = response
        except Exception as e:
            result.duration_ms = int((time.time() - start_time) * 1000)
            result.error = str(e)
            result.message = f"请求异常: {e}"
        
        return result
    
    def test_stress_test(self) -> TestResult:
        """测试压力测试"""
        result = TestResult("压力测试 (10并发 x 5请求)", "性能测试")
        start_time = time.time()
        
        try:
            status, response = self.http_request("POST", 
                                                  "/etl/performance/stress-test?concurrentUsers=10&requestsPerUser=5")
            result.duration_ms = int((time.time() - start_time) * 1000)
            
            if status == 200:
                throughput = response.get("throughput", {}).get("requestsPerSecond", "0")
                success_rate = response.get("results", {}).get("successRate", "0%")
                result.passed = True
                result.message = f"压力测试完成: {success_rate} 成功率, {throughput} req/s"
                result.response = response
            else:
                result.message = f"压力测试失败: {response}"
                result.response = response
        except Exception as e:
            result.duration_ms = int((time.time() - start_time) * 1000)
            result.error = str(e)
            result.message = f"请求异常: {e}"
        
        return result
    
    def test_session_persistence(self) -> TestResult:
        """测试会话持久化"""
        result = TestResult("会话持久化验证", "会话管理")
        start_time = time.time()
        
        try:
            expressions = [
                "step1 = 'init'",
                "step2 = step1 + '_done'",
                "step3 = step2 + '_final'"
            ]
            
            for i, expr in enumerate(expressions):
                status, response = self.http_request("POST", "/etl/expression/execute", data=expr)
                if status != 200 or not response.get("success"):
                    result.duration_ms = int((time.time() - start_time) * 1000)
                    result.message = f"第{i+1}步执行失败: {response}"
                    result.response = response
                    return result
            
            status, response = self.http_request("GET", f"/etl/expression/session/{self.session_id}")
            result.duration_ms = int((time.time() - start_time) * 1000)
            
            if status == 200:
                variables = response.get("variables", {})
                if "step1" in variables and "step2" in variables and "step3" in variables:
                    result.passed = True
                    result.message = f"会话持久化验证成功, 变量: {list(variables.keys())}"
                else:
                    result.message = f"变量未正确持久化: {variables}"
                result.response = response
            else:
                result.message = f"获取会话失败: {response}"
                result.response = response
        except Exception as e:
            result.duration_ms = int((time.time() - start_time) * 1000)
            result.error = str(e)
            result.message = f"请求异常: {e}"
        
        return result
    
    def test_destroy_session(self) -> TestResult:
        """测试销毁会话"""
        result = TestResult("销毁会话", "会话管理")
        start_time = time.time()
        
        if not self.session_id:
            result.message = "跳过: 无可用会话"
            return result
        
        try:
            status, response = self.http_request("DELETE", f"/etl/expression/session/{self.session_id}")
            result.duration_ms = int((time.time() - start_time) * 1000)
            
            if status == 200 and response.get("success"):
                result.passed = True
                result.message = f"会话销毁成功: {self.session_id}"
                result.response = response
                self.session_id = None
            else:
                result.message = f"会话销毁失败: {response}"
                result.response = response
        except Exception as e:
            result.duration_ms = int((time.time() - start_time) * 1000)
            result.error = str(e)
            result.message = f"请求异常: {e}"
        
        return result
    
    def run_all_tests(self):
        """运行所有测试"""
        self.log("=" * 60, "HEADER")
        self.log("ETL Expression Engine 集成测试", "HEADER")
        self.log(f"目标服务器: {self.base_url}", "HEADER")
        self.log(f"测试时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}", "HEADER")
        self.log("=" * 60, "HEADER")
        print()
        
        test_sequence = [
            ("基础验证", [
                self.test_health_check,
                self.test_create_session,
            ]),
            ("表达式执行", [
                self.test_basic_expression,
                self.test_variable_assignment,
                self.test_conditional_expression,
                self.test_string_operations,
            ]),
            ("SQL功能", [
                self.test_sql_query,
            ]),
            ("HTTP功能", [
                self.test_http_get_request,
                self.test_http_post_json,
                self.test_http_basic_auth,
                self.test_http_timeout,
            ]),
            ("错误处理", [
                self.test_error_handling_empty_expression,
                self.test_error_handling_unsafe_expression,
                self.test_error_handling_invalid_sql,
            ]),
            ("会话管理", [
                self.test_get_session_info,
                self.test_session_persistence,
            ]),
            ("性能测试", [
                self.test_performance_complex_test,
                self.test_stress_test,
            ]),
            ("清理验证", [
                self.test_destroy_session,
            ]),
        ]
        
        for category, tests in test_sequence:
            self.log(f"\n>>> {category}", "HEADER")
            for test_func in tests:
                self.run_test(test_func)
        
        self.print_summary()
    
    def print_summary(self):
        """打印测试汇总"""
        print()
        self.log("=" * 60, "HEADER")
        self.log("测试汇总报告", "HEADER")
        self.log("=" * 60, "HEADER")
        print()
        
        total = self.test_counts["total"]
        passed = self.test_counts["passed"]
        failed = self.test_counts["failed"]
        
        print(f"  总测试数: {total}")
        print(f"  {Colors.GREEN}通过: {passed}{Colors.RESET}")
        print(f"  {Colors.RED}失败: {failed}{Colors.RESET}")
        print(f"  通过率: {passed/total*100:.1f}%" if total > 0 else "  通过率: N/A")
        print()
        
        failed_tests = [r for r in self.results if not r.passed]
        if failed_tests:
            self.log("失败测试详情:", "ERROR")
            for test in failed_tests:
                print(f"  - {test.name}: {test.message}")
            print()
        
        total_duration = sum(r.duration_ms for r in self.results)
        avg_duration = total_duration / total if total > 0 else 0
        
        print(f"  总耗时: {total_duration}ms")
        print(f"  平均耗时: {avg_duration:.1f}ms")
        print()
        
        self.log("=" * 60, "HEADER")
        
        if failed == 0:
            self.log("✓ 所有测试通过!", "SUCCESS")
        else:
            self.log(f"✗ {failed} 个测试失败", "ERROR")
    
    def export_results(self, filename: str = "test_results.json"):
        """导出测试结果到JSON文件"""
        output = {
            "timestamp": datetime.now().isoformat(),
            "server": self.base_url,
            "summary": self.test_counts,
            "results": [r.to_dict() for r in self.results]
        }
        
        with open(filename, "w", encoding="utf-8") as f:
            json.dump(output, f, ensure_ascii=False, indent=2)
        
        self.log(f"测试结果已导出到: {filename}", "INFO")


def main():
    parser = argparse.ArgumentParser(description="ETL Expression Engine 集成测试")
    parser.add_argument("--host", default="localhost", help="服务器地址")
    parser.add_argument("--port", type=int, default=8080, help="服务器端口")
    parser.add_argument("--verbose", "-v", action="store_true", help="显示详细输出")
    parser.add_argument("--export", "-e", action="store_true", help="导出测试结果到JSON")
    
    args = parser.parse_args()
    
    runner = IntegrationTestRunner(args.host, args.port, args.verbose)
    
    try:
        runner.run_all_tests()
        
        if args.export:
            runner.export_results()
        
        sys.exit(0 if runner.test_counts["failed"] == 0 else 1)
    
    except KeyboardInterrupt:
        print("\n测试被中断")
        sys.exit(130)
    except Exception as e:
        print(f"\n测试运行出错: {e}")
        sys.exit(1)


if __name__ == "__main__":
    main()
