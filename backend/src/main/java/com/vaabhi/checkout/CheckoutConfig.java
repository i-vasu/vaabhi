package com.vaabhi.checkout;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({RazorpayProperties.class, CheckoutProperties.class})
class CheckoutConfig {}
