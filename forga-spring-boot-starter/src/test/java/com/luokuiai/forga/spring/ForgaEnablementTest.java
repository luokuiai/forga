package com.luokuiai.forga.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.luokuiai.forga.core.context.AuthenticatedSubjectProvider;
import com.luokuiai.forga.mybatis.ForgaMyBatisInterceptor;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ForgaEnablementTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ForgaMyBatisAutoConfiguration.class));

  @Test
  void annotationEnablesMyBatisIntegration() {
    contextRunner
        .withUserConfiguration(EnabledConfiguration.class)
        .run(context -> assertThat(context).hasSingleBean(ForgaMyBatisInterceptor.class));
  }

  @Test
  void legacyPropertyCannotEnableMyBatisIntegration() {
    contextRunner
        .withPropertyValues("forga.enabled=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(ForgaMyBatisInterceptor.class);
            });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableForga
  static class EnabledConfiguration {

    @Bean
    AuthenticatedSubjectProvider authenticatedSubjectProvider() {
      return Optional::empty;
    }
  }
}
