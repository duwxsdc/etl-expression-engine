package com.etl.engine.rest.ext.validator;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/test/validator")
public class JsonKeyValidatorTestController {

    @PostMapping("/check")
    public ResponseEntity<Map<String, Object>> testValidator(
            @RequestBody Map<String, Object> testData,
            @RequestParam(defaultValue = "id,name,status") String requiredKeys,
            @RequestParam(defaultValue = "true") boolean requireAll) {

        String[] keys = requiredKeys.split(",");
        JsonKeyValidator validator = new JsonKeyValidator(requireAll, keys);

        boolean valid = validator.isValid(testData);
        List<String> errors = validator.getErrors();
        String errorMessage = validator.getErrorMessage();

        return ResponseEntity.ok(Map.of(
            "valid", valid,
            "requiredKeys", List.of(keys),
            "requireAll", requireAll,
            "testData", testData,
            "errors", errors,
            "errorMessage", errorMessage,
            "missingKeys", validator.findMissingKeys(testData),
            "existingKeys", validator.findExistingKeys(testData)
        ));
    }

    @GetMapping("/demo")
    public ResponseEntity<Map<String, Object>> demo() {
        JsonKeyValidator validator = new JsonKeyValidator("id", "name", "status");

        Map<String, Object> testData = Map.of("id", 123, "code", "OK");

        boolean valid = validator.isValid(testData);
        List<String> errors = validator.getErrors();

        return ResponseEntity.ok(Map.of(
            "description", "测试缺失字段的JSON数据验证",
            "requiredKeys", validator.getRequiredKeys(),
            "testData", testData,
            "expectedBehavior", "应该缺少 name 和 status 字段",
            "valid", valid,
            "errors", errors,
            "errorMessage", validator.getErrorMessage(),
            "missingKeys", validator.findMissingKeys(testData),
            "existingKeys", validator.findExistingKeys(testData)
        ));
    }
}
