package com.etl.engine.rest.ext.validator;

/**
 * 响应校验器接口
 * 
 * <p>校验响应内容，失败时抛出ResponseValidationException。</p>
 */
@FunctionalInterface
public interface ResponseValidator {

    /**
     * 校验响应
     * @param response 响应对象
     * @throws ResponseValidationException 校验失败时抛出
     */
    void validate(Object response);
}
