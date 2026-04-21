package tn.iteam;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires external MySQL/Redis services that are not available in the local test workspace")
class PfeprojectApplicationTests {

    @Test
    void contextLoads() {
    }

}
