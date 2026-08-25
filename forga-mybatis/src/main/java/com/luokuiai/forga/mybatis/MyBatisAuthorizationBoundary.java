package com.luokuiai.forga.mybatis;

import com.luokuiai.forga.query.AuthorizedListQuery;
import com.luokuiai.forga.query.QueryConstraint;
import java.util.Objects;
import java.util.Optional;

/**
 * Declared integration boundary for applying one composed authorization constraint.
 *
 * @param id boundary id
 * @param constraint composed authorization constraint, or null for another boundary type
 * @param listQuery set-based authorized list query, or null for another boundary type
 */
public record MyBatisAuthorizationBoundary(
    String id, QueryConstraint constraint, AuthorizedListQuery listQuery) {

  /**
   * Creates a predicate boundary.
   *
   * @param id boundary id
   * @param constraint composed authorization constraint
   */
  public MyBatisAuthorizationBoundary(String id, QueryConstraint constraint) {
    this(id, constraint, null);
  }

  /**
   * Creates an authorization boundary.
   *
   * @param id boundary id
   * @param constraint composed authorization constraint
   * @param listQuery set-based authorized list query
   */
  public MyBatisAuthorizationBoundary {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("id is required");
    }
    id = id.trim();
    if (constraint != null && listQuery != null) {
      throw new IllegalArgumentException("at most one boundary type is allowed");
    }
  }

  /**
   * Creates a boundary whose typed constraint is resolved for each request.
   *
   * @param id boundary id
   * @return dynamic authorization boundary
   */
  public static MyBatisAuthorizationBoundary dynamic(String id) {
    return new MyBatisAuthorizationBoundary(id, null, null);
  }

  /**
   * Creates an authorized list boundary.
   *
   * @param id boundary id
   * @param listQuery set-based authorized list query
   * @return authorization boundary
   */
  public static MyBatisAuthorizationBoundary list(String id, AuthorizedListQuery listQuery) {
    return new MyBatisAuthorizationBoundary(
        id, null, Objects.requireNonNull(listQuery, "listQuery is required"));
  }

  /**
   * Returns the predicate boundary when present.
   *
   * @return predicate boundary
   */
  public Optional<QueryConstraint> predicate() {
    return Optional.ofNullable(constraint);
  }

  /**
   * Returns the authorized list query when present.
   *
   * @return authorized list query
   */
  public Optional<AuthorizedListQuery> authorizedList() {
    return Optional.ofNullable(listQuery);
  }

  /**
   * Returns whether this declaration requires request-time resolution.
   *
   * @return true when no concrete constraint is present
   */
  public boolean isDynamic() {
    return constraint == null && listQuery == null;
  }
}
