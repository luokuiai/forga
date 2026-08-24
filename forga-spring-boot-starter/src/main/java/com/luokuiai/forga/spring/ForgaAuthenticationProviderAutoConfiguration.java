package com.luokuiai.forga.spring;

import com.luokuiai.forga.core.context.AuthenticatedSubjectProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/** Validates that enabled Forga integration has one unambiguous authentication provider. */
@AutoConfiguration
@ConditionalOnForgaEnabled
public class ForgaAuthenticationProviderAutoConfiguration {

  /**
   * Validates authenticated-subject provider discovery after singleton assembly.
   *
   * @param providers discovered authenticated-subject providers
   * @return startup validation callback
   */
  @Bean
  public SmartInitializingSingleton forgaAuthenticationProviderValidation(
      ObjectProvider<AuthenticatedSubjectProvider> providers) {
    return () -> ForgaAuthenticationProviders.requireOne(providers);
  }
}
