package com.aaron.helpdesk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain helpdeskSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("SUPPORT_OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/chat/**", "/api/returns/**").hasRole("CUSTOMER")
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .build();
    }

    @Bean
    PasswordEncoder helpdeskPasswordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService helpdeskUsers(PasswordEncoder encoder,
            @Value("${helpdesk.security.customer-1001.password:customer-1001-local}") String customer1001Password,
            @Value("${helpdesk.security.customer-2002.password:customer-2002-local}") String customer2002Password,
            @Value("${helpdesk.security.operator.password:operator-local}") String operatorPassword) {
        return new InMemoryUserDetailsManager(
                User.withUsername("customer-1001").password(encoder.encode(customer1001Password)).roles("CUSTOMER").build(),
                User.withUsername("customer-2002").password(encoder.encode(customer2002Password)).roles("CUSTOMER").build(),
                User.withUsername("support-operator").password(encoder.encode(operatorPassword)).roles("SUPPORT_OPERATOR").build());
    }
}
