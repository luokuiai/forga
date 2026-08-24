package com.luokuiai.forga.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.luokuiai.forga.core.context.AuthenticatedSubjectProvider;
import com.luokuiai.forga.core.model.SubjectRef;
import com.luokuiai.forga.query.PredicateOperator;
import com.luokuiai.forga.query.QueryConstraint;
import com.luokuiai.forga.query.QueryField;
import com.luokuiai.forga.query.QueryParameter;
import com.luokuiai.forga.query.QueryResource;
import com.luokuiai.forga.query.QueryValueType;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;

class ForgaMyBatisInterceptorTest {

  private static final QueryResource RESOURCE = new QueryResource("resource");

  @Test
  void executableQueryReceivesAuthorizationConstraint() throws SQLException {
    DataSource dataSource = database();
    SqlSessionFactory sessions = sessions(dataSource, true);

    try (SqlSession session = sessions.openSession()) {
      List<Map<String, Object>> rows = session.selectList("ResourceMapper.selectAll");

      assertThat(rows).extracting(row -> row.get("OWNER_ID")).containsExactly("alice");
    }
  }

  @Test
  void disabledIntegrationLeavesExecutableQueryUnchanged() throws SQLException {
    DataSource dataSource = database();
    SqlSessionFactory sessions = sessions(dataSource, false);

    try (SqlSession session = sessions.openSession()) {
      List<Map<String, Object>> rows = session.selectList("ResourceMapper.selectAll");

      assertThat(rows).extracting(row -> row.get("OWNER_ID"))
          .containsExactly("alice", "bob");
    }
  }

  private static SqlSessionFactory sessions(DataSource dataSource, boolean enabled) {
    Configuration configuration =
        new Configuration(
            new Environment("test", new JdbcTransactionFactory(), dataSource));
    configuration.addInterceptor(new ForgaMyBatisInterceptor(sqlInterceptor(enabled)));
    StaticSqlSource sqlSource =
        new StaticSqlSource(
            configuration, "SELECT id, owner_id FROM resource_table ORDER BY id");
    ResultMap resultMap =
        new ResultMap.Builder(configuration, "resourceMap", Map.class, List.of()).build();
    MappedStatement statement =
        new MappedStatement.Builder(
                configuration, "ResourceMapper.selectAll", sqlSource, SqlCommandType.SELECT)
            .resultMaps(List.of(resultMap))
            .build();
    configuration.addMappedStatement(statement);
    return new SqlSessionFactoryBuilder().build(configuration);
  }

  private static MyBatisAuthorizationSqlInterceptor sqlInterceptor(boolean enabled) {
    QueryParameter subject = new QueryParameter("subject", QueryValueType.STRING);
    MyBatisAuthorizationBoundary boundary =
        new MyBatisAuthorizationBoundary(
            "resource-owner",
            QueryConstraint.predicate(
                new QueryField(RESOURCE, "owner"), PredicateOperator.EQUALS, subject));
    MyBatisStatementAuthorization statement =
        new MyBatisStatementAuthorization("ResourceMapper.selectAll", boundary);
    AuthenticatedSubjectProvider subjects =
        () -> Optional.of(new SubjectRef("principal", "alice"));
    MyBatisConstraintTranslator translator =
        new MyBatisConstraintTranslator(
            Map.of(
                RESOURCE,
                new MyBatisResourceMapping(
                    RESOURCE, "resource_table", Map.of("owner", "owner_id"))));
    return new MyBatisAuthorizationSqlInterceptor(
        new MyBatisStatementRegistry(List.of(statement)),
        subjects,
        Map::of,
        new MyBatisConstraintApplicator(translator),
        enabled);
  }

  private static DataSource database() throws SQLException {
    DataSource dataSource =
        new UnpooledDataSource(
            "org.h2.Driver", "jdbc:h2:mem:forga;DB_CLOSE_DELAY=-1", "sa", "");
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS resource_table");
      statement.execute("CREATE TABLE resource_table (id INT PRIMARY KEY, owner_id VARCHAR(32))");
      statement.execute("INSERT INTO resource_table VALUES (1, 'alice'), (2, 'bob')");
    }
    return dataSource;
  }
}
