package com.vaabhi.shared.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security settings under {@code vaabhi.security}.
 *
 * @param jwtSecret HMAC secret (at least 32 bytes) used to sign and verify access tokens
 * @param corsAllowedOrigins browser origins allowed to call the API (storefront, admin)
 */
@ConfigurationProperties("vaabhi.security")
public record SecurityProperties(String jwtSecret, List<String> corsAllowedOrigins) {}
