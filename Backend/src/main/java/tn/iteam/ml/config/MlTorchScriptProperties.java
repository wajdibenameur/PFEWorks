package tn.iteam.ml.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ml.torchscript")
public record MlTorchScriptProperties(
        String modelPath,
        String metadataPath
) {
}
