package com.vaabhi.shared.email;

import java.util.Properties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmailProperties.class)
class EmailConfig {

    @Bean
    EmailSender emailSender(EmailProperties properties, Environment environment) {
        if (!properties.configured()) {
            return new LogEmailSender(!environment.matchesProfiles("prod"));
        }
        var mail = new JavaMailSenderImpl();
        mail.setHost(properties.host());
        mail.setPort(properties.port());
        mail.setUsername(properties.username());
        mail.setPassword(properties.password());
        mail.setDefaultEncoding("UTF-8");
        mail.setJavaMailProperties(smtp(properties.port()));
        return new SmtpEmailSender(mail, properties);
    }

    /** STARTTLS on 587 (and 25), SSL on 465; both required, never plain text. Short timeouts. */
    static Properties smtp(int port) {
        var props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        if (port == 465) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return props;
    }
}
