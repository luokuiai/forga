package com.luokuiai.forga.example;

import com.luokuiai.forga.core.model.PermissionRef;
import com.luokuiai.forga.core.model.SubjectRef;
import com.luokuiai.forga.core.model.ObjectRef;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/** Persistence-shaped, host-owned authorization data used by the runnable example. */
final class ExampleAuthorizationDataStore {

  static final String READER_ROLE = "reader";

  static final String APPOINTED_READER_ROLE = "appointed-reader";

  static final String HOME_TENANT = "tenant-home";

  static final String TARGET_TENANT = "tenant-target";

  static final String DOCUMENT_LIST_BOUNDARY = "documents:list";

  private final AtomicReference<Snapshot> current = new AtomicReference<>(initialSnapshot());

  Snapshot snapshot() {
    return current.get();
  }

  void assignRole(String tenantId, SubjectRef subject, String role) {
    update(snapshot -> snapshot.withRoleAssignment(new RoleAssignment(tenantId, subject, role)));
  }

  void grantPermission(String tenantId, String role, PermissionRef permission) {
    update(
        snapshot ->
            snapshot.withPermissionGrant(new PermissionGrant(tenantId, role, permission)));
  }

  void revokePermission(String tenantId, String role, PermissionRef permission) {
    update(
        snapshot ->
            snapshot.withoutPermissionGrant(new PermissionGrant(tenantId, role, permission)));
  }

  void setDataScope(
      String tenantId, String role, String boundaryId, DocumentDataScope scope) {
    update(
        snapshot ->
            snapshot.withDataScopeGrant(
                new DataScopeGrant(tenantId, role, boundaryId, scope)));
  }

  void removeDataScope(String tenantId, String role, String boundaryId) {
    update(snapshot -> snapshot.withoutDataScopeGrant(tenantId, role, boundaryId));
  }

  void activateAppointment(SubjectRef subject, String tenantId, ObjectRef department) {
    update(
        snapshot ->
            snapshot.withAppointment(new ConcurrentAppointment(subject, tenantId, department)));
  }

  void deactivateAppointment(SubjectRef subject, String tenantId, ObjectRef department) {
    update(
        snapshot ->
            snapshot.withoutAppointment(new ConcurrentAppointment(subject, tenantId, department)));
  }

  void reset() {
    current.set(initialSnapshot());
  }

  private void update(UnaryOperator<Snapshot> mutation) {
    current.updateAndGet(mutation);
  }

  private static Snapshot initialSnapshot() {
    return new Snapshot(
        Set.of(
            new RoleAssignment(
                HOME_TENANT, ExampleAuthorizationConfiguration.CAROL, READER_ROLE),
            new RoleAssignment(
                TARGET_TENANT,
                ExampleAuthorizationConfiguration.CAROL_MEMBERSHIP,
                APPOINTED_READER_ROLE)),
        Set.of(
            new PermissionGrant(
                HOME_TENANT, READER_ROLE, ExampleAuthorizationConfiguration.VIEW_DOCUMENT),
            new PermissionGrant(
                TARGET_TENANT,
                APPOINTED_READER_ROLE,
                ExampleAuthorizationConfiguration.VIEW_DOCUMENT)),
        Set.of(
            new DataScopeGrant(
                HOME_TENANT, READER_ROLE, DOCUMENT_LIST_BOUNDARY, DocumentDataScope.OWNER),
            new DataScopeGrant(
                TARGET_TENANT,
                APPOINTED_READER_ROLE,
                DOCUMENT_LIST_BOUNDARY,
                DocumentDataScope.DEPARTMENT)),
        Set.of(
            new ConcurrentAppointment(
                ExampleAuthorizationConfiguration.CAROL,
                HOME_TENANT,
                ExampleAuthorizationConfiguration.HOME_DEPARTMENT),
            new ConcurrentAppointment(
                ExampleAuthorizationConfiguration.CAROL_MEMBERSHIP,
                TARGET_TENANT,
                ExampleAuthorizationConfiguration.TARGET_DEPARTMENT_A),
            new ConcurrentAppointment(
                ExampleAuthorizationConfiguration.CAROL_MEMBERSHIP,
                TARGET_TENANT,
                ExampleAuthorizationConfiguration.TARGET_DEPARTMENT_B)));
  }

  enum DocumentDataScope {
    OWNER(1),
    DEPARTMENT(2),
    TENANT(3);

    private final int breadth;

    DocumentDataScope(int breadth) {
      this.breadth = breadth;
    }
  }

  record RoleAssignment(String tenantId, SubjectRef subject, String role) { }

  record PermissionGrant(String tenantId, String role, PermissionRef permission) { }

  record DataScopeGrant(
      String tenantId, String role, String boundaryId, DocumentDataScope scope) { }

  record ConcurrentAppointment(SubjectRef subject, String tenantId, ObjectRef department) { }

  record Snapshot(
      Set<RoleAssignment> roleAssignments,
      Set<PermissionGrant> permissionGrants,
      Set<DataScopeGrant> dataScopeGrants,
      Set<ConcurrentAppointment> appointments) {

    Snapshot {
      roleAssignments = Set.copyOf(roleAssignments);
      permissionGrants = Set.copyOf(permissionGrants);
      dataScopeGrants = Set.copyOf(dataScopeGrants);
      appointments = Set.copyOf(appointments);
    }

    boolean hasPermission(SubjectRef subject, String tenantId, PermissionRef permission) {
      Set<String> roles = roles(subject, tenantId);
      return permissionGrants.stream()
          .filter(grant -> tenantId.equals(grant.tenantId()))
          .anyMatch(grant -> roles.contains(grant.role()) && permission.equals(grant.permission()));
    }

    Optional<DocumentDataScope> effectiveDataScope(
        SubjectRef subject, String tenantId, String boundaryId) {
      Set<String> roles = roles(subject, tenantId);
      return dataScopeGrants.stream()
          .filter(grant -> tenantId.equals(grant.tenantId()))
          .filter(grant -> roles.contains(grant.role()))
          .filter(grant -> boundaryId.equals(grant.boundaryId()))
          .map(DataScopeGrant::scope)
          .max(Comparator.comparingInt(scope -> scope.breadth));
    }

    Snapshot withRoleAssignment(RoleAssignment assignment) {
      return new Snapshot(
          with(roleAssignments, assignment), permissionGrants, dataScopeGrants, appointments);
    }

    Snapshot withPermissionGrant(PermissionGrant grant) {
      return new Snapshot(
          roleAssignments, with(permissionGrants, grant), dataScopeGrants, appointments);
    }

    Snapshot withoutPermissionGrant(PermissionGrant grant) {
      return new Snapshot(
          roleAssignments, without(permissionGrants, grant), dataScopeGrants, appointments);
    }

    Snapshot withDataScopeGrant(DataScopeGrant grant) {
      Set<DataScopeGrant> remaining =
          dataScopeGrants.stream()
              .filter(
                  existing ->
                      !existing.role().equals(grant.role())
                          || !existing.tenantId().equals(grant.tenantId())
                          || !existing.boundaryId().equals(grant.boundaryId()))
              .collect(Collectors.toSet());
      remaining.add(grant);
      return new Snapshot(roleAssignments, permissionGrants, remaining, appointments);
    }

    Snapshot withoutDataScopeGrant(String tenantId, String role, String boundaryId) {
      Set<DataScopeGrant> remaining =
          dataScopeGrants.stream()
              .filter(
                  grant ->
                      !grant.tenantId().equals(tenantId)
                          || !grant.role().equals(role)
                          || !grant.boundaryId().equals(boundaryId))
              .collect(Collectors.toSet());
      return new Snapshot(roleAssignments, permissionGrants, remaining, appointments);
    }

    boolean hasActiveAppointment(SubjectRef subject, String tenantId, ObjectRef department) {
      return appointments.contains(new ConcurrentAppointment(subject, tenantId, department));
    }

    Set<SubjectRef> appointedSubjects(ObjectRef department) {
      return appointments.stream()
          .filter(appointment -> department.equals(appointment.department()))
          .map(ConcurrentAppointment::subject)
          .collect(Collectors.toUnmodifiableSet());
    }

    Snapshot withAppointment(ConcurrentAppointment appointment) {
      return new Snapshot(
          roleAssignments,
          permissionGrants,
          dataScopeGrants,
          with(appointments, appointment));
    }

    Snapshot withoutAppointment(ConcurrentAppointment appointment) {
      return new Snapshot(
          roleAssignments,
          permissionGrants,
          dataScopeGrants,
          without(appointments, appointment));
    }

    private Set<String> roles(SubjectRef subject, String tenantId) {
      return roleAssignments.stream()
          .filter(assignment -> tenantId.equals(assignment.tenantId()))
          .filter(assignment -> subject.equals(assignment.subject()))
          .map(RoleAssignment::role)
          .collect(Collectors.toUnmodifiableSet());
    }

    private static <T> Set<T> with(Set<T> values, T value) {
      HashSet<T> copy = new HashSet<>(values);
      copy.add(value);
      return Set.copyOf(copy);
    }

    private static <T> Set<T> without(Set<T> values, T value) {
      HashSet<T> copy = new HashSet<>(values);
      copy.remove(value);
      return Set.copyOf(copy);
    }
  }
}
