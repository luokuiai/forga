package com.luokuiai.forga.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.luokuiai.forga.core.context.AuthenticatedSubjectProvider;
import com.luokuiai.forga.core.context.AuthorizationAttributesProvider;
import com.luokuiai.forga.core.model.AttributeRef;
import com.luokuiai.forga.core.model.SubjectRef;
import com.luokuiai.forga.query.PredicateOperator;
import com.luokuiai.forga.query.QueryConstraint;
import com.luokuiai.forga.query.QueryParameter;
import com.luokuiai.forga.query.QueryResource;
import com.luokuiai.forga.query.QueryValueType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MyBatisAuthorizationSqlInterceptorTest {

  private static final QueryResource RESOURCE = new QueryResource("resource");

  @Test
  void appliesConstraintForConfiguredSelectStatement() {
    MyBatisAuthorizationSqlInterceptor interceptor = interceptor(true, subjectProvider());

    MyBatisBoundSql sql = interceptor.intercept("Mapper.select", "SELECT * FROM resource_table");

    assertThat(sql.sql())
        .isEqualTo(
            "SELECT * FROM resource_table WHERE "
                + "(resource_table.owner_id = #{forga.parameters.subject})");
    assertThat(sql.parameters()).extracting(QueryParameter::name).containsExactly("subject");
    assertThat(sql.parameterValues()).containsEntry("subject", "alice");
    assertThat(sql.parameterValues()).containsEntry("subject_id", "alice");
    assertThat(sql.parameterValues()).containsEntry("subject_type", "principal");
  }

  @Test
  void leavesUnconfiguredStatementUnchanged() {
    MyBatisAuthorizationSqlInterceptor interceptor = interceptor(true, subjectProvider());

    MyBatisBoundSql sql = interceptor.intercept("Mapper.other", "SELECT * FROM resource_table");

    assertThat(sql.sql()).isEqualTo("SELECT * FROM resource_table");
    assertThat(sql.parameters()).isEmpty();
  }

  @Test
  void leavesConfiguredStatementUnchangedWhenDisabled() {
    MyBatisAuthorizationSqlInterceptor interceptor = interceptor(false, () -> Optional.empty());

    MyBatisBoundSql sql = interceptor.intercept("Mapper.select", "SELECT * FROM resource_table");

    assertThat(sql.sql()).isEqualTo("SELECT * FROM resource_table");
    assertThat(sql.parameters()).isEmpty();
  }

  @Test
  void failsClosedWhenSubjectIsMissing() {
    MyBatisAuthorizationSqlInterceptor interceptor = interceptor(true, Optional::empty);

    assertThatExceptionOfType(MyBatisAuthorizationException.class)
        .isThrownBy(() -> interceptor.intercept("Mapper.select", "SELECT * FROM resource_table"))
        .withMessageContaining("subject");
  }

  @Test
  void failsClosedWhenRequiredAuthorizationParameterIsMissing() {
    MyBatisAuthorizationSqlInterceptor interceptor = interceptor(true, subjectProvider(), Map::of);

    assertThatExceptionOfType(MyBatisAuthorizationException.class)
        .isThrownBy(() -> interceptor.intercept("Mapper.scoped", "SELECT * FROM resource_table"))
        .withMessageContaining("scope");
  }

  @Test
  void failsClosedForUnsupportedSqlShape() {
    MyBatisAuthorizationSqlInterceptor interceptor = interceptor(true, subjectProvider());

    assertThatExceptionOfType(MyBatisAuthorizationException.class)
        .isThrownBy(() -> interceptor.intercept("Mapper.select", "UPDATE resource_table SET a = 1"))
        .withMessageContaining("SELECT");
  }

  @Test
  void resolvesDynamicBoundaryFromCurrentSubjectAndAttributes() {
    AttributeRef scopeAttribute = new AttributeRef("scope_id");
    AtomicInteger calls = new AtomicInteger();
    MyBatisAuthorizationBoundaryResolver resolver =
        (statementId, declared, subject, attributes) -> {
          calls.incrementAndGet();
          assertThat(statementId).isEqualTo("Mapper.dynamic");
          assertThat(subject.id()).isEqualTo("alice");
          assertThat(attributes).containsEntry(scopeAttribute, "org-1");
          return scopedBoundary(declared.id());
        };
    MyBatisAuthorizationSqlInterceptor interceptor =
        interceptor(
            true,
            subjectProvider(),
            () -> Map.of(scopeAttribute, "org-1"),
            resolver);

    MyBatisBoundSql sql =
        interceptor.intercept("Mapper.dynamic", "SELECT * FROM resource_table");

    assertThat(sql.sql())
        .isEqualTo(
            "SELECT * FROM resource_table WHERE "
                + "(resource_table.owner_id = #{forga.parameters.scope_id})");
    assertThat(sql.parameterValues()).containsEntry("scope_id", "org-1");
    assertThat(calls).hasValue(1);
  }

  @Test
  void dynamicBoundaryFailsClosedWithoutConcreteResolution() {
    MyBatisAuthorizationSqlInterceptor missing = interceptor(true, subjectProvider());
    MyBatisAuthorizationSqlInterceptor unresolved =
        interceptor(true, subjectProvider(), Map::of, (id, boundary, subject, attributes) -> null);

    assertThatExceptionOfType(MyBatisAuthorizationException.class)
        .isThrownBy(() -> missing.intercept("Mapper.dynamic", "SELECT * FROM resource_table"))
        .withMessageContaining("resolver is missing");
    assertThatExceptionOfType(MyBatisAuthorizationException.class)
        .isThrownBy(() -> unresolved.intercept("Mapper.dynamic", "SELECT * FROM resource_table"))
        .withMessageContaining("was not resolved");
  }

  @Test
  void disabledDynamicBoundaryDoesNotInvokeResolver() {
    MyBatisAuthorizationSqlInterceptor interceptor =
        interceptor(
            false,
            Optional::empty,
            Map::of,
            (id, boundary, subject, attributes) -> {
              throw new AssertionError("resolver must not be called");
            });

    MyBatisBoundSql sql =
        interceptor.intercept("Mapper.dynamic", "SELECT * FROM resource_table");

    assertThat(sql.sql()).isEqualTo("SELECT * FROM resource_table");
  }

  @Test
  void rejectsDuplicateStatementIds() {
    MyBatisStatementAuthorization statement = statement();

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new MyBatisStatementRegistry(List.of(statement, statement)))
        .withMessageContaining("duplicate");
  }

  private static MyBatisAuthorizationSqlInterceptor interceptor(
      boolean enabled, AuthenticatedSubjectProvider subjects) {
    return interceptor(enabled, subjects, Map::of);
  }

  private static MyBatisAuthorizationSqlInterceptor interceptor(
      boolean enabled,
      AuthenticatedSubjectProvider subjects,
      AuthorizationAttributesProvider attributes) {
    return interceptor(
        enabled,
        subjects,
        attributes,
        MyBatisAuthorizationBoundaryResolver.declared());
  }

  private static MyBatisAuthorizationSqlInterceptor interceptor(
      boolean enabled,
      AuthenticatedSubjectProvider subjects,
      AuthorizationAttributesProvider attributes,
      MyBatisAuthorizationBoundaryResolver boundaries) {
    MyBatisConstraintTranslator translator =
        new MyBatisConstraintTranslator(
            Map.of(
                RESOURCE,
                new MyBatisResourceMapping(
                    RESOURCE, "resource_table", Map.of("owner", "owner_id"))));
    return new MyBatisAuthorizationSqlInterceptor(
        new MyBatisStatementRegistry(
            List.of(statement(), scopedStatement(), dynamicStatement())),
        subjects,
        attributes,
        boundaries,
        new MyBatisConstraintApplicator(translator),
        enabled);
  }

  private static AuthenticatedSubjectProvider subjectProvider() {
    return () -> Optional.of(new SubjectRef("principal", "alice"));
  }

  private static MyBatisStatementAuthorization statement() {
    QueryParameter subject = new QueryParameter("subject", QueryValueType.STRING);
    MyBatisAuthorizationBoundary boundary =
        new MyBatisAuthorizationBoundary(
            "resource-view",
            QueryConstraint.predicate(
                new com.luokuiai.forga.query.QueryField(RESOURCE, "owner"),
                PredicateOperator.EQUALS,
                subject));
    return new MyBatisStatementAuthorization("Mapper.select", boundary);
  }

  private static MyBatisStatementAuthorization scopedStatement() {
    return new MyBatisStatementAuthorization("Mapper.scoped", scopedBoundary("resource-scope"));
  }

  private static MyBatisAuthorizationBoundary scopedBoundary(String id) {
    QueryParameter scope = new QueryParameter("scope_id", QueryValueType.STRING);
    return new MyBatisAuthorizationBoundary(
        id,
        QueryConstraint.predicate(
            new com.luokuiai.forga.query.QueryField(RESOURCE, "owner"),
            PredicateOperator.EQUALS,
            scope));
  }

  private static MyBatisStatementAuthorization dynamicStatement() {
    return new MyBatisStatementAuthorization(
        "Mapper.dynamic", MyBatisAuthorizationBoundary.dynamic("resource-dynamic"));
  }
}
