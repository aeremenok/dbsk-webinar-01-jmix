package com.debugskills.equipment.api;

import io.jmix.core.JmixSecurityFilterChainOrder;
import io.jmix.security.util.JmixHttpSecurityUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security filter chain for the custom REST endpoints under {@code /api/**}.
 * <p>
 * The chain is ordered before the Jmix UI chain, so it handles {@code /api/**} requests
 * and leaves everything else (login, views) to the standard Jmix UI security.
 * Access is open to everyone; the anonymous user is the one returned by Jmix
 * {@code UserRepository}, and it has no permissions, so controllers read data
 * through {@code UnconstrainedDataManager}.
 */
@Configuration
public class ApiSecurityConfiguration {

    @Bean
    @Order(JmixSecurityFilterChainOrder.CUSTOM)
    SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/**")
                .authorizeHttpRequests(authorize ->
                        authorize.anyRequest().permitAll()
                )
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );
        JmixHttpSecurityUtils.configureAnonymous(http);
        return http.build();
    }
}
