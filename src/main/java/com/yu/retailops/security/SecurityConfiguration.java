package com.yu.retailops.security;

import com.yu.retailops.config.RetailOpsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    InMemoryUserDetailsManager users(RetailOpsProperties properties) {
        var manager = new InMemoryUserDetailsManager();
        properties.users().forEach((name, user) -> {
            if (!user.passwordHash().matches("\\{bcrypt}\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}")) {
                throw new IllegalStateException("Operator passwords must be {bcrypt} hashes");
            }
            manager.createUser(User.withUsername(name).password(user.passwordHash())
                    .roles("DIAGNOSE").build());
        });
        return manager;
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        // Stateless, JSON-only API; no browser sessions, form login, or cross-origin support.
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers("/api/v1/diagnoses/**").hasRole("DIAGNOSE")
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults()).build();
    }
}
