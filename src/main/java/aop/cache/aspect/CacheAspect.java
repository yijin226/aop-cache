package aop.cache.aspect;

import aop.cache.annotation.Cache;
import aop.cache.enums.CacheType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.util.Strings;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author kevin
 * Date  2022/1/23 11:43 PM
 * @version 1.0
 */
@Aspect
@Component
public final class CacheAspect {
    private final static Logger logger = LoggerFactory.getLogger(CacheAspect.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int TTL_RANDOM_SECONDS_RANGE = 30;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 定义切入点，使用了 @RedisCache 的方法
     */
    @Pointcut("@annotation(aop.cache.annotation.Cache)")
    public void redisServicePoint() {

    }

    @Around("redisServicePoint()")
    public Object redisCacheAspect(ProceedingJoinPoint jp) throws Throwable {

        Class<?> cls = jp.getTarget().getClass();
        String methodName = jp.getSignature().getName();

        Map<String, Object> map = isCache(cls, methodName);
        boolean isCache = (boolean)map.get("isCache");

        if (isCache) {
            String cacheName = (String) map.get("name"); // 缓存名字
            String key = (String) map.get("key"); // 自定义缓存key
            long ttl = (long) map.get("remoteTtl"); // 过期时间， 0代表永久有效
            long ttlRandom = (long) map.get("remoteRandom");
            CacheType cacheType = (CacheType) map.get("type");
            Class<?> methodReturnType = (Class<?>)map.get("methodReturnType"); // 方法的返回类型
            Method method = (Method)map.get("method"); // 方法

            // 判断cacheName是否为空，如果cacheName为空则使用默认的cacheName
            String realCacheName = cacheName.equals("") ? cls.getName() + "." + methodName : cacheName;
            // 判断key是否为空， 如果为空则使用默认的key
            String realKey = key.equals("") ? realCacheName + ":" + defaultKeyGenerator(jp) : realCacheName + ":" + parseKey(key, method, jp.getArgs());

            // 判断缓存中是否存在该key, 如果存在则直接从缓存中获取数据并返回
            if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(realKey))) {
                String value = stringRedisTemplate.opsForValue().get(realKey);
                return objectMapper.readValue(value, methodReturnType);
            } else {
                Object result = jp.proceed();
                // 将返回结果保存到缓存中
                long realTtl = ttl + ttlRandom;
                stringRedisTemplate.opsForValue().set(realKey, objectMapper.writeValueAsString(result), realTtl, TimeUnit.SECONDS);
                return result;
            }
        }
        return jp.proceed();
    }

    /**
     * 自定义生成key，使用方法中的参数作为key
     */
    private String defaultKeyGenerator(ProceedingJoinPoint jp) {
        // 获取所有参数的值
        List<String> list = new ArrayList<>();
        Object[] args = jp.getArgs();
        for (Object object : args) {
            list.add(object.toString());
        }
        return list.toString();
    }

    /**
     *	获取缓存的key
     *	key 定义在注解上，支持SPEL表达式
     */
    private String parseKey(String key, Method method, Object [] args){
        //获取被拦截方法参数名列表(使用Spring支持类库)
        LocalVariableTableParameterNameDiscoverer u = new LocalVariableTableParameterNameDiscoverer();
        String [] paraNameArr = u.getParameterNames(method);
        //使用SPEL进行key的解析
        ExpressionParser parser = new SpelExpressionParser();
        //SPEL上下文
        StandardEvaluationContext context = new StandardEvaluationContext();
        //把方法参数放入SPEL上下文中
        for(int i = 0; i < Objects.requireNonNull(paraNameArr).length; i++){
            context.setVariable(paraNameArr[i], args[i]);
        }
        return parser.parseExpression(key).getValue(context, String.class);
    }

    /**
     * 获取是否是缓存
     *
     * @param clazz clazz
     * @param methodName methodName
     * @return Map<String, Object>
     */
    private Map<String, Object> isCache(Class<?> clazz, String methodName) {
        boolean isCache = false;
        Map<String, Object> map = new HashMap<>();
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().equals(methodName) && method.isAnnotationPresent(Cache.class)) {
                Cache cache = method.getAnnotation(Cache.class); // 获取方法上的注解
                checkParam(cache, map);
                Class<?> methodReturnType = method.getReturnType(); // 获取方法的返回类型
                map.put("name", cache.name());
                map.put("key", cache.key());
                map.put("cacheType", cache.type());
                map.put("remoteTtl", cache.remoteTtl());
                map.put("remoteRandom", cache.remoteRandom());
                map.put("methodReturnType", methodReturnType);
                map.put("method", method);
                isCache = true;
                break;
            }
        }
        map.put("isCache", isCache);
        return map;
    }

    /**
     * 检查注解参数是否有效
     *
     * @param cache cache
     * @param map map
     */
    private void checkParam(Cache cache, Map<String, Object> map) {
        if (Strings.isBlank((String) cache.key())) {
            throw new IllegalArgumentException("Cache key can not be null or empty");
        }
        if ((long) cache.remoteTtl() <= 0) {
            throw new IllegalArgumentException("Cache can't have no expiration time");
        }
        if ((long) cache.remoteRandom() <= 0) {
            Random random = new Random();
            long ttlRandom = random.nextInt(TTL_RANDOM_SECONDS_RANGE);
            map.put("remoteRandom", ttlRandom);
        }
    }
}
