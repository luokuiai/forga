package com.luokuiai.forga.spring.security;

import com.luokuiai.forga.core.context.AuthenticatedSubjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.context.SecurityContextHolder;

/** Spring Boot assembly for Spring Security authenticated-subject mapping. */
@AutoConfiguration
@AutoConfigureAfter(name = "com.luokuiai.forga.spring.ForgaAuthenticationProviderAutoConfiguration")
@ConditionalOnClass(SecurityContextHolder.class)
@ConditionalOnBean(name = "forgaAuthenticationProviderValidation")
public class ForgaSpringSecurityAutoConfiguration {

  /**
   * Creates the Spring Security subject provider for the current security context.
   *
   * @return authenticated-subject provider
   */
  @Bean
  public AuthenticatedSubjectProvider forgaSpringSecuritySubjectProvider() {
    return new SpringSecurityAuthenticatedSubjectProvider();
  }
}
