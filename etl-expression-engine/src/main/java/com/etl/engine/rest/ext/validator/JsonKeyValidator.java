package com.etl.engine.rest.ext.validator;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * JSON Key检查验证器
 * 
 * <p>验证API响应JSON数据中是否包含指定的关键key。</p>
 * 
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 创建验证器
 * JsonKeyValidator validator = new JsonKeyValidator("id", "name", "status");
 * 
 * // 执行验证
 * boolean valid = validator.validate(responseJson);
 * 
 * // 获取错误信息
 * if (!valid) {
 *     List<String> errors = validator.getErrors();
 * }
 * }</pre>
 */
@Slf4j
public class JsonKeyValidator implements ResponseValidator {

    /**
     * 待检查的key列表
     */
    private final Set<String> requiredKeys;

    /**
     * 验证过程中收集的错误信息
     */
    @Getter
    private final List<String> errors = new ArrayList<>();

    /**
     * 是否所有key都必须存在
     */
    private final boolean requireAll;

    /**
     * 构造方法 - 所有key都必须存在
     * @param keys 待检查的key列表
     */
    public JsonKeyValidator(String... keys) {
        this(true, keys);
    }

    /**
     * 构造方法 - 指定是否所有key都必须存在
     * @param requireAll true=所有key都必须存在，false=至少存在一个
     * @param keys 待检查的key列表
     */
    public JsonKeyValidator(boolean requireAll, String... keys) {
        this.requireAll = requireAll;
        this.requiredKeys = Arrays.stream(keys)
                .filter(Objects::nonNull)
                .filter(k -> !k.isEmpty())
                .collect(Collectors.toSet());
        log.debug("创建JsonKeyValidator: requiredKeys={}, requireAll={}", requiredKeys, requireAll);
    }

    /**
     * 构造方法 - 使用集合
     * @param keys 待检查的key集合
     */
    public JsonKeyValidator(Collection<String> keys) {
        this(true, keys);
    }

    /**
     * 构造方法 - 使用集合并指定是否所有key都必须存在
     * @param requireAll true=所有key都必须存在，false=至少存在一个
     * @param keys 待检查的key集合
     */
    public JsonKeyValidator(boolean requireAll, Collection<String> keys) {
        this.requireAll = requireAll;
        this.requiredKeys = keys.stream()
                .filter(Objects::nonNull)
                .filter(k -> !k.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * 验证响应JSON中是否包含指定的key
     * @param response 响应对象（需可转换为Map）
     * @throws ResponseValidationException 验证失败时抛出
     */
    @Override
    public void validate(Object response) {
        clearErrors();

        if (response == null) {
            addError("响应对象为null");
            throw new ResponseValidationException(getErrorMessage());
        }

        if (requiredKeys.isEmpty()) {
            log.debug("没有需要检查的key，验证通过");
            return;
        }

        Map<?, ?> responseMap = convertToMap(response);
        if (responseMap == null) {
            addError("响应对象无法转换为Map格式，实际类型: " + response.getClass().getName());
            throw new ResponseValidationException(getErrorMessage());
        }

        Set<String> missingKeys = findMissingKeys(responseMap);
        Set<String> existingKeys = findExistingKeys(responseMap);

        if (requireAll) {
            if (!missingKeys.isEmpty()) {
                addError("缺少必需字段: " + String.join(", ", missingKeys));
                addError("现有字段: " + String.join(", ", existingKeys));
                throw new ResponseValidationException(getErrorMessage());
            }
        } else {
            if (existingKeys.isEmpty()) {
                addError("响应中不包含任何期望的字段: " + String.join(", ", requiredKeys));
                throw new ResponseValidationException(getErrorMessage());
            }
        }

        log.debug("验证通过: 检查keys={}, 存在keys={}", requiredKeys, existingKeys);
    }

    /**
     * 执行验证并返回布尔结果（不抛出异常）
     * @param response 响应对象
     * @return true=验证通过，false=验证失败
     */
    public boolean isValid(Object response) {
        try {
            validate(response);
            return true;
        } catch (ResponseValidationException e) {
            log.debug("验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 查找缺失的key
     * @param responseMap 响应Map
     * @return 缺失的key集合
     */
    public Set<String> findMissingKeys(Map<?, ?> responseMap) {
        return requiredKeys.stream()
                .filter(key -> !responseMap.containsKey(key))
                .collect(Collectors.toSet());
    }

    /**
     * 查找存在的key
     * @param responseMap 响应Map
     * @return 存在的key集合
     */
    public Set<String> findExistingKeys(Map<?, ?> responseMap) {
        return requiredKeys.stream()
                .filter(responseMap::containsKey)
                .collect(Collectors.toSet());
    }

    /**
     * 添加错误信息
     * @param error 错误信息
     */
    private void addError(String error) {
        errors.add(error);
        log.debug("添加验证错误: {}", error);
    }

    /**
     * 清空错误信息
     */
    public void clearErrors() {
        errors.clear();
    }

    /**
     * 获取完整的错误信息
     * @return 错误信息字符串
     */
    public String getErrorMessage() {
        if (errors.isEmpty()) {
            return "无错误";
        }
        return String.join("; ", errors);
    }

    /**
     * 将响应对象转换为Map
     * @param response 响应对象
     * @return Map对象，转换失败返回null
     */
    @SuppressWarnings("unchecked")
    private Map<?, ?> convertToMap(Object response) {
        if (response instanceof Map) {
            return (Map<?, ?>) response;
        }

        if (response instanceof String jsonStr) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(jsonStr, Map.class);
            } catch (Exception e) {
                log.debug("JSON字符串解析失败: {}", e.getMessage());
                return null;
            }
        }

        return null;
    }

    /**
     * 获取待检查的key列表（只读）
     */
    public Set<String> getRequiredKeys() {
        return Collections.unmodifiableSet(requiredKeys);
    }

    /**
     * 是否要求所有key都必须存在
     */
    public boolean isRequireAll() {
        return requireAll;
    }

    /**
     * 创建Builder进行链式构建
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder类 - 支持链式构建
     */
    public static class Builder {
        private final Set<String> keys = new LinkedHashSet<>();
        private boolean requireAll = true;

        /**
         * 添加单个key
         */
        public Builder key(String key) {
            if (key != null && !key.isEmpty()) {
                keys.add(key);
            }
            return this;
        }

        /**
         * 添加多个key
         */
        public Builder keys(String... keys) {
            for (String key : keys) {
                key(key);
            }
            return this;
        }

        /**
         * 添加key集合
         */
        public Builder keys(Collection<String> keys) {
            this.keys.addAll(keys.stream()
                    .filter(Objects::nonNull)
                    .filter(k -> !k.isEmpty())
                    .collect(Collectors.toSet()));
            return this;
        }

        /**
         * 设置是否所有key都必须存在
         */
        public Builder requireAll(boolean requireAll) {
            this.requireAll = requireAll;
            return this;
        }

        /**
         * 构建JsonKeyValidator实例
         */
        public JsonKeyValidator build() {
            return new JsonKeyValidator(requireAll, keys);
        }
    }
}
