package com.luokuiai.forga.spring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ForgaBannerAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ForgaBannerAutoConfiguration.class));

  @Test
  void registersVersionedBannerWhenForgaIsEnabled() {
    contextRunner
        .withUserConfiguration(EnabledConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasBean("forgaStartupBanner");
              String banner = ForgaStartupBanner.render();
              assertThat(banner)
                  .contains("Forga")
                  .contains("Fine-grained Object-Relation Graph Authorization")
                  .contains("(v" + ForgaStartupBanner.version() + ")");
              assertThat(banner.split("\\R"))
                  .hasSize(10)
                  .containsSequence(
                      "    ______",
                      "   |  ____|",
                      "   | |__ ___  _ __ __ _  __ _",
                      "   |  __/ _ \\| '__/ _` |/ _` |",
                      "   | | | (_) | | | (_| | (_| |",
                      "   |_|  \\___/|_|  \\__, |\\__,_|",
                      "                   |___/");
              assertThat(ForgaStartupBanner.version()).isNotBlank().doesNotStartWith("${");
            });
  }

  @Test
  void legacyPropertyDoesNotRegisterBanner() {
    contextRunner
        .withPropertyValues("forga.enabled=true")
        .run(context -> assertThat(context).doesNotHaveBean("forgaStartupBanner"));
  }

  @Configuration(proxyBeanMethods = false)
  @EnableForga
  static class EnabledConfiguration { }
}
