package aop.cache.test;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * @author kevin
 * Date  2022/1/24 12:21 AM
 * @version 1.0
 */
@SpringBootTest
public class FuncTest {

    @Test
    public void test() {
        long ttlRandom = new Random().nextInt(30);
        System.out.println(ttlRandom);
    }
}
