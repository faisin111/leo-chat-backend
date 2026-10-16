package com.example.chat_app_backend.security;

import com.example.chat_app_backend.security.jwt.AuthCookieProperties;
import com.example.chat_app_backend.security.jwt.AuthCookies;
import com.example.chat_app_backend.security.jwt.AuthTokenFilter;
import com.example.chat_app_backend.security.jwt.JwtUtils;
import com.example.chat_app_backend.security.jwt.RestAccessDeniedHandler;
import com.example.chat_app_backend.security.jwt.RestAuthenticationEntryPoint;
import com.example.chat_app_backend.security.services.UserDetailsServiceImpl;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class WebSecurityConfig {
  private final UserDetailsServiceImpl userDetailsService;
  private final RestAuthenticationEntryPoint unauthorizedHandler;
  private final RestAccessDeniedHandler accessDeniedHandler;
  private final AuthCookies cookies;
  private final JwtUtils jwtUtils;

  public WebSecurityConfig(
      UserDetailsServiceImpl userDetailsService,
      RestAuthenticationEntryPoint unauthorizedHandler,
      RestAccessDeniedHandler accessDeniedHandler,
      JwtUtils jwtUtils,
      AuthCookies cookies) {
    this.userDetailsService = userDetailsService;
    this.unauthorizedHandler = unauthorizedHandler;
    this.accessDeniedHandler = accessDeniedHandler;
    this.cookies = cookies;
    this.jwtUtils = jwtUtils;
  }

  @Bean
  public AuthTokenFilter authenticationJwtTokenFilter() {
    return new AuthTokenFilter(jwtUtils, userDetailsService, cookies);
  }

  @Bean
  public DaoAuthenticationProvider authenticationProvider() {
    DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
    authProvider.setUserDetailsService(userDetailsService);
    authProvider.setPasswordEncoder(passwordEncoder());
    return authProvider;
  }

  @Bean
  public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig)
      throws Exception {
    return authConfig.getAuthenticationManager();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(
      @Value("${app.cors.allowed-origins:http://localhost:3000}") String[] allowedOrigins) {
    CorsConfiguration configuration = new CorsConfiguration();
    // Patterns allow Vercel preview deployments while credentials remain enabled.
    configuration.setAllowedOriginPatterns(List.of(allowedOrigins));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(
        List.of("Authorization", "Cache-Control", "Content-Type", "X-XSRF-TOKEN"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      CorsConfigurationSource corsConfigurationSource,
      AuthCookieProperties cookieProperties)
      throws Exception {
    CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfRepository.setCookieCustomizer(
        builder ->
            builder
                .path("/")
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .httpOnly(false));
    http.cors(cors -> cors.configurationSource(corsConfigurationSource))
        .csrf(
            csrf ->
                csrf.csrfTokenRepository(csrfRepository)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .requireCsrfProtectionMatcher(new BrowserCsrfMatcher(cookies)))
        .requestCache(cache -> cache.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        .headers(
            headers ->
                headers
                    .xssProtection(xss -> xss.disable())
                    .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
                    .frameOptions(frame -> frame.deny()))
        .exceptionHandling(
            exception ->
                exception
                    .authenticationEntryPoint(unauthorizedHandler)
                    .accessDeniedHandler(accessDeniedHandler))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf")
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/logout",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password",
                        "/api/v1/auth/verify-email",
                        "/api/v1/auth/resend-verification")
                    .permitAll()
                    .requestMatchers(
                        "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                    .requestMatchers("/ws", "/ws/**", "/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated());
    http.authenticationProvider(authenticationProvider());
    http.addFilterBefore(
        authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
