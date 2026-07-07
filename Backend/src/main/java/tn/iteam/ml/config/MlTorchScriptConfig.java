package tn.iteam.ml.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({MlTorchScriptProperties.class, MlRiskProperties.class})
public class MlTorchScriptConfig {
}
