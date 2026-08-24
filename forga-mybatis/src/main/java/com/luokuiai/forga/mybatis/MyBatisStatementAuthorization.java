package com.luokuiai.forga.mybatis;

import java.util.Objects;

/**
 * Authorization metadata for one MyBatis statement id.
 *
 * @param statementId MyBatis mapped statement id
 * @param boundary typed authorization boundary
 */
public record MyBatisStatementAuthorization(
    String statementId, MyBatisAuthorizationBoundary boundary) {

  /**
   * Creates statement authorization metadata.
   *
   * @param statementId MyBatis mapped statement id
   * @param boundary typed authorization boundary
   */
  public MyBatisStatementAuthorization {
    if (statementId == null || statementId.isBlank()) {
      throw new IllegalArgumentException("statementId is required");
    }
    statementId = statementId.trim();
    boundary = Objects.requireNonNull(boundary, "boundary is required");
  }
}
