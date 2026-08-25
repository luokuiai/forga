package com.luokuiai.forga.mybatis;

import com.luokuiai.forga.core.model.AttributeRef;
import com.luokuiai.forga.core.model.SubjectRef;
import java.util.Map;

/** Resolves a statement authorization boundary for the current request. */
@FunctionalInterface
public interface MyBatisAuthorizationBoundaryResolver {

  /**
   * Resolves one declared boundary to a concrete typed boundary.
   *
   * @param statementId MyBatis mapped statement id
   * @param declared declared fixed or dynamic boundary
   * @param subject current authorization subject
   * @param attributes current neutral authorization attributes
   * @return concrete typed boundary
   */
  MyBatisAuthorizationBoundary resolve(
      String statementId,
      MyBatisAuthorizationBoundary declared,
      SubjectRef subject,
      Map<AttributeRef, String> attributes);

  /**
   * Returns a resolver that preserves fixed declarations and rejects dynamic ones.
   *
   * @return fixed-boundary resolver
   */
  static MyBatisAuthorizationBoundaryResolver declared() {
    return (statementId, boundary, subject, attributes) -> {
      if (boundary.isDynamic()) {
        throw new MyBatisAuthorizationException(
            "dynamic authorization boundary resolver is missing: " + boundary.id());
      }
      return boundary;
    };
  }
}
