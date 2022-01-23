package aop.cache.annotation;

import aop.cache.enums.CacheType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author kevin
 * Date  2022/1/23 11:13 PM
 * @version 1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Cache {
    /**
     * Redis Cache Key Prefix
     * @return String
     */
    String name() default "";

    /**
     * Redis Cache Key
     *
     * @return String
     */
    String key() default "";

    /**
     * 使用的缓存方式
     *
     * @return CacheType
     */
    CacheType type() default CacheType.REMOTE;

    /**
     *缓存过期时间，单位:s，默认60s
     *
     * @return long
     */
    long remoteTtl() default 60;

    /**
     * 缓存随机时间
     *
     * @return long
     */
    long remoteRandom() default 0;
}
