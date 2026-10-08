package com.luokuiai.forga.satoken;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.stp.StpLogic;
import com.luokuiai.forga.core.context.AuthenticatedSubjectProvider;
import com.luokuiai.forga.spring.EnableForga;
import com.luokuiai.forga.spring.ForgaAuthenticationProviderAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ForgaSaTokenAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ForgaSaTokenAutoConfiguration.class,
                  ForgaAuthenticationProviderAutoConfiguration.class));

  @Test
  void assemblesSaTokenProviderWhenStpLogicExists() {
    contextRunner
        .withUserConfiguration(EnabledConfiguration.class)
        .withBean(StpLogic.class, () -> new StubStpLogic("alice"))
        .run(
            context -> {
              assertThat(context).hasSingleBean(AuthenticatedSubjectProvider.class);
              assertThat(context.getBean(AuthenticatedSubjectProvider.class).currentSubject())
                  .hasValueSatisfying(
                      subject -> {
                        assertThat(subject.type()).isEqualTo("user");
                        assertThat(subject.id()).isEqualTo("alice");
                      });
            });
  }

  @Test
  void failsWithoutStpLogic() {
    contextRunner.withUserConfiguration(EnabledConfiguration.class).run(
        context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class);
        });
  }

  @Test
  void disabledIntegrationDoesNotCreateProvider() {
    contextRunner.withBean(StpLogic.class, () -> new StubStpLogic("alice"))
        .run(context -> assertThat(context).doesNotHaveBean(AuthenticatedSubjectProvider.class));
  }

  @Configuration(proxyBeanMethods = false)
  @EnableForga
  static class EnabledConfiguration { }

  private static final class StubStpLogic extends StpLogic {

    private final String loginId;

    private StubStpLogic(String loginId) {
      super("test");
      this.loginId = loginId;
    }

    @Override
    public boolean isLogin() {
      return true;
    }

    @Override
    public String getLoginIdAsString() {
      return loginId;
    }
  }
}
