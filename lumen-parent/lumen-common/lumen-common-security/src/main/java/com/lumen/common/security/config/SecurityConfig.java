package com.lumen.common.security.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .logout(logout -> logout.disable())
            // Explicitly wire a SecurityContextRepository so UserContextResolverFilter
            // can persist the Authentication it derives from gateway headers, and so
            // Spring Security's SecurityContextHolderFilter picks it up on the next
            // request thread (otherwise it runs after our filter and clears the
            // ThreadLocal context).
            .securityContext(sc -> sc.securityContextRepository(securityContextRepository()));
        return http.build();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        // HttpSessionSecurityContextRepository is stateless-by-default-friendly: it
        // does nothing if there's no session, but still preserves the Authentication
        // we set via saveContext(...) within the same request scope.
        return new HttpSessionSecurityContextRepository();
    }
}