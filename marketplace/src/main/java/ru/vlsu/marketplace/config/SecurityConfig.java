package ru.vlsu.marketplace.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import ru.vlsu.marketplace.services.CustomUserDetailsService;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF-защита включена: Thymeleaf добавляет токен в формы, fetch-запросы
            // передают его в заголовке X-CSRF-TOKEN (см. fragments/header.html)
            .csrf(csrf -> csrf.ignoringRequestMatchers("/admin/experiments/**"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/about", "/authentication", "/auth", "/auth/**", "/catalog", "/product/**", "/brands", "/brand/*/logo", "/main.css", "/css/**", "/js/**", "/images/**", "/photoformain/**", "/brends/**", "/product/*/image", "/error").permitAll()
                .requestMatchers("/cart/**", "/orders/**", "/favorites/**", "/profile/**", "/reviews/**").authenticated()
                .requestMatchers("/seller/**").hasAnyAuthority("ROLE_seller", "ROLE_admin")
                .requestMatchers("/moderation/**").hasAnyAuthority("ROLE_moderator", "ROLE_admin")
                .requestMatchers("/admin/**").hasAuthority("ROLE_admin")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/auth")
                .loginProcessingUrl("/auth/login")
                .defaultSuccessUrl("/", true)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/profile/logout")
                .logoutSuccessUrl("/")
            )
            .rememberMe(remember -> remember
                .tokenValiditySeconds(86400)
                .key("marketplaceSecretKey")
                .userDetailsService(userDetailsService)
            )
            .userDetailsService(userDetailsService)
            // Токен CSRF загружается заранее: иначе он создаётся лениво во время рендеринга
            // шаблона, когда ответ уже частично отправлен и сессию создать нельзя
            .addFilterAfter(new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                FilterChain chain) throws ServletException, IOException {
                    CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                    if (token != null) token.getToken();
                    chain.doFilter(request, response);
                }
            }, CsrfFilter.class);

        return http.build();
    }
}
