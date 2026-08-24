package com.luokuiai.forga.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.luokuiai.forga.query.AuthorizedListQuery;
import com.luokuiai.forga.query.ExistsConstraint;
import com.luokuiai.forga.query.PredicateOperator;
import com.luokuiai.forga.query.QueryConstraint;
import com.luokuiai.forga.query.QueryConstraintGenerator;
import com.luokuiai.forga.query.QueryOrdering;
import com.luokuiai.forga.query.QueryParameter;
import com.luokuiai.forga.query.QueryProjection;
import com.luokuiai.forga.query.QueryResource;
import com.luokuiai.forga.query.QuerySortDirection;
import com.luokuiai.forga.query.QueryValueType;
import com.luokuiai.forga.query.ResourceQueryMapping;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MyBatisConstraintTranslatorTest {

  private static final QueryResource OUTER = new QueryResource("outer");

  private static final QueryResource INNER = new QueryResource("inner");

  private static final ResourceQueryMapping OUTER_QUERY =
      ResourceQueryMapping.of(OUTER, List.of("id", "state"));

  private static final ResourceQueryMapping INNER_QUERY =
      ResourceQueryMapping.of(INNER, List.of("outer_id", "subject_id", "relation", "rank"));

  @Test
  void translatesCorrelatedExistsWithBoundParameter() {
    QueryParameter subject = new QueryParameter("subject", QueryValueType.STRING);
    ExistsConstraint constraint =
        QueryConstraintGenerator.correlatedExists(
            OUTER_QUERY, INNER_QUERY, "id", "outer_id", "subject_id", subject);

    MyBatisBoundConstraint translated = translator().translate(constraint);

    assertThat(translated.sql())
        .isEqualTo(
            "EXISTS (SELECT 1 FROM related_table WHERE "
                + "outer_table.id = related_table.outer_id "
                + "AND related_table.subject_id = #{forga.parameters.subject})");
    assertThat(translated.parameters()).containsExactly(subject);
  }

  @Test
  void translatesOneComposedBoundaryConstraint() {
    QueryParameter state = new QueryParameter("state", QueryValueType.STRING);
    QueryConstraint first =
        QueryConstraint.predicate(OUTER_QUERY.field("state"), PredicateOperator.EQUALS, state);
    QueryConstraint second =
        QueryConstraint.predicate(
            OUTER_QUERY.field("id"),
            PredicateOperator.EQUALS,
            new QueryParameter("id", QueryValueType.OPAQUE));
    MyBatisAuthorizationBoundary boundary =
        new MyBatisAuthorizationBoundary("outer-list", QueryConstraint.and(List.of(first, second)));

    MyBatisBoundConstraint translated = translator().translate(boundary.constraint());

    assertThat(translated.sql())
        .isEqualTo(
            "(outer_table.state = #{forga.parameters.state}) "
                + "AND (outer_table.id = #{forga.parameters.id})");
    assertThat(translated.parameters())
        .extracting(QueryParameter::name)
        .containsExactly("state", "id");
  }

  @Test
  void applicatorPreservesSqlWhenDisabled() {
    String sql = "SELECT * FROM outer_table WHERE deleted = 0";
    MyBatisAuthorizationBoundary boundary =
        new MyBatisAuthorizationBoundary(
            "outer-list",
            QueryConstraint.predicate(
                OUTER_QUERY.field("state"),
                PredicateOperator.EQUALS,
                new QueryParameter("state", QueryValueType.STRING)));

    MyBatisBoundSql applied =
        new MyBatisConstraintApplicator(translator())
            .apply(sql, Optional.of(boundary), false);

    assertThat(applied.sql()).isEqualTo(sql);
    assertThat(applied.parameters()).isEmpty();
  }

  @Test
  void applicatorAddsOneComposedConstraintWhenEnabled() {
    MyBatisAuthorizationBoundary boundary =
        new MyBatisAuthorizationBoundary(
            "outer-list",
            QueryConstraint.predicate(
                OUTER_QUERY.field("state"),
                PredicateOperator.EQUALS,
                new QueryParameter("state", QueryValueType.STRING)));

    MyBatisBoundSql applied =
        new MyBatisConstraintApplicator(translator())
            .apply("SELECT * FROM outer_table", Optional.of(boundary), true);

    assertThat(applied.sql())
        .isEqualTo(
            "SELECT * FROM outer_table WHERE "
                + "(outer_table.state = #{forga.parameters.state})");
    assertThat(applied.parameters()).extracting(QueryParameter::name)
        .containsExactly("state");
  }

  @Test
  void translatesAuthorizedRowsetJoinProjectionAndOrdering() {
    QueryParameter subject = new QueryParameter("subject", QueryValueType.STRING);
    AuthorizedListQuery listQuery =
        QueryConstraintGenerator.authorizedRowset(
            OUTER_QUERY,
            INNER_QUERY,
            "id",
            "outer_id",
            QueryConstraint.predicate(
                INNER_QUERY.field("subject_id"), PredicateOperator.EQUALS, subject),
            List.of(new QueryProjection(INNER_QUERY.field("relation"), "forga_relation")),
            List.of(
                new QueryOrdering(INNER_QUERY.field("rank"), QuerySortDirection.DESC),
                new QueryOrdering(OUTER_QUERY.field("state"), QuerySortDirection.ASC)));
    MyBatisAuthorizationBoundary boundary =
        MyBatisAuthorizationBoundary.list("outer-list", listQuery);

    MyBatisBoundSql applied =
        new MyBatisConstraintApplicator(translator())
            .apply(
                "SELECT outer_table.* FROM outer_table "
                    + "WHERE outer_table.deleted = 0 "
                    + "ORDER BY outer_table.id ASC LIMIT 20",
                Optional.of(boundary),
                true);

    assertThat(applied.sql())
        .isEqualTo(
            "SELECT outer_table.*, related_table.relation AS forga_relation "
                + "FROM outer_table INNER JOIN related_table "
                + "ON outer_table.id = related_table.outer_id "
                + "WHERE (outer_table.deleted = 0) "
                + "AND (related_table.subject_id = #{forga.parameters.subject}) "
                + "ORDER BY related_table.rank DESC, outer_table.state ASC, "
                + "outer_table.id ASC LIMIT 20");
    assertThat(applied.parameters()).containsExactly(subject);
  }

  @Test
  void appliesConstraintBeforeTrailingClausesAndIgnoresClauseTextInLiteral() {
    MyBatisAuthorizationBoundary boundary = stateBoundary(PredicateOperator.EQUALS);

    MyBatisBoundSql applied =
        new MyBatisConstraintApplicator(translator())
            .apply(
                "SELECT * FROM outer_table WHERE state <> 'order by hidden' "
                    + "ORDER BY id LIMIT 5;",
                Optional.of(boundary),
                true);

    assertThat(applied.sql())
        .isEqualTo(
            "SELECT * FROM outer_table WHERE (state <> 'order by hidden') "
                + "AND (outer_table.state = #{forga.parameters.state}) ORDER BY id LIMIT 5");
  }

  @Test
  void appliesConstraintOnlyToTopLevelSelectWithNestedQuery() {
    MyBatisAuthorizationBoundary boundary = stateBoundary(PredicateOperator.EQUALS);

    MyBatisBoundSql applied =
        new MyBatisConstraintApplicator(translator())
            .apply(
                "SELECT * FROM outer_table WHERE id IN "
                    + "(SELECT outer_id FROM related_table WHERE relation = 'viewer')",
                Optional.of(boundary),
                true);

    assertThat(applied.sql())
        .isEqualTo(
            "SELECT * FROM outer_table WHERE (id IN (SELECT outer_id FROM related_table "
                + "WHERE relation = 'viewer')) AND "
                + "(outer_table.state = #{forga.parameters.state})");
  }

  @Test
  void rejectsSetOperationsAndCollectionlessInOperand() {
    MyBatisConstraintApplicator applicator = new MyBatisConstraintApplicator(translator());

    assertThatExceptionOfType(MyBatisTranslationException.class)
        .isThrownBy(
            () ->
                applicator.apply(
                    "SELECT * FROM outer_table UNION SELECT * FROM outer_table",
                    Optional.of(stateBoundary(PredicateOperator.EQUALS)),
                    true))
        .withMessageContaining("plain SELECT");
    assertThatExceptionOfType(MyBatisTranslationException.class)
        .isThrownBy(
            () ->
                applicator.apply(
                    "SELECT * FROM outer_table",
                    Optional.of(stateBoundary(PredicateOperator.IN)),
                    true))
        .withMessageContaining("collection");
  }

  @Test
  void rejectsUnknownResourceOrColumn() {
    QueryConstraint unknownResource =
        QueryConstraint.predicate(
            ResourceQueryMapping.of(new QueryResource("unknown"), List.of("id")).field("id"),
            PredicateOperator.EQUALS,
            new QueryParameter("id", QueryValueType.OPAQUE));
    QueryConstraint unknownColumn =
        QueryConstraint.predicate(
            ResourceQueryMapping.of(OUTER, List.of("missing")).field("missing"),
            PredicateOperator.EQUALS,
            new QueryParameter("id", QueryValueType.OPAQUE));

    assertThatExceptionOfType(MyBatisTranslationException.class)
        .isThrownBy(() -> translator().translate(unknownResource));
    assertThatExceptionOfType(MyBatisTranslationException.class)
        .isThrownBy(() -> translator().translate(unknownColumn));
  }

  @Test
  void rejectsUnsafeMappingIdentifiers() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> new MyBatisResourceMapping(OUTER, "outer_table", Map.of("id", "id;drop")));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new MyBatisAuthorizationBoundary(
                    "",
                    QueryConstraint.not(
                        QueryConstraint.predicate(
                            OUTER_QUERY.field("id"),
                            PredicateOperator.EQUALS,
                            new QueryParameter("id", QueryValueType.OPAQUE)))));
  }

  private static MyBatisConstraintTranslator translator() {
    return new MyBatisConstraintTranslator(
        Map.of(
            OUTER,
            new MyBatisResourceMapping(
                OUTER, "outer_table", Map.of("id", "id", "state", "state")),
            INNER,
            new MyBatisResourceMapping(
                INNER,
                "related_table",
                Map.of(
                    "outer_id",
                    "outer_id",
                    "subject_id",
                    "subject_id",
                    "relation",
                    "relation",
                    "rank",
                    "rank"))));
  }

  private static MyBatisAuthorizationBoundary stateBoundary(PredicateOperator operator) {
    return new MyBatisAuthorizationBoundary(
        "outer-list",
        QueryConstraint.predicate(
            OUTER_QUERY.field("state"),
            operator,
            new QueryParameter("state", QueryValueType.STRING)));
  }
}
