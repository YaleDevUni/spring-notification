package com.example.notification;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("전체 컨텍스트 로드 테스트는 DB 연결이 필요하므로 통합 환경에서만 실행")
class NotificationApplicationTests {

	@Test
	void contextLoads() {
	}

}
