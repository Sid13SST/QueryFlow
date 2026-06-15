package com.queryflow;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Disabled because it requires a live database connection which is not available in unit test execution environment")
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
