package com.luokuiai.forga.mybatis;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Applies at most one composed authorization constraint at a declared MyBatis boundary.
 */
public final class MyBatisConstraintApplicator {

  private final MyBatisConstraintTranslator translator;

  /**
   * Creates a constraint applicator.
   *
   * @param translator typed constraint translator
   */
  public MyBatisConstraintApplicator(MyBatisConstraintTranslator translator) {
    this.translator = Objects.requireNonNull(translator, "translator is required");
  }

  /**
   * Applies a boundary constraint when enabled.
   *
   * @param sql original SQL
   * @param boundary optional authorization boundary
   * @param enabled whether authorization is enabled
   * @return SQL and authorization parameter references
   */
  public MyBatisBoundSql apply(
      String sql, Optional<MyBatisAuthorizationBoundary> boundary, boolean enabled) {
    if (sql == null || sql.isBlank()) {
      throw new IllegalArgumentException("sql is required");
    }
    String original = sql.trim();
    if (!enabled || boundary.isEmpty()) {
      return new MyBatisBoundSql(original, List.of());
    }
    MyBatisAuthorizationBoundary authorizationBoundary = boundary.orElseThrow();
    if (authorizationBoundary.isDynamic()) {
      throw new MyBatisAuthorizationException(
          "authorization boundary was not resolved: " + authorizationBoundary.id());
    }
    if (authorizationBoundary.authorizedList().isPresent()) {
      return translator.translateAuthorizedList(
          original, authorizationBoundary.authorizedList().orElseThrow());
    }
    return translator.apply(original, authorizationBoundary.predicate().orElseThrow());
  }
}
