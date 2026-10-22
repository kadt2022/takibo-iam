package com.takibo.adp.spring.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "takibo.adp")
public class AdpProperties {
    
    private boolean enabled = true;
    
    private int timeoutMs = 15;
    
    private int parallelism = 8;
    
    private String policyVersion = "1.0";
    
    private Features features = new Features();
    
    private Profile profile = new Profile();
    
    @Data
    public static class Features {
        private boolean baseline = true;
        private boolean velocity = true;
        private boolean thresholds = true;
        private boolean uncertainty = true;
        private boolean location = true;
        private boolean network = true;
        private boolean time = true;
    }
    
    @Data
    public static class Profile {
        private WriterMode writer = WriterMode.NOOP;
        private int asyncQueueSize = 1000;
        private int asyncBatchSize = 50;
    }
    
    public enum WriterMode {
        NOOP,
        ASYNC_JPA
    }
}
