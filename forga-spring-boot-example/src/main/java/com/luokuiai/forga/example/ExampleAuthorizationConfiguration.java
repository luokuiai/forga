package com.luokuiai.forga.example;

import com.luokuiai.forga.core.context.AuthenticatedSubjectProvider;
import com.luokuiai.forga.core.context.AuthorizationAttributesProvider;
import com.luokuiai.forga.core.eval.PermissionGrantLookup;
import com.luokuiai.forga.core.eval.PermissionGrantResult;
import com.luokuiai.forga.core.model.AttributeRef;
import com.luokuiai.forga.core.model.ObjectRef;
import com.luokuiai.forga.core.model.PermissionRef;
import com.luokuiai.forga.core.model.RelationRef;
import com.luokuiai.forga.core.model.SubjectRef;
import com.luokuiai.forga.core.policy.CompiledPolicy;
import com.luokuiai.forga.core.policy.PermissionExpression;
import com.luokuiai.forga.core.policy.PolicyCompiler;
import com.luokuiai.forga.core.policy.PolicyDefinition;
import com.luokuiai.forga.core.policy.ResolverCapabilities;
import com.luokuiai.forga.mybatis.MyBatisAuthorizationBoundary;
import com.luokuiai.forga.mybatis.MyBatisAuthorizationBoundaryResolver;
import com.luokuiai.forga.mybatis.MyBatisAuthorizationException;
import com.luokuiai.forga.query.PredicateOperator;
import com.luokuiai.forga.query.QueryConstraint;
import com.luokuiai.forga.query.QueryParameter;
import com.luokuiai.forga.query.QueryResource;
import com.luokuiai.forga.query.QueryValueType;
import com.luokuiai.forga.query.ResourceQueryMapping;
import com.luokuiai.forga.resolver.DirectSubject;
import com.luokuiai.forga.resolver.ForwardRelationshipBatchRequest;
import com.luokuiai.forga.resolver.ForwardRelationshipBatchResponse;
import com.luokuiai.forga.resolver.ForwardRelationshipRequest;
import com.luokuiai.forga.resolver.ForwardRelationshipResponse;
import com.luokuiai.forga.resolver.RelationshipResolver;
import com.luokuiai.forga.resolver.RelationshipSubject;
import com.luokuiai.forga.resolver.ResolverDescriptor;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ExampleAuthorizationConfiguration {

  private static final ResourceQueryMapping DOCUMENT_QUERY =
      ResourceQueryMapping.of(
          new QueryResource("document"), Set.of("owner_id", "department_id", "tenant_id"));

  static final RelationRef VIEWER = new RelationRef("viewer");

  static final RelationRef APPOINTED = new RelationRef("appointed");

  static final PermissionRef VIEW_DOCUMENT = new PermissionRef("view_document");

  static final PermissionRef ENTER_DEPARTMENT = new PermissionRef("enter_department");

  static final AttributeRef EFFECTIVE_TENANT_ID = new AttributeRef("tenant_id");

  static final AttributeRef CURRENT_DEPARTMENT_ID = new AttributeRef("department_id");

  static final ObjectRef DOCUMENT = new ObjectRef("document", "doc-1");

  static final SubjectRef ALICE = new SubjectRef("user", "alice");

  static final SubjectRef CAROL = new SubjectRef("user", "carol");

  static final SubjectRef CAROL_MEMBERSHIP = new SubjectRef("membership", "carol-target");

  static final ObjectRef HOME_DEPARTMENT =
      department(ExampleAuthorizationDataStore.HOME_TENANT, "home-department");

  static final ObjectRef TARGET_DEPARTMENT_A =
      department(ExampleAuthorizationDataStore.TARGET_TENANT, "department-a");

  static final ObjectRef TARGET_DEPARTMENT_B =
      department(ExampleAuthorizationDataStore.TARGET_TENANT, "department-b");

  @Bean
  CompiledPolicy forgaPolicy() {
    PolicyDefinition definition =
        new PolicyDefinition(
            Map.of(
                VIEW_DOCUMENT,
                PermissionExpression.union(
                    List.of(
                        PermissionExpression.relation(VIEWER),
                        PermissionExpression.grant())),
                ENTER_DEPARTMENT,
                PermissionExpression.relation(APPOINTED)));
    return PolicyCompiler.compile(
        definition, ResolverCapabilities.of(List.of(VIEWER, APPOINTED), List.of()));
  }

  @Bean
  ExampleAuthorizationDataStore authorizationDataStore() {
    return new ExampleAuthorizationDataStore();
  }

  @Bean
  PermissionGrantLookup rolePermissionSnapshot(ExampleAuthorizationDataStore dataStore) {
    return requests -> {
      ExampleAuthorizationDataStore.Snapshot snapshot = dataStore.snapshot();
      return requests.stream()
          .collect(
              java.util.stream.Collectors.toUnmodifiableMap(
                  request -> request,
                  request ->
                      new PermissionGrantResult(
                          attribute(request.attributes(), EFFECTIVE_TENANT_ID)
                              .map(
                                  tenantId ->
                                      snapshot.hasPermission(
                                          request.subject(), tenantId, request.permission()))
                              .orElse(false))));
    };
  }

  @Bean
  MyBatisAuthorizationBoundaryResolver documentDataScopeResolver(
      ExampleAuthorizationDataStore dataStore) {
    return (statementId, declared, subject, attributes) -> {
      if (!declared.isDynamic()) {
        return declared;
      }
      if (!ExampleAuthorizationDataStore.DOCUMENT_LIST_BOUNDARY.equals(declared.id())) {
        throw new MyBatisAuthorizationException(
            "unsupported dynamic authorization boundary: " + declared.id());
      }
      String tenantId = requiredAttribute(attributes, EFFECTIVE_TENANT_ID);
      ExampleAuthorizationDataStore.Snapshot snapshot = dataStore.snapshot();
      ExampleAuthorizationDataStore.DocumentDataScope scope =
          snapshot
              .effectiveDataScope(subject, tenantId, declared.id())
              .orElseThrow(
                  () ->
                      new MyBatisAuthorizationException(
                          "data scope is not granted: " + declared.id()));
      if (scope == ExampleAuthorizationDataStore.DocumentDataScope.DEPARTMENT) {
        String departmentId = requiredAttribute(attributes, CURRENT_DEPARTMENT_ID);
        if (!snapshot.hasActiveAppointment(
            subject, tenantId, department(tenantId, departmentId))) {
          throw new MyBatisAuthorizationException(
              "selected department is not an active appointment: " + departmentId);
        }
      }
      QueryConstraint constraint = dataScopeConstraint(scope);
      return new MyBatisAuthorizationBoundary(declared.id(), constraint);
    };
  }

  @Bean
  RelationshipResolver exampleRelationships(ExampleAuthorizationDataStore dataStore) {
    return new InMemoryBusinessRelationshipResolver(dataStore);
  }

  @Bean
  AuthenticatedSubjectProvider authenticatedSubjectProvider(
      ObjectProvider<HttpServletRequest> requests) {
    return () -> {
      HttpServletRequest request = requests.getIfAvailable();
      if (request == null) {
        return Optional.empty();
      }
      String subjectId = request.getHeader("X-Subject-Id");
      if (subjectId == null || subjectId.isBlank()) {
        return Optional.empty();
      }
      String subjectType = request.getHeader("X-Subject-Type");
      return Optional.of(
          new SubjectRef(
              subjectType == null || subjectType.isBlank() ? "user" : subjectType.trim(),
              subjectId.trim()));
    };
  }

  @Bean
  AuthorizationAttributesProvider authorizationAttributesProvider(
      ObjectProvider<HttpServletRequest> requests) {
    return () -> {
      HttpServletRequest request = requests.getIfAvailable();
      if (request == null) {
        return Map.of();
      }
      Map<AttributeRef, String> attributes = new LinkedHashMap<>();
      putHeader(attributes, EFFECTIVE_TENANT_ID, request, "X-Effective-Tenant-Id");
      putHeader(attributes, CURRENT_DEPARTMENT_ID, request, "X-Department-Id");
      return Map.copyOf(attributes);
    };
  }

  private static final class InMemoryBusinessRelationshipResolver
      implements RelationshipResolver {

    private static final ResolverDescriptor DESCRIPTOR =
        new ResolverDescriptor(
            "example-business-relations", Set.of(VIEWER, APPOINTED), Set.of(), Set.of());

    private final ExampleAuthorizationDataStore dataStore;

    private InMemoryBusinessRelationshipResolver(ExampleAuthorizationDataStore dataStore) {
      this.dataStore = dataStore;
    }

    @Override
    public ResolverDescriptor descriptor() {
      return DESCRIPTOR;
    }

    @Override
    public ForwardRelationshipBatchResponse resolveForward(
        ForwardRelationshipBatchRequest request) {
      ExampleAuthorizationDataStore.Snapshot snapshot = dataStore.snapshot();
      return new ForwardRelationshipBatchResponse(
          request.requests().stream()
              .map(item -> new ForwardRelationshipResponse(item, subjects(snapshot, item)))
              .toList());
    }

    private static List<RelationshipSubject> subjects(
        ExampleAuthorizationDataStore.Snapshot snapshot,
        ForwardRelationshipRequest request) {
      if (DOCUMENT.equals(request.object()) && VIEWER.equals(request.relation())) {
        return List.of(new DirectSubject(ALICE));
      }
      if (APPOINTED.equals(request.relation())
          && "department".equals(request.object().type())) {
        return snapshot.appointedSubjects(request.object()).stream()
            .limit(request.limit())
            .map(DirectSubject::new)
            .map(RelationshipSubject.class::cast)
            .toList();
      }
      return List.of();
    }
  }

  private static QueryConstraint dataScopeConstraint(
      ExampleAuthorizationDataStore.DocumentDataScope scope) {
    return switch (scope) {
      case OWNER ->
          QueryConstraint.predicate(
              DOCUMENT_QUERY.field("owner_id"),
              PredicateOperator.EQUALS,
              new QueryParameter("subject_id", QueryValueType.STRING));
      case DEPARTMENT ->
          QueryConstraint.predicate(
              DOCUMENT_QUERY.field("department_id"),
              PredicateOperator.EQUALS,
              new QueryParameter("department_id", QueryValueType.STRING));
      case TENANT ->
          QueryConstraint.predicate(
              DOCUMENT_QUERY.field("tenant_id"),
              PredicateOperator.EQUALS,
              new QueryParameter("tenant_id", QueryValueType.STRING));
    };
  }

  private static ObjectRef department(String tenantId, String departmentId) {
    return new ObjectRef("department", tenantId + ":" + departmentId);
  }

  private static Optional<String> attribute(
      Map<AttributeRef, String> attributes, AttributeRef attribute) {
    return Optional.ofNullable(attributes.get(attribute)).filter(value -> !value.isBlank());
  }

  private static String requiredAttribute(
      Map<AttributeRef, String> attributes, AttributeRef attribute) {
    return attribute(attributes, attribute)
        .orElseThrow(
            () ->
                new MyBatisAuthorizationException(
                    "authorization attribute is missing: " + attribute.name()));
  }

  private static void putHeader(
      Map<AttributeRef, String> attributes,
      AttributeRef attribute,
      HttpServletRequest request,
      String header) {
    String value = request.getHeader(header);
    if (value != null && !value.isBlank()) {
      attributes.put(attribute, value.trim());
    }
  }
}
