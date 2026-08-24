package com.luokuiai.forga.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.luokuiai.forga.core.context.AuthenticatedSubjectProvider;
import com.luokuiai.forga.core.context.AuthorizationAttributesProvider;
import com.luokuiai.forga.core.model.SubjectRef;
import com.luokuiai.forga.query.PredicateOperator;
import com.luokuiai.forga.query.QueryConstraint;
import com.luokuiai.forga.query.QueryParameter;
import com.luokuiai.forga.query.QueryResource;
import com.luokuiai.forga.query.QueryValueType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    MyBatisConstraintTranslator translator =
        new MyBatisConstraintTranslator(
            Map.of(
                RESOURCE,
                new MyBatisResourceMapping(
                    RESOURCE, "resource_table", Map.of("owner", "owner_id"))));
    return new MyBatisAuthorizationSqlInterceptor(
        new MyBatisStatementRegistry(List.of(statement(), scopedStatement())),
        subjects,
        attributes,
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
    QueryParameter scope = new QueryParameter("scope_id", QueryValueType.STRING);
    MyBatisAuthorizationBoundary boundary =
        new MyBatisAuthorizationBoundary(
            "resource-scope",
            QueryConstraint.predicate(
                new com.luokuiai.forga.query.QueryField(RESOURCE, "owner"),
                PredicateOperator.EQUALS,
                scope));
    return new MyBatisStatementAuthorization("Mapper.scoped", boundary);
  }
}
