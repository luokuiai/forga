package com.luokuiai.forga.spring;

import com.luokuiai.forga.core.context.AuthenticatedSubjectProvider;
import com.luokuiai.forga.core.context.AuthorizationAttributesProvider;
import com.luokuiai.forga.mybatis.ForgaMyBatisInterceptor;
import com.luokuiai.forga.mybatis.MyBatisAuthorizationBoundaryResolver;
import com.luokuiai.forga.mybatis.MyBatisResourceMapping;
import com.luokuiai.forga.mybatis.MyBatisStatementAuthorization;
import com.luokuiai.forga.mybatis.MyBatisStatementRegistry;
import com.luokuiai.forga.query.QueryResource;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot auto-configuration for generic Forga MyBatis authorization.
 */
@AutoConfiguration
@ConditionalOnForgaEnabled
@ConditionalOnClass(name = "org.apache.ibatis.plugin.Interceptor")
public class ForgaMyBatisAutoConfiguration {

  /**
   * Aggregates statement authorization declarations into the default registry.
   *
   * @param statements declared statement authorization metadata
   * @return statement registry
   */
  @Bean
  @ConditionalOnMissingBean(MyBatisStatementRegistry.class)
  public MyBatisStatementRegistry forgaMyBatisStatementRegistry(
      ObjectProvider<MyBatisStatementAuthorization> statements) {
    return new MyBatisStatementRegistry(statements.orderedStream().toList());
  }

  /**
   * Supplies empty request attributes when the host does not use request-scoped ABAC inputs.
   *
   * @return empty request attributes provider
   */
  @Bean
  @ConditionalOnMissingBean(AuthorizationAttributesProvider.class)
  public AuthorizationAttributesProvider forgaAuthorizationAttributesProvider() {
    return Map::of;
  }

  /**
   * Registers the Forga MyBatis interceptor when integration is enabled.
   *
   * @param statements statement registry
   * @param subjects discovered subject providers
   * @param attributes request attributes provider
   * @param mappings declared MyBatis resource mappings
   * @param boundaries optional request-time boundary resolver
   * @return MyBatis interceptor
   */
  @Bean
  @ConditionalOnMissingBean(ForgaMyBatisInterceptor.class)
  public ForgaMyBatisInterceptor forgaMyBatisInterceptor(
      MyBatisStatementRegistry statements,
      ObjectProvider<AuthenticatedSubjectProvider> subjects,
      AuthorizationAttributesProvider attributes,
      ObjectProvider<MyBatisResourceMapping> mappings,
      ObjectProvider<MyBatisAuthorizationBoundaryResolver> boundaries) {
    return ForgaMyBatisAutoConfigurationSupport.assemble(
            statements,
            ForgaAuthenticationProviders.requireOne(subjects),
            attributes,
            boundaries.getIfAvailable(MyBatisAuthorizationBoundaryResolver::declared),
            resourceMappings(mappings))
        .interceptor();
  }

  private static Map<QueryResource, MyBatisResourceMapping> resourceMappings(
      ObjectProvider<MyBatisResourceMapping> mappings) {
    return mappings.orderedStream()
        .collect(
            Collectors.toUnmodifiableMap(
                MyBatisResourceMapping::resource,
                Function.identity(),
                (first, duplicate) -> {
                  throw new ForgaRuntimeException(
                      "duplicate MyBatis resource mapping: " + duplicate.resource().type());
                }));
  }
}
