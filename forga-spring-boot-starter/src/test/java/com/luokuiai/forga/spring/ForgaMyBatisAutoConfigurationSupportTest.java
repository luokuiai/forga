package com.luokuiai.forga.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.luokuiai.forga.core.context.AuthenticatedSubjectProvider;
import com.luokuiai.forga.core.context.AuthorizationAttributesProvider;
import com.luokuiai.forga.core.model.SubjectRef;
import com.luokuiai.forga.mybatis.MyBatisAuthorizationBoundary;
import com.luokuiai.forga.mybatis.MyBatisResourceMapping;
import com.luokuiai.forga.mybatis.MyBatisStatementAuthorization;
import com.luokuiai.forga.mybatis.MyBatisStatementRegistry;
import com.luokuiai.forga.query.PredicateOperator;
import com.luokuiai.forga.query.QueryConstraint;
import com.luokuiai.forga.query.QueryParameter;
import com.luokuiai.forga.query.QueryResource;
import com.luokuiai.forga.query.QueryValueType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ForgaMyBatisAutoConfigurationSupportTest {

  private static final QueryResource RESOURCE = new QueryResource("resource");

  @Test
  void assemblesInterceptorFromGenericBeans() {
    AuthorizationAttributesProvider attributes = Map::of;
    AuthenticatedSubjectProvider subjects = subjectProvider();

    ForgaMyBatisIntegrationComponents components =
        ForgaMyBatisAutoConfigurationSupport.assemble(
            registry(), subjects, attributes, mappings());

    assertThat(components.interceptor()).isNotNull();
    assertThat(components.subjects()).isSameAs(subjects);
    assertThat(components.attributes()).isSameAs(attributes);
  }

  private static AuthenticatedSubjectProvider subjectProvider() {
    return () -> Optional.of(new SubjectRef("principal", "alice"));
  }

  private static MyBatisStatementRegistry registry() {
    QueryParameter subject = new QueryParameter("subject", QueryValueType.STRING);
    MyBatisAuthorizationBoundary boundary =
        new MyBatisAuthorizationBoundary(
            "resource-view",
            QueryConstraint.predicate(
                new com.luokuiai.forga.query.QueryField(RESOURCE, "owner"),
                PredicateOperator.EQUALS,
                subject));
    return new MyBatisStatementRegistry(
        List.of(
            new MyBatisStatementAuthorization("Mapper.select", boundary)));
  }

  private static Map<QueryResource, MyBatisResourceMapping> mappings() {
    return Map.of(
        RESOURCE,
        new MyBatisResourceMapping(RESOURCE, "resource_table", Map.of("owner", "owner_id")));
  }
}
