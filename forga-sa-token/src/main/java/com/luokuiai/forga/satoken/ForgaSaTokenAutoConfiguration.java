package com.luokuiai.forga.satoken;

import cn.dev33.satoken.stp.StpLogic;
import com.luokuiai.forga.core.context.AuthenticatedSubjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

/** Spring Boot assembly for Sa-Token authenticated-subject mapping. */
@AutoConfiguration
@AutoConfigureAfter(name = "com.luokuiai.forga.spring.ForgaAuthenticationProviderAutoConfiguration")
@ConditionalOnClass(StpLogic.class)
@ConditionalOnBean(name = "forgaAuthenticationProviderValidation")
public class ForgaSaTokenAutoConfiguration {

  /**
   * Creates the Sa-Token subject provider for the host's login context.
   *
   * @param stpLogic selected Sa-Token login context
   * @return authenticated-subject provider
   */
  @Bean
  public AuthenticatedSubjectProvider forgaSaTokenSubjectProvider(StpLogic stpLogic) {
    return new SaTokenAuthenticatedSubjectProvider(stpLogic);
  }
}
