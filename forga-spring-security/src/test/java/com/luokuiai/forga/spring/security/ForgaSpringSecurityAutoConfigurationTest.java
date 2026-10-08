package com.luokuiai.forga.spring.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.luokuiai.forga.core.context.AuthenticatedSubjectProvider;
import com.luokuiai.forga.spring.EnableForga;
import com.luokuiai.forga.spring.ForgaAuthenticationProviderAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ForgaSpringSecurityAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ForgaSpringSecurityAutoConfiguration.class,
                  ForgaAuthenticationProviderAutoConfiguration.class));

  @Test
  void assemblesSpringSecurityProvider() {
    contextRunner.withUserConfiguration(EnabledConfiguration.class).run(
        context -> assertThat(context).hasSingleBean(AuthenticatedSubjectProvider.class));
  }

  @Test
  void disabledIntegrationDoesNotCreateProvider() {
    contextRunner.run(
        context -> assertThat(context).doesNotHaveBean(AuthenticatedSubjectProvider.class));
  }

  @Configuration(proxyBeanMethods = false)
  @EnableForga
  static class EnabledConfiguration { }
}
