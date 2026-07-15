package com.brainos.dashboard;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class DashboardConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock dashboardClock() {
        return Clock.systemDefaultZone();
    }
}
