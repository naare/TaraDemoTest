package ee.ria.tara.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        TestTaraProperties.class
})
public class TestConfiguration {
}
