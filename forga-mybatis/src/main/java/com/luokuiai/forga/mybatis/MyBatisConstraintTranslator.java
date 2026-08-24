package com.luokuiai.forga.mybatis;

import com.luokuiai.forga.query.AuthorizedListQuery;
import com.luokuiai.forga.query.BooleanConstraint;
import com.luokuiai.forga.query.BooleanOperator;
import com.luokuiai.forga.query.ExistsConstraint;
import com.luokuiai.forga.query.PredicateConstraint;
import com.luokuiai.forga.query.PredicateOperator;
import com.luokuiai.forga.query.QueryConstraint;
import com.luokuiai.forga.query.QueryCorrelation;
import com.luokuiai.forga.query.QueryFieldOperand;
import com.luokuiai.forga.query.QueryJoin;
import com.luokuiai.forga.query.QueryOrdering;
import com.luokuiai.forga.query.QueryOperand;
import com.luokuiai.forga.query.QueryParameter;
import com.luokuiai.forga.query.QueryProjection;
import com.luokuiai.forga.query.QueryResource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Alias;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.ParenthesedExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.SelectItem;

/**
 * Translates typed constraints into one parameterized MyBatis SQL predicate fragment.
 */
public final class MyBatisConstraintTranslator {

  private final Map<QueryResource, MyBatisResourceMapping> mappings;

  /**
   * Creates a translator.
   *
   * @param mappings allowlisted resource mappings
   */
  public MyBatisConstraintTranslator(Map<QueryResource, MyBatisResourceMapping> mappings) {
    this.mappings = Map.copyOf(Objects.requireNonNull(mappings, "mappings are required"));
  }

  /**
   * Translates a typed constraint.
   *
   * @param constraint typed constraint
   * @return bound SQL predicate
   */
  public MyBatisBoundConstraint translate(QueryConstraint constraint) {
    List<QueryParameter> parameters = new ArrayList<>();
    String sql = translateConstraint(constraint, parameters);
    return new MyBatisBoundConstraint(sql, parameters);
  }

  MyBatisBoundSql apply(String sql, QueryConstraint constraint) {
    Objects.requireNonNull(constraint, "constraint is required");
    MyBatisBoundConstraint translated = translate(constraint);
    ParsedSelect parsed = parseSelect(sql, translated.parameters());
    addWhere(parsed.select(), parsed.expression(translated.sql()));
    return new MyBatisBoundSql(parsed.sql(), translated.parameters());
  }

  /**
   * Translates and applies a set-based authorized list query to a SELECT statement.
   *
   * @param sql original SELECT SQL
   * @param query authorized list query
   * @return SQL with authorization rowset join, projections, and ordering
   */
  public MyBatisBoundSql translateAuthorizedList(String sql, AuthorizedListQuery query) {
    Objects.requireNonNull(query, "query is required");
    List<QueryParameter> parameters = new ArrayList<>();
    String where = translateConstraint(query.where(), parameters);
    ParsedSelect parsed = parseSelect(sql, parameters);
    addProjections(parsed.select(), query.projections());
    addJoin(parsed.select(), query, parsed);
    addWhere(parsed.select(), parsed.expression(where));
    addOrderings(parsed.select(), query.orderings());
    return new MyBatisBoundSql(parsed.sql(), parameters);
  }

  private String translateConstraint(
      QueryConstraint constraint, List<QueryParameter> parameters) {
    if (constraint instanceof PredicateConstraint predicate) {
      return translatePredicate(predicate, parameters);
    }
    if (constraint instanceof BooleanConstraint booleanConstraint) {
      return translateBoolean(booleanConstraint, parameters);
    }
    if (constraint instanceof ExistsConstraint exists) {
      return translateExists(exists, parameters);
    }
    throw new MyBatisTranslationException("unsupported constraint node");
  }

  private String translatePredicate(
      PredicateConstraint predicate, List<QueryParameter> parameters) {
    if (predicate.operator() == PredicateOperator.IN) {
      throw new MyBatisTranslationException("IN requires a typed collection operand");
    }
    return column(predicate.left())
        + " "
        + operator(predicate.operator())
        + " "
        + operand(predicate.right(), parameters);
  }

  private String translateBoolean(
      BooleanConstraint constraint, List<QueryParameter> parameters) {
    if (constraint.operator() == BooleanOperator.NOT) {
      return "NOT (" + translateConstraint(constraint.constraints().get(0), parameters) + ")";
    }
    String separator = constraint.operator() == BooleanOperator.AND ? " AND " : " OR ";
    return constraint.constraints().stream()
        .map(child -> "(" + translateConstraint(child, parameters) + ")")
        .collect(Collectors.joining(separator));
  }

  private String translateExists(ExistsConstraint exists, List<QueryParameter> parameters) {
    MyBatisResourceMapping mapping = mapping(exists.resource());
    String joins =
        exists.joins().stream()
            .map(this::translateJoin)
            .collect(Collectors.joining(" AND "));
    String where = translateConstraint(exists.where(), parameters);
    return "EXISTS (SELECT 1 FROM "
        + mapping.tableReference()
        + " WHERE "
        + joins
        + " AND "
        + where
        + ")";
  }

  private String translateJoin(QueryJoin join) {
    return join.correlations().stream()
        .map(this::translateCorrelation)
        .collect(Collectors.joining(" AND "));
  }

  private String translateCorrelation(QueryCorrelation correlation) {
    return column(correlation.outer()) + " = " + column(correlation.inner());
  }

  private void addProjections(PlainSelect select, List<QueryProjection> projections) {
    projections.forEach(
        projection ->
            select.addSelectItems(
                new SelectItem<>(
                    new Column(column(projection.field())), new Alias(projection.alias(), true))));
  }

  private void addJoin(PlainSelect select, AuthorizedListQuery query, ParsedSelect parsed) {
    String joinSql =
        query.join().correlations().stream()
            .map(this::translateCorrelation)
            .collect(Collectors.joining(" AND "));
    MyBatisResourceMapping rowset = mapping(query.join().rowset().resource());
    Join join = new Join();
    join.setInner(true);
    join.setRightItem(new Table(rowset.tableReference()));
    join.addOnExpression(parsed.expression(joinSql));
    select.addJoins(join);
  }

  private static void addWhere(PlainSelect select, Expression authorization) {
    Expression protectedAuthorization = new ParenthesedExpressionList<>(authorization);
    if (select.getWhere() == null) {
      select.setWhere(protectedAuthorization);
      return;
    }
    select.setWhere(
        new AndExpression(
            new ParenthesedExpressionList<>(select.getWhere()), protectedAuthorization));
  }

  private void addOrderings(PlainSelect select, List<QueryOrdering> orderings) {
    if (orderings.isEmpty()) {
      return;
    }
    List<OrderByElement> combined = new ArrayList<>();
    orderings.forEach(
        ordering -> {
          OrderByElement element = new OrderByElement();
          element.setExpression(new Column(column(ordering.field())));
          element.setAsc(ordering.direction() == com.luokuiai.forga.query.QuerySortDirection.ASC);
          element.setAscDescPresent(true);
          combined.add(element);
        });
    if (select.getOrderByElements() != null) {
      combined.addAll(select.getOrderByElements());
    }
    select.setOrderByElements(combined);
  }

  private String operand(QueryOperand operand, List<QueryParameter> parameters) {
    if (operand instanceof QueryParameter parameter) {
      parameters.add(parameter);
      return "#{forga.parameters." + parameter.name() + "}";
    }
    if (operand instanceof QueryFieldOperand fieldOperand) {
      return column(fieldOperand.field());
    }
    throw new MyBatisTranslationException("unsupported operand node");
  }

  private String column(com.luokuiai.forga.query.QueryField field) {
    return mapping(field.resource()).columnReference(field);
  }

  private MyBatisResourceMapping mapping(QueryResource resource) {
    MyBatisResourceMapping mapping = mappings.get(resource);
    if (mapping == null) {
      throw new MyBatisTranslationException("resource is not mapped: " + resource.type());
    }
    return mapping;
  }

  private static ParsedSelect parseSelect(String sql, List<QueryParameter> parameters) {
    String original = Objects.requireNonNull(sql, "sql is required").trim();
    if (original.isBlank()) {
      throw new IllegalArgumentException("sql is required");
    }
    try {
      Statement statement = CCJSqlParserUtil.parse(original);
      if (!(statement instanceof PlainSelect plainSelect)) {
        throw new MyBatisTranslationException("authorization requires one plain SELECT statement");
      }
      return new ParsedSelect(plainSelect, parameterTokens(parameters));
    } catch (JSQLParserException exception) {
      throw new MyBatisTranslationException("failed to parse SELECT statement", exception);
    }
  }

  private static Map<String, String> parameterTokens(List<QueryParameter> parameters) {
    Map<String, String> tokens = new LinkedHashMap<>();
    for (QueryParameter parameter : parameters) {
      tokens.computeIfAbsent(
          parameter.name(), ignored -> ":forga_parameter_" + tokens.size());
    }
    return tokens;
  }

  private static String operator(PredicateOperator operator) {
    return switch (operator) {
      case EQUALS -> "=";
      case NOT_EQUALS -> "<>";
      case IN -> throw new MyBatisTranslationException("IN requires a typed collection operand");
      case GREATER_THAN -> ">";
      case GREATER_THAN_OR_EQUALS -> ">=";
      case LESS_THAN -> "<";
      case LESS_THAN_OR_EQUALS -> "<=";
    };
  }

  private record ParsedSelect(PlainSelect select, Map<String, String> parameterTokens) {

    private Expression expression(String sql) {
      String parsable = sql;
      for (Map.Entry<String, String> token : parameterTokens.entrySet()) {
        parsable =
            parsable.replace("#{forga.parameters." + token.getKey() + "}", token.getValue());
      }
      try {
        return CCJSqlParserUtil.parseCondExpression(parsable);
      } catch (JSQLParserException exception) {
        throw new MyBatisTranslationException(
            "failed to parse authorization expression", exception);
      }
    }

    private String sql() {
      String rendered = select.toString();
      for (Map.Entry<String, String> token : parameterTokens.entrySet()) {
        rendered =
            rendered.replace(token.getValue(), "#{forga.parameters." + token.getKey() + "}");
      }
      return rendered;
    }
  }
}
