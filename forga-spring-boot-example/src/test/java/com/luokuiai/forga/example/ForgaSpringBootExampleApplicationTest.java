package com.luokuiai.forga.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.luokuiai.forga.core.eval.CheckRequest;
import com.luokuiai.forga.core.eval.AuthorizationEvaluator;
import com.luokuiai.forga.core.eval.EvaluationReadContext;
import com.luokuiai.forga.core.eval.PermissionGrantLookup;
import com.luokuiai.forga.core.model.AttributeRef;
import com.luokuiai.forga.core.model.ObjectRef;
import com.luokuiai.forga.core.model.SubjectRef;
import com.luokuiai.forga.core.policy.CompiledPolicy;
import com.luokuiai.forga.mybatis.MyBatisAuthorizationBoundary;
import com.luokuiai.forga.mybatis.MyBatisAuthorizationBoundaryResolver;
import com.luokuiai.forga.mybatis.MyBatisAuthorizationException;
import com.luokuiai.forga.query.PredicateConstraint;
import com.luokuiai.forga.query.QueryParameter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ForgaSpringBootExampleApplicationTest {

  @Autowired private MockMvc mvc;

  @Autowired private ApplicationContext applicationContext;

  @Autowired private CompiledPolicy policy;

  @Autowired private ExampleAuthorizationDataStore dataStore;

  @Autowired private PermissionGrantLookup grants;

  @Autowired private MyBatisAuthorizationBoundaryResolver boundaries;

  @Autowired private AuthorizationEvaluator evaluator;

  @BeforeEach
  void resetAuthorizationData() {
    dataStore.reset();
  }

  @Test
  void allowsConfiguredViewer() throws Exception {
    mvc.perform(get("/documents/doc-1").header("X-Subject-Id", "alice"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("doc-1"));
  }

  @Test
  void rejectsMissingSubject() throws Exception {
    mvc.perform(get("/documents/doc-1")).andExpect(status().isUnauthorized());
  }

  @Test
  void allowsSubjectFromEffectivePermissionSnapshot() throws Exception {
    mvc.perform(
            get("/documents/doc-2")
                .header("X-Subject-Id", "carol")
                .header("X-Effective-Tenant-Id", ExampleAuthorizationDataStore.HOME_TENANT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("doc-2"));
  }

  @Test
  void deniesSubjectWithoutViewerRelation() throws Exception {
    mvc.perform(get("/documents/doc-1").header("X-Subject-Id", "bob"))
        .andExpect(status().isForbidden());
  }

  @Test
  void deniesViewerForAnotherDocument() throws Exception {
    mvc.perform(get("/documents/doc-2").header("X-Subject-Id", "alice"))
        .andExpect(status().isForbidden());
  }

  @Test
  void observesPermissionGrantChangesWithoutReplacingPolicy() throws Exception {
    CompiledPolicy originalPolicy = policy;
    dataStore.revokePermission(
        ExampleAuthorizationDataStore.HOME_TENANT,
        ExampleAuthorizationDataStore.READER_ROLE,
        ExampleAuthorizationConfiguration.VIEW_DOCUMENT);

    mvc.perform(
            get("/documents/doc-2")
                .header("X-Subject-Id", "carol")
                .header("X-Effective-Tenant-Id", ExampleAuthorizationDataStore.HOME_TENANT))
        .andExpect(status().isForbidden());

    dataStore.grantPermission(
        ExampleAuthorizationDataStore.HOME_TENANT,
        ExampleAuthorizationDataStore.READER_ROLE,
        ExampleAuthorizationConfiguration.VIEW_DOCUMENT);

    mvc.perform(
            get("/documents/doc-2")
                .header("X-Subject-Id", "carol")
                .header("X-Effective-Tenant-Id", ExampleAuthorizationDataStore.HOME_TENANT))
        .andExpect(status().isOk());
    assertThat(applicationContext.getBean(CompiledPolicy.class)).isSameAs(originalPolicy);
  }

  @Test
  void resolvesCompleteGrantBatchFromCurrentSnapshot() {
    SubjectRef unassigned = new SubjectRef("user", "dave");
    CheckRequest granted =
        new CheckRequest(
            new ObjectRef("document", "doc-1"),
            ExampleAuthorizationConfiguration.VIEW_DOCUMENT,
            ExampleAuthorizationConfiguration.CAROL,
            attributes(ExampleAuthorizationDataStore.HOME_TENANT));
    CheckRequest denied =
        new CheckRequest(
            new ObjectRef("document", "doc-2"),
            ExampleAuthorizationConfiguration.VIEW_DOCUMENT,
            unassigned,
            attributes(ExampleAuthorizationDataStore.HOME_TENANT));

    var resolved =
        grants.resolve(
            List.of(granted, denied),
            new EvaluationReadContext(Optional.empty(), Optional.empty()));

    assertThat(resolved.values()).containsOnlyKeys(granted, denied);
    assertThat(resolved.values().get(granted)).isTrue();
    assertThat(resolved.values().get(denied)).isFalse();
  }

  @Test
  void observesDataScopeChangesWithoutReplacingPolicy() {
    CompiledPolicy originalPolicy = policy;
    MyBatisAuthorizationBoundary declared =
        MyBatisAuthorizationBoundary.dynamic(
            ExampleAuthorizationDataStore.DOCUMENT_LIST_BOUNDARY);

    PredicateConstraint owner =
        constraint(
            boundaries.resolve(
                "DocumentMapper.list",
                declared,
                ExampleAuthorizationConfiguration.CAROL,
                attributes(ExampleAuthorizationDataStore.HOME_TENANT)));
    assertThat(owner.left().name()).isEqualTo("owner_id");
    assertThat(((QueryParameter) owner.right()).name()).isEqualTo("subject_id");

    dataStore.setDataScope(
        ExampleAuthorizationDataStore.HOME_TENANT,
        ExampleAuthorizationDataStore.READER_ROLE,
        ExampleAuthorizationDataStore.DOCUMENT_LIST_BOUNDARY,
        ExampleAuthorizationDataStore.DocumentDataScope.DEPARTMENT);

    PredicateConstraint department =
        constraint(
            boundaries.resolve(
                "DocumentMapper.list",
                declared,
                ExampleAuthorizationConfiguration.CAROL,
                attributes(
                    ExampleAuthorizationDataStore.HOME_TENANT, "home-department")));
    assertThat(department.left().name()).isEqualTo("department_id");
    assertThat(((QueryParameter) department.right()).name()).isEqualTo("department_id");

    dataStore.setDataScope(
        ExampleAuthorizationDataStore.HOME_TENANT,
        ExampleAuthorizationDataStore.READER_ROLE,
        ExampleAuthorizationDataStore.DOCUMENT_LIST_BOUNDARY,
        ExampleAuthorizationDataStore.DocumentDataScope.TENANT);

    PredicateConstraint tenant =
        constraint(
            boundaries.resolve(
                "DocumentMapper.list",
                declared,
                ExampleAuthorizationConfiguration.CAROL,
                attributes(ExampleAuthorizationDataStore.HOME_TENANT)));
    assertThat(tenant.left().name()).isEqualTo("tenant_id");
    assertThat(((QueryParameter) tenant.right()).name()).isEqualTo("tenant_id");
    assertThat(applicationContext.getBean(CompiledPolicy.class)).isSameAs(originalPolicy);
  }

  @Test
  void deniesDynamicBoundaryWithoutDataScopeGrant() {
    dataStore.removeDataScope(
        ExampleAuthorizationDataStore.HOME_TENANT,
        ExampleAuthorizationDataStore.READER_ROLE,
        ExampleAuthorizationDataStore.DOCUMENT_LIST_BOUNDARY);

    assertThatThrownBy(
            () -> boundaries.resolve(
                "DocumentMapper.list",
                MyBatisAuthorizationBoundary.dynamic(
                    ExampleAuthorizationDataStore.DOCUMENT_LIST_BOUNDARY),
                ExampleAuthorizationConfiguration.CAROL,
                attributes(ExampleAuthorizationDataStore.HOME_TENANT)))
        .isInstanceOf(MyBatisAuthorizationException.class)
        .hasMessageContaining("data scope is not granted");
  }

  @Test
  void authorizesMembershipOnlyInItsEffectiveTenant() throws Exception {
    mvc.perform(
            get("/documents/doc-2")
                .header("X-Subject-Type", "membership")
                .header("X-Subject-Id", "carol-target")
                .header("X-Effective-Tenant-Id", ExampleAuthorizationDataStore.TARGET_TENANT)
                .header("X-Department-Id", "department-a"))
        .andExpect(status().isOk());

    CheckRequest membershipInHomeTenant =
        new CheckRequest(
            new ObjectRef("document", "doc-2"),
            ExampleAuthorizationConfiguration.VIEW_DOCUMENT,
            ExampleAuthorizationConfiguration.CAROL_MEMBERSHIP,
            attributes(ExampleAuthorizationDataStore.HOME_TENANT));
    CheckRequest userInTargetTenant =
        new CheckRequest(
            new ObjectRef("document", "doc-2"),
            ExampleAuthorizationConfiguration.VIEW_DOCUMENT,
            ExampleAuthorizationConfiguration.CAROL,
            attributes(ExampleAuthorizationDataStore.TARGET_TENANT));

    var isolated =
        grants.resolve(
            List.of(membershipInHomeTenant, userInTargetTenant),
            new EvaluationReadContext(Optional.empty(), Optional.empty()));

    assertThat(isolated.values().values()).allMatch(result -> !result);
  }

  @Test
  void resolvesAndRevokesConcurrentAppointmentRelationship() {
    CheckRequest enterDepartment =
        new CheckRequest(
            ExampleAuthorizationConfiguration.TARGET_DEPARTMENT_A,
            ExampleAuthorizationConfiguration.ENTER_DEPARTMENT,
            ExampleAuthorizationConfiguration.CAROL_MEMBERSHIP);

    assertThat(evaluator.check(enterDepartment).allowed()).isTrue();

    dataStore.deactivateAppointment(
        ExampleAuthorizationConfiguration.CAROL_MEMBERSHIP,
        ExampleAuthorizationDataStore.TARGET_TENANT,
        ExampleAuthorizationConfiguration.TARGET_DEPARTMENT_A);

    assertThat(evaluator.check(enterDepartment).allowed()).isFalse();
  }

  @Test
  void restrictsMembershipDepartmentScopeToSelectedActiveAppointment() {
    MyBatisAuthorizationBoundary declared =
        MyBatisAuthorizationBoundary.dynamic(
            ExampleAuthorizationDataStore.DOCUMENT_LIST_BOUNDARY);

    PredicateConstraint firstDepartment =
        constraint(
            boundaries.resolve(
                "DocumentMapper.list",
                declared,
                ExampleAuthorizationConfiguration.CAROL_MEMBERSHIP,
                attributes(ExampleAuthorizationDataStore.TARGET_TENANT, "department-a")));
    PredicateConstraint secondDepartment =
        constraint(
            boundaries.resolve(
                "DocumentMapper.list",
                declared,
                ExampleAuthorizationConfiguration.CAROL_MEMBERSHIP,
                attributes(ExampleAuthorizationDataStore.TARGET_TENANT, "department-b")));

    assertThat(firstDepartment.left().name()).isEqualTo("department_id");
    assertThat(secondDepartment.left().name()).isEqualTo("department_id");

    assertThatThrownBy(
            () ->
                boundaries.resolve(
                    "DocumentMapper.list",
                    declared,
                    ExampleAuthorizationConfiguration.CAROL_MEMBERSHIP,
                    attributes(
                        ExampleAuthorizationDataStore.TARGET_TENANT, "department-c")))
        .isInstanceOf(MyBatisAuthorizationException.class)
        .hasMessageContaining("not an active appointment");

    dataStore.deactivateAppointment(
        ExampleAuthorizationConfiguration.CAROL_MEMBERSHIP,
        ExampleAuthorizationDataStore.TARGET_TENANT,
        ExampleAuthorizationConfiguration.TARGET_DEPARTMENT_B);

    assertThatThrownBy(
            () ->
                boundaries.resolve(
                    "DocumentMapper.list",
                    declared,
                    ExampleAuthorizationConfiguration.CAROL_MEMBERSHIP,
                    attributes(
                        ExampleAuthorizationDataStore.TARGET_TENANT, "department-b")))
        .isInstanceOf(MyBatisAuthorizationException.class)
        .hasMessageContaining("not an active appointment");
  }

  private static PredicateConstraint constraint(MyBatisAuthorizationBoundary boundary) {
    return (PredicateConstraint) boundary.predicate().orElseThrow();
  }

  private static Map<AttributeRef, String> attributes(String tenantId) {
    return Map.of(ExampleAuthorizationConfiguration.EFFECTIVE_TENANT_ID, tenantId);
  }

  private static Map<AttributeRef, String> attributes(String tenantId, String departmentId) {
    return Map.of(
        ExampleAuthorizationConfiguration.EFFECTIVE_TENANT_ID,
        tenantId,
        ExampleAuthorizationConfiguration.CURRENT_DEPARTMENT_ID,
        departmentId);
  }
}
