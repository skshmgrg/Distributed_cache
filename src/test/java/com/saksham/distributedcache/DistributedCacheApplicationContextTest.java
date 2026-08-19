package com.saksham.distributedcache;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class DistributedCacheApplicationContextTest {

    @Test
    void applicationContextStarts() {
        // Starting the context verifies constructor selection and all bean wiring.
    }
}
