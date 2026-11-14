package com.vaabhi.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        // CSRF protection is off because the API is stateless and authenticates only with an
        // `Authorization: Bearer` header, which browsers never attach on their own. If auth ever moves
        // to cookies, CSRF protection must be turned back on.
        return http.csrf(csrf -> csrf.disable()) // NOSONAR: bearer-token API, see above
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/store/**")
                        .permitAll()
                        // Read-only bag pricing; POST only because the bag does not fit in a URL.
                        .requestMatchers(HttpMethod.POST, "/api/v1/store/bag/quote")
                        .permitAll()
                        // Guest order tracking (order number + phone); POST so the phone stays out of URLs and logs.
                        .requestMatchers(HttpMethod.POST, "/api/v1/store/orders/track")
                        .permitAll()
                        .requestMatchers("/api/v1/webhooks/**")
                        .permitAll()
                        // Which sign-in methods work, read by the sign-in page.
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/options")
                        .permitAll()
                        // Sign-in, refresh and logout: the caller has no access token yet (or it expired).
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/**")
                        .permitAll()
                        // Dashboard areas: the owner may use all of them, everyone else needs the area's
                        // permission (the token's "permissions" claim, set at sign-in from the role).
                        .requestMatchers("/api/v1/admin/me", "/api/v1/admin/me/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/settings/**", "/api/v1/admin/categories/**")
                        .hasRole("ADMIN")
                        // Tax classes on categories are billing configuration, like the settings they sit beside.
                        .requestMatchers("/api/v1/admin/settings/**", "/api/v1/admin/categories/**")
                        .access(area("SETTINGS"))
                        // Online orders: cancelling (refunds the payment) and refunding a return are for the owner.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/admin/online-orders/*/cancel",
                                "/api/v1/admin/online-orders/*/refund",
                                "/api/v1/admin/online-orders/returns/*/refund")
                        .hasRole("OWNER")
                        .requestMatchers(
                                "/api/v1/admin/orders/**",
                                "/api/v1/admin/online-orders/**",
                                "/api/v1/admin/clients/**",
                                "/api/v1/admin/alterations/**",
                                "/api/v1/admin/service-products/**",
                                "/api/v1/admin/tailoring/**",
                                // An order's invoice is shown and issued from the order page.
                                "/api/v1/admin/money/orders/**",
                                // Counter staff check coupons, see points and message customers.
                                "/api/v1/admin/engage/coupons/check",
                                "/api/v1/admin/engage/rewards/clients/**",
                                "/api/v1/admin/engage/messages/**")
                        .access(area("ORDERS"))
                        .requestMatchers("/api/v1/admin/stock/**")
                        .access(area("STOCK"))
                        .requestMatchers("/api/v1/admin/engage/**", "/api/v1/admin/gallery/**")
                        .access(area("MARKETING"))
                        .requestMatchers("/api/v1/admin/reports/**", "/api/v1/admin/activity/**")
                        .access(area("REPORTS"))
                        .requestMatchers("/api/v1/admin/workshop/**")
                        .access(area("WORKSHOP"))
                        // Any admin can read the tax classes (product and category editors list them).
                        .requestMatchers(
                                HttpMethod.GET, "/api/v1/admin/money/tax-classes", "/api/v1/admin/money/tax-classes/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/money/**")
                        .access(area("MONEY"))
                        // The books: reading needs the MONEY area; every change is for the owner.
                        .requestMatchers(HttpMethod.GET, "/api/v1/admin/accounts/**")
                        .access(area("MONEY"))
                        .requestMatchers("/api/v1/admin/accounts/**")
                        .hasRole("OWNER")
                        // Changing what a role may do is for the owner alone.
                        .requestMatchers(HttpMethod.PUT, "/api/v1/admin/staff/roles/**")
                        .hasRole("OWNER")
                        .requestMatchers("/api/v1/admin/staff/**")
                        .access(area("STAFF"))
                        .requestMatchers("/api/v1/admin/payroll/**")
                        .access(area("PAYROLL"))
                        .requestMatchers(
                                "/api/v1/admin/products/**", "/api/v1/admin/catalog/**", "/api/v1/admin/storefront/**")
                        .access(area("CATALOGUE"))
                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/v1/customer/**")
                        .hasRole("CUSTOMER")
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(
                        oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecurityProperties properties) {
        return NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /** Signs access tokens (staff and customer sign-in) with the same HS256 secret the decoder verifies. */
    @Bean
    JwtEncoder jwtEncoder(SecurityProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(properties)));
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(properties.corsAllowedOrigins());
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        // The admin dashboard shows API status from the browser.
        source.registerCorsConfiguration("/actuator/health/**", config);
        return source;
    }

    static SecretKey secretKey(SecurityProperties properties) {
        return new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** The owner, or a staff member (ROLE_ADMIN) whose role has the dashboard area's permission. */
    static AuthorizationManager<RequestAuthorizationContext> area(String permission) {
        return AuthorizationManagers.anyOf(
                AuthorityAuthorizationManager.hasRole("OWNER"),
                AuthorizationManagers.allOf(
                        AuthorityAuthorizationManager.hasRole("ADMIN"),
                        AuthorityAuthorizationManager.hasAuthority(PERMISSION_PREFIX + permission)));
    }

    /** Authority prefix for the {@code permissions} claim, e.g. PERM_ORDERS. */
    public static final String PERMISSION_PREFIX = "PERM_";

    /**
     * Maps the {@code roles} claim (e.g. ["ADMIN", "OWNER"]) to {@code ROLE_*} authorities and the {@code permissions}
     * claim (e.g. ["ORDERS"]) to {@code PERM_*} authorities.
     */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        var roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        var permissions = new JwtGrantedAuthoritiesConverter();
        permissions.setAuthoritiesClaimName("permissions");
        permissions.setAuthorityPrefix(PERMISSION_PREFIX);
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var all = new ArrayList<GrantedAuthority>(roles.convert(jwt));
            all.addAll(permissions.convert(jwt));
            return all;
        });
        return converter;
    }
}
