package com.luokuiai.forga.mybatis;

import com.luokuiai.forga.core.context.AuthenticatedSubjectProvider;
import com.luokuiai.forga.core.context.AuthorizationAttributesProvider;
import com.luokuiai.forga.core.model.AttributeRef;
import com.luokuiai.forga.core.model.SubjectRef;
import com.luokuiai.forga.query.QueryParameter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Framework-neutral SQL interception logic used by the MyBatis plugin.
 */
public final class MyBatisAuthorizationSqlInterceptor {

  private final MyBatisStatementRegistry statements;

  private final AuthenticatedSubjectProvider subjects;

  private final AuthorizationAttributesProvider attributes;

  private final MyBatisAuthorizationBoundaryResolver boundaries;

  private final MyBatisConstraintApplicator applicator;

  private final boolean enabled;

  /**
   * Creates SQL interception support.
   *
   * @param statements statement registry
   * @param subjects subject provider
   * @param attributes request attributes provider
   * @param applicator constraint applicator
   * @param enabled whether authorization is enabled
   */
  public MyBatisAuthorizationSqlInterceptor(
      MyBatisStatementRegistry statements,
      AuthenticatedSubjectProvider subjects,
      AuthorizationAttributesProvider attributes,
      MyBatisConstraintApplicator applicator,
      boolean enabled) {
    this(
        statements,
        subjects,
        attributes,
        MyBatisAuthorizationBoundaryResolver.declared(),
        applicator,
        enabled);
  }

  /**
   * Creates SQL interception support with request-time boundary resolution.
   *
   * @param statements statement registry
   * @param subjects subject provider
   * @param attributes request attributes provider
   * @param boundaries request-time authorization boundary resolver
   * @param applicator constraint applicator
   * @param enabled whether authorization is enabled
   */
  public MyBatisAuthorizationSqlInterceptor(
      MyBatisStatementRegistry statements,
      AuthenticatedSubjectProvider subjects,
      AuthorizationAttributesProvider attributes,
      MyBatisAuthorizationBoundaryResolver boundaries,
      MyBatisConstraintApplicator applicator,
      boolean enabled) {
    this.statements = Objects.requireNonNull(statements, "statements are required");
    this.subjects = Objects.requireNonNull(subjects, "subjects are required");
    this.attributes = Objects.requireNonNull(attributes, "attributes are required");
    this.boundaries = Objects.requireNonNull(boundaries, "boundaries are required");
    this.applicator = Objects.requireNonNull(applicator, "applicator is required");
    this.enabled = enabled;
  }

  /**
   * Applies authorization to SQL for one statement id.
   *
   * @param statementId MyBatis mapped statement id
   * @param sql original SQL
   * @return authorized SQL and bound authorization parameters
   */
  public MyBatisBoundSql intercept(String statementId, String sql) {
    MyBatisStatementAuthorization statement = statements.find(statementId).orElse(null);
    if (!enabled || statement == null) {
      return applicator.apply(sql, java.util.Optional.empty(), false);
    }
    if (!isSelect(sql)) {
      throw new MyBatisAuthorizationException("only SELECT statements can be authorized");
    }
    SubjectRef subject =
        subjects.currentSubject()
            .orElseThrow(
                () -> new MyBatisAuthorizationException("authorization subject is missing"));
    Map<AttributeRef, String> typedAttributes = Map.copyOf(attributes.attributes());
    Map<String, String> requestAttributes =
        typedAttributes.entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    entry -> entry.getKey().name(), Map.Entry::getValue));
    MyBatisAuthorizationBoundary boundary =
        resolveBoundary(statementId, statement.boundary(), subject, typedAttributes);
    MyBatisBoundSql bound = applicator.apply(sql, java.util.Optional.of(boundary), true);
    Map<String, Object> parameterValues = parameterValues(subject, requestAttributes, bound);
    return new MyBatisBoundSql(bound.sql(), bound.parameters(), parameterValues);
  }

  private MyBatisAuthorizationBoundary resolveBoundary(
      String statementId,
      MyBatisAuthorizationBoundary declared,
      SubjectRef subject,
      Map<AttributeRef, String> requestAttributes) {
    MyBatisAuthorizationBoundary resolved;
    try {
      resolved = boundaries.resolve(statementId, declared, subject, requestAttributes);
    } catch (MyBatisAuthorizationException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new MyBatisAuthorizationException(
          "authorization boundary resolution failed: " + declared.id(), exception);
    }
    if (resolved == null || resolved.isDynamic()) {
      throw new MyBatisAuthorizationException(
          "authorization boundary was not resolved: " + declared.id());
    }
    if (!declared.id().equals(resolved.id())) {
      throw new MyBatisAuthorizationException(
          "authorization boundary id changed during resolution: " + declared.id());
    }
    return resolved;
  }

  private static Map<String, Object> parameterValues(
      SubjectRef subject,
      Map<String, String> attributes,
      MyBatisBoundSql bound) {
    Map<String, Object> values = new HashMap<>();
    values.put("subject", subject.id());
    values.put("subject_id", subject.id());
    values.put("subject_type", subject.type());
    values.putAll(attributes);
    for (QueryParameter parameter : bound.parameters()) {
      if (!values.containsKey(parameter.name())) {
        throw new MyBatisAuthorizationException(
            "authorization parameter is missing: " + parameter.name());
      }
    }
    return values;
  }

  private static boolean isSelect(String sql) {
    return sql != null && sql.stripLeading().toLowerCase(Locale.ROOT).startsWith("select ");
  }
}
