package com.luokuiai.forga.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.luokuiai.forga.core.context.AuthenticatedSubjectProvider;
import com.luokuiai.forga.core.context.AuthorizationAttributesProvider;
import com.luokuiai.forga.mybatis.ForgaMyBatisInterceptor;
import com.luokuiai.forga.mybatis.MyBatisAuthorizationBoundary;
import com.luokuiai.forga.mybatis.MyBatisAuthorizationBoundaryResolver;
import com.luokuiai.forga.mybatis.MyBatisResourceMapping;
import com.luokuiai.forga.mybatis.MyBatisStatementAuthorization;
import com.luokuiai.forga.mybatis.MyBatisStatementRegistry;
import com.luokuiai.forga.query.PredicateOperator;
import com.luokuiai.forga.query.QueryConstraint;
import com.luokuiai.forga.query.QueryField;
import com.luokuiai.forga.query.QueryParameter;
import com.luokuiai.forga.query.QueryResource;
import com.luokuiai.forga.query.QueryValueType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.ibatis.plugin.Interceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ForgaMyBatisAutoConfigurationTest {

  private static final QueryResource RESOURCE = new QueryResource("resource");

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ForgaMyBatisAutoConfiguration.class))
          .withUserConfiguration(EnabledConfiguration.class);

  @Test
  void suppliesSafeDefaultsWithoutOptionalDeclarations() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(ForgaMyBatisInterceptor.class);
          assertThat(context).hasSingleBean(MyBatisStatementRegistry.class);
          assertThat(context.getBean(MyBatisStatementRegistry.class).isEmpty()).isTrue();
          assertThat(context).hasSingleBean(AuthorizationAttributesProvider.class);
          assertThat(context.getBean(AuthorizationAttributesProvider.class).attributes()).isEmpty();
        });
  }

  @Test
  void aggregatesTypedStatementAndResourceDeclarations() {
    MyBatisStatementAuthorization statement = statement("Mapper.select");
    contextRunner
        .withBean(MyBatisStatementAuthorization.class, () -> statement)
        .withBean(MyBatisResourceMapping.class, ForgaMyBatisAutoConfigurationTest::mapping)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(MyBatisStatementRegistry.class).find("Mapper.select"))
                  .containsSame(statement);
              assertThat(context).hasSingleBean(ForgaMyBatisInterceptor.class);
            });
  }

  @Test
  void acceptsHostDynamicBoundaryResolver() {
    MyBatisAuthorizationBoundaryResolver resolver =
        (statementId, declared, subject, attributes) -> statement(statementId).boundary();
    contextRunner
        .withBean(MyBatisAuthorizationBoundaryResolver.class, () -> resolver)
        .withBean(
            MyBatisStatementAuthorization.class,
            () ->
                new MyBatisStatementAuthorization(
                    "Mapper.dynamic", MyBatisAuthorizationBoundary.dynamic("resource-view")))
        .withBean(MyBatisResourceMapping.class, ForgaMyBatisAutoConfigurationTest::mapping)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(MyBatisAuthorizationBoundaryResolver.class))
                  .isSameAs(resolver);
              assertThat(context.getBean(MyBatisStatementRegistry.class).find("Mapper.dynamic"))
                  .isPresent();
            });
  }

  @Test
  void backsOffForHostOverrides() {
    MyBatisStatementRegistry registry =
        new MyBatisStatementRegistry(List.of(statement("Mapper.custom")));
    AuthorizationAttributesProvider attributes = Map::of;
    ForgaMyBatisInterceptor interceptor =
        ForgaMyBatisAutoConfigurationSupport.assemble(
                registry, subjectProvider(), attributes, Map.of(RESOURCE, mapping()))
            .interceptor();

    contextRunner
        .withBean(MyBatisStatementRegistry.class, () -> registry)
        .withBean(AuthorizationAttributesProvider.class, () -> attributes)
        .withBean(ForgaMyBatisInterceptor.class, () -> interceptor)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(MyBatisStatementRegistry.class)).isSameAs(registry);
              assertThat(context.getBean(AuthorizationAttributesProvider.class))
                  .isSameAs(attributes);
              assertThat(context.getBean(ForgaMyBatisInterceptor.class)).isSameAs(interceptor);
            });
  }

  @Test
  void rejectsDuplicateResourceMappings() {
    contextRunner
        .withBean(
            "firstMapping",
            MyBatisResourceMapping.class,
            ForgaMyBatisAutoConfigurationTest::mapping)
        .withBean(
            "secondMapping",
            MyBatisResourceMapping.class,
            ForgaMyBatisAutoConfigurationTest::mapping)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage("duplicate MyBatis resource mapping: resource");
            });
  }

  @Test
  void reportsFocusedErrorWhenSubjectProviderIsMissing() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ForgaMyBatisAutoConfiguration.class))
        .withUserConfiguration(MissingSubjectConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage(
                      "enabled integration requires an authentication provider");
            });
  }

  @Test
  void reportsFocusedErrorWhenSubjectProviderIsAmbiguous() {
    contextRunner
        .withBean(
            "secondSubjectProvider",
            AuthenticatedSubjectProvider.class,
            () -> Optional::empty)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage(
                      "enabled integration requires exactly one authentication provider, found 2");
            });
  }

  @Test
  void doesNotRegisterInfrastructureWhenMyBatisIsAbsent() {
    contextRunner
        .withClassLoader(new FilteredClassLoader(Interceptor.class))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean("forgaMyBatisStatementRegistry");
              assertThat(context).doesNotHaveBean("forgaAuthorizationAttributesProvider");
              assertThat(context).doesNotHaveBean("forgaMyBatisInterceptor");
            });
  }

  private static MyBatisStatementAuthorization statement(String statementId) {
    QueryParameter subject = new QueryParameter("subject", QueryValueType.STRING);
    MyBatisAuthorizationBoundary boundary =
        new MyBatisAuthorizationBoundary(
            "resource-view",
            QueryConstraint.predicate(
                new QueryField(RESOURCE, "owner"), PredicateOperator.EQUALS, subject));
    return new MyBatisStatementAuthorization(statementId, boundary);
  }

  private static MyBatisResourceMapping mapping() {
    return new MyBatisResourceMapping(
        RESOURCE, "resource_table", Map.of("owner", "owner_id"));
  }

  private static AuthenticatedSubjectProvider subjectProvider() {
    return () -> Optional.empty();
  }

  @Configuration(proxyBeanMethods = false)
  @EnableForga
  static class MissingSubjectConfiguration {
  }

  @Configuration(proxyBeanMethods = false)
  @EnableForga
  static class EnabledConfiguration {

    @Bean
    AuthenticatedSubjectProvider authenticatedSubjectProvider() {
      return subjectProvider();
    }
  }
}
