package com.hoperefund;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// JWT_SECRET이 없으면 서버가 켜지지 않으므로 테스트용 값을 넣는다 (실제 값은 .env)
@SpringBootTest(properties = "jwt.secret=test-jwt-secret-must-be-at-least-32-bytes")
class HopeRefundBackendApplicationTests {

    @Test
    void contextLoads() {}
}
