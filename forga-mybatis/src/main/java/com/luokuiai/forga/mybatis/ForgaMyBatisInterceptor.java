package com.luokuiai.forga.mybatis;

import com.luokuiai.forga.query.QueryParameter;
import com.luokuiai.forga.query.QueryValueType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

/**
 * MyBatis plugin that applies configured Forga authorization constraints to SELECT statements.
 */
@Intercepts({
    @Signature(
        type = Executor.class,
        method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(
        type = Executor.class,
        method = "query",
        args = {
          MappedStatement.class,
          Object.class,
          RowBounds.class,
          ResultHandler.class,
          CacheKey.class,
          BoundSql.class
        })
})
public final class ForgaMyBatisInterceptor implements Interceptor {

  private final MyBatisAuthorizationSqlInterceptor sqlInterceptor;

  /**
   * Creates a MyBatis interceptor.
   *
   * @param sqlInterceptor framework-neutral SQL interception support
   */
  public ForgaMyBatisInterceptor(MyBatisAuthorizationSqlInterceptor sqlInterceptor) {
    this.sqlInterceptor = Objects.requireNonNull(sqlInterceptor, "sqlInterceptor is required");
  }

  @Override
  public Object intercept(Invocation invocation) throws Throwable {
    Object[] args = invocation.getArgs();
    MappedStatement statement = (MappedStatement) args[0];
    BoundSql original =
        args.length == 6 ? (BoundSql) args[5] : statement.getBoundSql(args[1]);
    MyBatisBoundSql authorized = sqlInterceptor.intercept(statement.getId(), original.getSql());
    if (authorized.parameters().isEmpty() && authorized.sql().equals(original.getSql().trim())) {
      return invocation.proceed();
    }
    BoundSql executable = executableBoundSql(statement, original, authorized);
    if (args.length == 4) {
      args[0] = copyWithBoundSql(statement, executable);
    } else {
      args[5] = executable;
      args[4] =
          ((Executor) invocation.getTarget())
              .createCacheKey(statement, args[1], (RowBounds) args[2], executable);
    }
    return invocation.proceed();
  }

  @Override
  public Object plugin(Object target) {
    return Plugin.wrap(target, this);
  }

  private static BoundSql executableBoundSql(
      MappedStatement statement, BoundSql original, MyBatisBoundSql authorized) {
    Map<String, QueryParameter> authorizationParameters = new LinkedHashMap<>();
    authorized
        .parameters()
        .forEach(parameter -> authorizationParameters.put(parameter.name(), parameter));
    List<ParameterMapping> mappings = new ArrayList<>();
    String sql =
        bindSql(
            authorized.sql(),
            original.getParameterMappings(),
            authorizationParameters,
            mappings,
            statement);
    BoundSql executable =
        new BoundSql(statement.getConfiguration(), sql, mappings, original.getParameterObject());
    original.getParameterMappings().stream()
        .map(ParameterMapping::getProperty)
        .filter(original::hasAdditionalParameter)
        .forEach(
            property ->
                executable.setAdditionalParameter(
                    property, original.getAdditionalParameter(property)));
    authorized.parameterValues()
        .forEach(
            (name, value) -> executable.setAdditionalParameter("forga.parameters." + name, value));
    return executable;
  }

  private static String bindSql(
      String sql,
      List<ParameterMapping> originalMappings,
      Map<String, QueryParameter> authorizationParameters,
      List<ParameterMapping> mappings,
      MappedStatement statement) {
    StringBuilder executable = new StringBuilder(sql.length());
    int originalIndex = 0;
    boolean quoted = false;
    for (int index = 0; index < sql.length(); index++) {
      char current = sql.charAt(index);
      if (current == '\'' && (index == 0 || sql.charAt(index - 1) != '\\')) {
        quoted = !quoted;
      }
      if (!quoted && current == '?') {
        if (originalIndex >= originalMappings.size()) {
          throw new MyBatisAuthorizationException(
              "SQL parameter mappings do not match placeholders");
        }
        mappings.add(originalMappings.get(originalIndex++));
        executable.append('?');
        continue;
      }
      String prefix = "#{forga.parameters.";
      if (!quoted && sql.startsWith(prefix, index)) {
        int end = sql.indexOf('}', index + prefix.length());
        if (end < 0) {
          throw new MyBatisAuthorizationException("authorization parameter is not closed");
        }
        String name = sql.substring(index + prefix.length(), end);
        QueryParameter parameter = authorizationParameters.get(name);
        if (parameter == null) {
          throw new MyBatisAuthorizationException(
              "authorization parameter is not declared: " + name);
        }
        mappings.add(
            new ParameterMapping.Builder(
                    statement.getConfiguration(),
                    "forga.parameters." + name,
                    javaType(parameter.type()))
                .build());
        executable.append('?');
        index = end;
        continue;
      }
      executable.append(current);
    }
    if (quoted || originalIndex != originalMappings.size()) {
      throw new MyBatisAuthorizationException("SQL parameter mappings do not match placeholders");
    }
    return executable.toString();
  }

  private static Class<?> javaType(QueryValueType type) {
    return switch (type) {
      case STRING -> String.class;
      case NUMBER -> Number.class;
      case BOOLEAN -> Boolean.class;
      case INSTANT -> Instant.class;
      case OPAQUE -> Object.class;
    };
  }

  private static MappedStatement copyWithBoundSql(
      MappedStatement statement, BoundSql authorizedBoundSql) {
    SqlSource sqlSource = ignored -> authorizedBoundSql;
    MappedStatement.Builder builder =
        new MappedStatement.Builder(
                statement.getConfiguration(),
                statement.getId(),
                sqlSource,
                statement.getSqlCommandType())
            .resource(statement.getResource())
            .parameterMap(statement.getParameterMap())
            .resultMaps(statement.getResultMaps())
            .fetchSize(statement.getFetchSize())
            .timeout(statement.getTimeout())
            .statementType(statement.getStatementType())
            .resultSetType(statement.getResultSetType())
            .cache(statement.getCache())
            .flushCacheRequired(statement.isFlushCacheRequired())
            .useCache(statement.isUseCache())
            .resultOrdered(statement.isResultOrdered())
            .keyGenerator(statement.getKeyGenerator())
            .databaseId(statement.getDatabaseId())
            .lang(statement.getLang())
            .dirtySelect(statement.isDirtySelect());
    setJoined(statement.getKeyProperties(), builder::keyProperty);
    setJoined(statement.getKeyColumns(), builder::keyColumn);
    setJoined(statement.getResultSets(), builder::resultSets);
    return builder.build();
  }

  private static void setJoined(String[] values, java.util.function.Consumer<String> setter) {
    if (values != null) {
      setter.accept(String.join(",", values));
    }
  }
}
