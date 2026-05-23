package com.etl.engine.controller;

import com.etl.engine.http.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/etl/expression/test")
public class AsJavaTestController {
    
    private static final Logger logger = LoggerFactory.getLogger(AsJavaTestController.class);
    
    @GetMapping("/asjava/simple")
    public ResponseEntity<Map<String, Object>> testSimpleObject() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", "张三");
        result.put("age", 30);
        result.put("active", true);
        result.put("score", 95.5);
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/asjava/nested")
    public ResponseEntity<Map<String, Object>> testNestedObject() {
        Map<String, Object> address = new LinkedHashMap<>();
        address.put("province", "北京");
        address.put("city", "北京市");
        address.put("district", "海淀区");
        address.put("zipCode", "100000");
        
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("education", "本科");
        profile.put("major", "计算机科学");
        profile.put("graduationYear", 2020);
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", 1001);
        result.put("name", "李四");
        result.put("address", address);
        result.put("profile", profile);
        result.put("skills", Arrays.asList("Java", "Python", "SQL"));
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/asjava/list")
    public ResponseEntity<List<Map<String, Object>>> testListObject() {
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (int i = 1; i <= 5; i++) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", i);
            item.put("name", "Item-" + i);
            item.put("value", i * 100);
            item.put("active", i % 2 == 0);
            result.add(item);
        }
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/asjava/map")
    public ResponseEntity<Map<String, Object>> testMapObject() {
        Map<String, Object> result = new LinkedHashMap<>();
        
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("math", 90);
        scores.put("english", 85);
        scores.put("physics", 88);
        
        Map<String, String> contacts = new LinkedHashMap<>();
        contacts.put("email", "test@example.com");
        contacts.put("phone", "13800138000");
        
        result.put("userId", "user-001");
        result.put("scores", scores);
        result.put("contacts", contacts);
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/asjava/generic")
    public ResponseEntity<Map<String, Object>> testGenericType() {
        Map<String, Object> result = new LinkedHashMap<>();
        
        List<Integer> intList = Arrays.asList(1, 2, 3, 4, 5);
        List<String> strList = Arrays.asList("a", "b", "c");
        Map<String, Double> doubleMap = new LinkedHashMap<>();
        doubleMap.put("a", 1.1);
        doubleMap.put("b", 2.2);
        
        result.put("intList", intList);
        result.put("strList", strList);
        result.put("doubleMap", doubleMap);
        result.put("nestedList", Arrays.asList(
            Arrays.asList(1, 2),
            Arrays.asList(3, 4)
        ));
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/asjava/boundary")
    public ResponseEntity<Map<String, Object>> testBoundaryConditions() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nullValue", null);
        result.put("emptyString", "");
        result.put("emptyList", Collections.emptyList());
        result.put("emptyMap", Collections.emptyMap());
        result.put("maxInt", Integer.MAX_VALUE);
        result.put("minInt", Integer.MIN_VALUE);
        result.put("maxLong", Long.MAX_VALUE);
        result.put("zero", 0);
        result.put("negative", -100);
        result.put("specialChars", "特殊字符：\n\t\\\"'");
        result.put("unicode", "中文日本語한국어");
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }
}
