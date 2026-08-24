package com.luokuiai.forga.core.eval;

import com.luokuiai.forga.core.model.ObjectRef;
import com.luokuiai.forga.core.model.RelationRef;
import com.luokuiai.forga.core.model.SubjectRef;
import com.luokuiai.forga.core.model.SubjectSetRef;
import com.luokuiai.forga.core.model.ConsistencyToken;
import com.luokuiai.forga.core.policy.CaveatExpression;
import com.luokuiai.forga.core.policy.CompiledPolicy;
import com.luokuiai.forga.core.policy.ExclusionExpression;
import com.luokuiai.forga.core.policy.IntersectionExpression;
import com.luokuiai.forga.core.policy.PermissionExpression;
import com.luokuiai.forga.core.policy.RelationExpression;
import com.luokuiai.forga.core.policy.TraversalExpression;
import com.luokuiai.forga.core.policy.UnionExpression;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Request-scoped authorization evaluator.
 */
public final class AuthorizationEvaluator {

  private final CompiledPolicy policy;

  private final RelationshipLookup relationships;

  private final ObjectListingLookup objectListings;

  private final EvaluationLimits limits;

  private final CaveatEvaluator caveats;

  private final ListingCursorCodec cursorCodec;

  /**
   * Creates an evaluator.
   *
   * @param policy compiled policy
   * @param relationships relationship lookup
   * @param limits evaluation limits
   */
  public AuthorizationEvaluator(
      CompiledPolicy policy, RelationshipLookup relationships, EvaluationLimits limits) {
    this(policy, relationships, null, limits, (caveat, request) -> false);
  }

  /**
   * Creates an evaluator with object listing support.
   *
   * @param policy compiled policy
   * @param relationships relationship lookup
   * @param objectListings reverse object listing lookup
   * @param limits evaluation limits
   */
  public AuthorizationEvaluator(
      CompiledPolicy policy,
      RelationshipLookup relationships,
      ObjectListingLookup objectListings,
      EvaluationLimits limits) {
    this(policy, relationships, objectListings, limits, (caveat, request) -> false);
  }

  /**
   * Creates an evaluator.
   *
   * @param policy compiled policy
   * @param relationships relationship lookup
   * @param limits evaluation limits
   * @param caveats caveat evaluator
   */
  public AuthorizationEvaluator(
      CompiledPolicy policy,
      RelationshipLookup relationships,
      EvaluationLimits limits,
      CaveatEvaluator caveats) {
    this(policy, relationships, null, limits, caveats);
  }

  /**
   * Creates an evaluator with object listing support.
   *
   * @param policy compiled policy
   * @param relationships relationship lookup
   * @param objectListings reverse object listing lookup
   * @param limits evaluation limits
   * @param caveats caveat evaluator
   */
  public AuthorizationEvaluator(
      CompiledPolicy policy,
      RelationshipLookup relationships,
      ObjectListingLookup objectListings,
      EvaluationLimits limits,
      CaveatEvaluator caveats) {
    this.policy = Objects.requireNonNull(policy, "policy is required");
    this.relationships = Objects.requireNonNull(relationships, "relationships is required");
    this.objectListings = objectListings;
    this.limits = Objects.requireNonNull(limits, "limits are required");
    this.caveats = Objects.requireNonNull(caveats, "caveats are required");
    this.cursorCodec = new ListingCursorCodec();
  }

  /**
   * Evaluates one check request.
   *
   * @param request check request
   * @return decision
   */
  public CheckDecision check(CheckRequest request) {
    Objects.requireNonNull(request, "request is required");
    return check(request, new EvaluationState());
  }

  /**
   * Evaluates a batch of check requests with shared request-scoped memoization.
   *
   * @param requests check requests
   * @return decisions in request order
   */
  public List<CheckDecision> bulkCheck(List<CheckRequest> requests) {
    List<CheckRequest> immutableRequests = List.copyOf(requests);
    if (immutableRequests.size() > limits.maxBatchSize()) {
      return immutableRequests.stream()
          .map(request -> new CheckDecision(request, false, DecisionReason.LIMIT_EXCEEDED))
          .toList();
    }
    Map<RelationLookupRequest, List<RelationshipEntry>> sharedCache = new HashMap<>();
    prefetchBulk(immutableRequests, new EvaluationState(sharedCache));
    return immutableRequests.stream()
        .map(request -> check(request, new EvaluationState(sharedCache)))
        .toList();
  }

  /**
   * Lists objects whose permission can be proven for a subject.
   *
   * @param request listing request
   * @return listing response
   */
  public ListObjectsResponse listObjects(ListObjectsRequest request) {
    Objects.requireNonNull(request, "request is required");
    if (objectListings == null) {
      return ListObjectsResponse.failure(request, DecisionReason.RESOLVER_FAILURE);
    }
    if (request.pageSize() > limits.maxBatchSize()) {
      return ListObjectsResponse.failure(request, DecisionReason.LIMIT_EXCEEDED);
    }
    PermissionExpression expression = policy.definition().permissions().get(request.permission());
    if (expression == null) {
      return ListObjectsResponse.failure(request, DecisionReason.UNKNOWN_PERMISSION);
    }
    ListingCursorState cursorState = cursorState(request);
    if (!cursorState.valid()) {
      return ListObjectsResponse.failure(request, DecisionReason.INVALID_CURSOR);
    }
    EvaluationState state = new EvaluationState(cursorState);
    Set<ObjectRef> objects =
        collectObjects(
            expression,
            request,
            request.objectType(),
            limits.maxIntermediateResults(),
            state,
            0);
    if (state.deniedReason != DecisionReason.NO_MATCH) {
      return ListObjectsResponse.failure(request, state.deniedReason);
    }
    List<ObjectRef> allObjects =
        objects.stream()
            .sorted(Comparator.comparing(ObjectRef::type).thenComparing(ObjectRef::id))
            .toList();
    if (allObjects.size() > limits.maxIntermediateResults()) {
      return ListObjectsResponse.failure(request, DecisionReason.LIMIT_EXCEEDED);
    }
    int toIndex = Math.min(cursorState.offset() + request.pageSize(), allObjects.size());
    List<ObjectRef> page =
        cursorState.offset() >= allObjects.size()
            ? List.of()
            : allObjects.subList(cursorState.offset(), toIndex);
    boolean hasLocalResults = toIndex < allObjects.size();
    Optional<ListObjectsCursor> nextCursor = Optional.empty();
    if (hasLocalResults) {
      nextCursor =
          Optional.of(
              cursor(request, state, toIndex, state.inputContinuationCursors));
    } else if (state.hasContinuation()) {
      nextCursor =
          Optional.of(
              cursor(request, state, 0, state.outputContinuationCursors));
    }
    return ListObjectsResponse.success(request, page, nextCursor);
  }

  private CheckDecision check(CheckRequest request, EvaluationState state) {
    state.beginDecision();
    PermissionExpression expression = policy.definition().permissions().get(request.permission());
    if (expression == null) {
      return new CheckDecision(request, false, DecisionReason.UNKNOWN_PERMISSION);
    }
    boolean allowed = evaluate(expression, request, request.object(), request.subject(), state, 0);
    DecisionReason reason = allowed ? DecisionReason.ALLOWED : state.deniedReason;
    List<ProofStep> proof = allowed ? state.proof : List.of();
    return new CheckDecision(request, allowed, reason, proof);
  }

  private void prefetchBulk(List<CheckRequest> requests, EvaluationState state) {
    List<BulkWork> frontier = new ArrayList<>();
    for (CheckRequest request : requests) {
      PermissionExpression expression = policy.definition().permissions().get(request.permission());
      if (expression != null) {
        frontier.add(new BulkWork(request, request.object(), expression));
      }
    }
    Set<BulkWork> visited = new HashSet<>();
    while (!frontier.isEmpty()) {
      Map<RelationLookupRequest, List<BulkContinuation>> continuations = new LinkedHashMap<>();
      ArrayDeque<BulkWork> pending = new ArrayDeque<>(frontier);
      while (!pending.isEmpty()) {
        expandBulkWork(pending.removeFirst(), pending, continuations, visited);
      }
      if (continuations.isEmpty() || !prefetchFrontier(continuations.keySet(), state)) {
        return;
      }
      frontier = nextBulkFrontier(continuations, state);
    }
  }

  private void expandBulkWork(
      BulkWork work,
      ArrayDeque<BulkWork> pending,
      Map<RelationLookupRequest, List<BulkContinuation>> continuations,
      Set<BulkWork> visited) {
    if (!visited.add(work)) {
      return;
    }
    PermissionExpression expression = work.expression();
    if (expression instanceof RelationExpression relationExpression) {
      addBulkContinuation(
          continuations,
          new RelationLookupRequest(work.object(), relationExpression.relation()),
          new BulkContinuation(work.request(), Optional.empty()));
    } else if (expression instanceof UnionExpression unionExpression) {
      unionExpression.expressions().stream()
          .map(child -> new BulkWork(work.request(), work.object(), child))
          .forEach(pending::addLast);
    } else if (expression instanceof IntersectionExpression intersectionExpression) {
      intersectionExpression.expressions().stream()
          .map(child -> new BulkWork(work.request(), work.object(), child))
          .forEach(pending::addLast);
    } else if (expression instanceof ExclusionExpression exclusionExpression) {
      pending.addLast(
          new BulkWork(work.request(), work.object(), exclusionExpression.base()));
      pending.addLast(
          new BulkWork(work.request(), work.object(), exclusionExpression.excluded()));
    } else if (expression instanceof TraversalExpression traversalExpression) {
      addBulkContinuation(
          continuations,
          new RelationLookupRequest(work.object(), traversalExpression.relation()),
          new BulkContinuation(
              work.request(), Optional.of(traversalExpression.expression())));
    } else if (expression instanceof CaveatExpression caveatExpression
        && caveats.evaluate(caveatExpression.caveat(), work.request())) {
      pending.addLast(
          new BulkWork(work.request(), work.object(), caveatExpression.expression()));
    }
  }

  private static void addBulkContinuation(
      Map<RelationLookupRequest, List<BulkContinuation>> continuations,
      RelationLookupRequest request,
      BulkContinuation continuation) {
    continuations.computeIfAbsent(request, ignored -> new ArrayList<>()).add(continuation);
  }

  private boolean prefetchFrontier(
      Set<RelationLookupRequest> frontier, EvaluationState state) {
    List<RelationLookupRequest> missing =
        frontier.stream().filter(request -> !state.cache.containsKey(request)).toList();
    if (missing.isEmpty()) {
      return true;
    }
    if (!state.allowResolverCall()) {
      return false;
    }
    Map<RelationLookupRequest, List<RelationshipEntry>> resolved;
    try {
      resolved = relationships.resolve(missing, state.deadline);
    } catch (RelationshipLookupException exception) {
      state.deniedReason = exception.reason();
      return false;
    } catch (RuntimeException exception) {
      state.deniedReason = DecisionReason.RESOLVER_FAILURE;
      return false;
    }
    if (resolved == null) {
      state.deniedReason = DecisionReason.RESOLVER_FAILURE;
      return false;
    }
    for (RelationLookupRequest request : missing) {
      List<RelationshipEntry> entries =
          List.copyOf(resolved.getOrDefault(request, List.of()));
      if (!state.allowIntermediateResults(entries.size())) {
        return false;
      }
      state.cache.put(request, entries);
    }
    return true;
  }

  private static List<BulkWork> nextBulkFrontier(
      Map<RelationLookupRequest, List<BulkContinuation>> continuations,
      EvaluationState state) {
    List<BulkWork> next = new ArrayList<>();
    continuations.forEach(
        (lookup, paths) ->
            state.cache.getOrDefault(lookup, List.of()).stream()
                .flatMap(entry -> entry.subjectSet().stream())
                .forEach(
                    subjectSet ->
                        paths.forEach(
                            path ->
                                next.add(
                                    new BulkWork(
                                        path.request(),
                                        subjectSet.object(),
                                        path.traversalExpression()
                                            .orElseGet(
                                                () ->
                                                    new RelationExpression(
                                                        subjectSet.relation())))))));
    return next;
  }

  private boolean evaluate(
      PermissionExpression expression,
      CheckRequest request,
      ObjectRef object,
      SubjectRef subject,
      EvaluationState state,
      int depth) {
    if (!state.allowProgress(depth)) {
      return false;
    }
    if (expression instanceof RelationExpression relationExpression) {
      return matchesRelation(object, relationExpression.relation(), subject, state, depth);
    }
    if (expression instanceof UnionExpression unionExpression) {
      for (PermissionExpression branch : unionExpression.expressions()) {
        int checkpoint = state.proof.size();
        if (evaluate(branch, request, object, subject, state, depth + 1)) {
          return true;
        }
        state.rollbackProof(checkpoint);
      }
      return false;
    }
    if (expression instanceof IntersectionExpression intersectionExpression) {
      int checkpoint = state.proof.size();
      for (PermissionExpression branch : intersectionExpression.expressions()) {
        if (!evaluate(branch, request, object, subject, state, depth + 1)) {
          state.rollbackProof(checkpoint);
          return false;
        }
      }
      return true;
    }
    if (expression instanceof ExclusionExpression exclusionExpression) {
      int checkpoint = state.proof.size();
      if (!evaluate(exclusionExpression.base(), request, object, subject, state, depth + 1)) {
        state.rollbackProof(checkpoint);
        return false;
      }
      int baseProofEnd = state.proof.size();
      if (evaluate(
          exclusionExpression.excluded(), request, object, subject, state, depth + 1)) {
        state.rollbackProof(checkpoint);
        return false;
      }
      state.rollbackProof(baseProofEnd);
      return true;
    }
    if (expression instanceof TraversalExpression traversalExpression) {
      return traverse(traversalExpression, request, object, subject, state, depth);
    }
    if (expression instanceof CaveatExpression caveatExpression) {
      return caveats.evaluate(caveatExpression.caveat(), request)
          && evaluate(caveatExpression.expression(), request, object, subject, state, depth + 1);
    }
    return false;
  }

  private boolean traverse(
      TraversalExpression expression,
      CheckRequest request,
      ObjectRef object,
      SubjectRef subject,
      EvaluationState state,
      int depth) {
    List<SubjectSetRef> subjectSets =
        lookup(new RelationLookupRequest(object, expression.relation()), state).stream()
            .flatMap(entry -> entry.subjectSet().stream())
            .toList();
    for (SubjectSetRef subjectSet : subjectSets) {
      int checkpoint = state.proof.size();
      if (evaluate(
          expression.expression(),
          request,
          subjectSet.object(),
          subject,
          state,
          depth + 1)) {
        return true;
      }
      state.rollbackProof(checkpoint);
    }
    return false;
  }

  private boolean matchesRelation(
      ObjectRef object,
      RelationRef relation,
      SubjectRef subject,
      EvaluationState state,
      int depth) {
    RelationLookupRequest request = new RelationLookupRequest(object, relation);
    if (!state.enter(request)) {
      return false;
    }
    try {
      for (RelationshipEntry entry : lookup(request, state)) {
        int checkpoint = state.proof.size();
        if (matchesEntry(entry, object, relation, subject, state, depth)) {
          return true;
        }
        state.rollbackProof(checkpoint);
      }
      return false;
    } finally {
      state.exit(request);
    }
  }

  private boolean matchesEntry(
      RelationshipEntry entry,
      ObjectRef object,
      RelationRef relation,
      SubjectRef subject,
      EvaluationState state,
      int depth) {
    if (entry.subject().filter(subject::equals).isPresent()) {
      entry.subject()
          .filter(subject::equals)
          .ifPresent(matched -> state.proof.add(new ProofStep(object, relation, matched)));
      return true;
    }
    return entry.subjectSet()
        .map(
            subjectSet ->
                matchesRelation(
                    subjectSet.object(), subjectSet.relation(), subject, state, depth + 1))
        .orElse(false);
  }

  private List<RelationshipEntry> lookup(RelationLookupRequest request, EvaluationState state) {
    if (!state.allowResolverCall(request)) {
      return List.of();
    }
    if (state.cache.containsKey(request)) {
      List<RelationshipEntry> entries = state.cache.get(request);
      return state.allowIntermediateResults(entries.size()) ? entries : List.of();
    }
    List<RelationshipEntry> entries;
    try {
      entries =
          List.copyOf(
              relationships
                  .resolve(List.of(request), state.deadline)
                  .getOrDefault(request, List.of()));
    } catch (RelationshipLookupException exception) {
      state.forgetResolverCall(request);
      state.deniedReason = exception.reason();
      return List.of();
    } catch (RuntimeException exception) {
      state.forgetResolverCall(request);
      state.deniedReason = DecisionReason.RESOLVER_FAILURE;
      return List.of();
    }
    if (!state.allowIntermediateResults(entries.size())) {
      state.forgetResolverCall(request);
      return List.of();
    }
    state.cache.put(request, entries);
    return entries;
  }

  private Set<ObjectRef> collectObjects(
      PermissionExpression expression,
      ListObjectsRequest request,
      String objectType,
      int resolverLimit,
      EvaluationState state,
      int depth) {
    if (!state.allowProgress(depth)) {
      return Set.of();
    }
    if (expression instanceof RelationExpression relationExpression) {
      return reverseLookup(
          objectType,
          relationExpression.relation(),
          new DirectReverseLookupSubject(request.subject()),
          resolverLimit,
          state);
    }
    if (expression instanceof UnionExpression unionExpression) {
      Set<ObjectRef> objects = new LinkedHashSet<>();
      for (PermissionExpression branch : unionExpression.expressions()) {
        objects.addAll(
            collectObjects(branch, request, objectType, resolverLimit, state, depth + 1));
      }
      return objects;
    }
    if (expression instanceof IntersectionExpression intersectionExpression) {
      Set<ObjectRef> objects = null;
      for (PermissionExpression branch : intersectionExpression.expressions()) {
        Set<ObjectRef> branchObjects =
            collectObjects(branch, request, objectType, resolverLimit, state, depth + 1);
        objects =
            objects == null
                ? new LinkedHashSet<>(branchObjects)
                : retain(objects, branchObjects);
      }
      return objects == null ? Set.of() : objects;
    }
    if (expression instanceof ExclusionExpression exclusionExpression) {
      Set<ObjectRef> objects =
          new LinkedHashSet<>(
              collectObjects(
                  exclusionExpression.base(),
                  request,
                  objectType,
                  resolverLimit,
                  state,
                  depth + 1));
      objects.removeAll(
          collectObjects(
              exclusionExpression.excluded(),
              request,
              objectType,
              resolverLimit,
              state,
              depth + 1));
      return objects;
    }
    if (expression instanceof TraversalExpression traversalExpression) {
      return collectTraversalObjects(
          traversalExpression, request, objectType, resolverLimit, state);
    }
    if (expression instanceof CaveatExpression caveatExpression) {
      CheckRequest caveatRequest =
          new CheckRequest(
              new ObjectRef(objectType, "listing"),
              request.permission(),
              request.subject(),
              request.attributes());
      if (!caveats.evaluate(caveatExpression.caveat(), caveatRequest)) {
        return Set.of();
      }
      return collectObjects(
          caveatExpression.expression(), request, objectType, resolverLimit, state, depth + 1);
    }
    return Set.of();
  }

  private Set<ObjectRef> collectTraversalObjects(
      TraversalExpression expression,
      ListObjectsRequest request,
      String objectType,
      int resolverLimit,
      EvaluationState state) {
    if (expression.objectType().isEmpty()
        || !(expression.expression() instanceof RelationExpression relationExpression)) {
      state.deniedReason = DecisionReason.RESOLVER_FAILURE;
      return Set.of();
    }
    Set<ObjectRef> nextObjects =
        reverseLookup(
            expression.objectType().orElseThrow(),
            relationExpression.relation(),
            new DirectReverseLookupSubject(request.subject()),
            resolverLimit,
            state);
    Set<ObjectRef> objects = new LinkedHashSet<>();
    for (ObjectRef nextObject : nextObjects) {
      objects.addAll(
          reverseLookup(
              objectType,
              expression.relation(),
              new SubjectSetReverseLookupSubject(
                  new SubjectSetRef(nextObject, relationExpression.relation())),
              resolverLimit,
              state));
    }
    return objects;
  }

  private Set<ObjectRef> reverseLookup(
      String objectType,
      RelationRef relation,
      ReverseLookupSubject subject,
      int limit,
      EvaluationState state) {
    ReverseRelationLookupRequest request =
        new ReverseRelationLookupRequest(
            objectType,
            relation,
            subject,
            state.continuation(reverseLookupKey(objectType, relation, subject)),
            state.consistency,
            limit);
    if (state.reverseCache.containsKey(request)) {
      return state.reverseCache.get(request);
    }
    if (!state.allowResolverCall()) {
      return Set.of();
    }
    ObjectListingPage page;
    try {
      page = objectListings.resolve(List.of(request), state.deadline).get(request);
    } catch (RelationshipLookupException exception) {
      state.deniedReason = exception.reason();
      return Set.of();
    } catch (RuntimeException exception) {
      state.deniedReason = DecisionReason.RESOLVER_FAILURE;
      return Set.of();
    }
    if (page == null) {
      page = new ObjectListingPage(List.of());
    }
    if (!state.allowIntermediateResults(page.objects().size())) {
      return Set.of();
    }
    if (!state.acceptConsistency(page.consistency())) {
      return Set.of();
    }
    state.recordContinuation(
        reverseLookupKey(objectType, relation, subject), page.nextCursor());
    Set<ObjectRef> objects = new LinkedHashSet<>();
    page.objects().stream()
        .filter(object -> objectType.equals(object.type()))
        .forEach(objects::add);
    state.reverseCache.put(request, objects);
    return objects;
  }

  private static Set<ObjectRef> retain(Set<ObjectRef> left, Set<ObjectRef> right) {
    left.retainAll(right);
    return left;
  }

  private ListingCursorState cursorState(ListObjectsRequest request) {
    if (request.cursor().isEmpty()) {
      return ListingCursorState.initial(request.consistency());
    }
    Optional<String> decoded = cursorCodec.decode(request.cursor().orElseThrow());
    if (decoded.isEmpty()) {
      return ListingCursorState.invalid();
    }
    String payload = decoded.orElseThrow();
    List<String> parts = payload.lines().toList();
    if (parts.size() != 4 || !decode(parts.get(0)).equals(cursorBinding(request))) {
      return ListingCursorState.invalid();
    }
    Optional<ConsistencyToken> consistency = token(parts.get(2));
    if (request.consistency().isPresent()
        && !request.consistency().equals(consistency)) {
      return ListingCursorState.invalid();
    }
    try {
      int offset = Integer.parseInt(parts.get(1));
      if (offset < 0) {
        return ListingCursorState.invalid();
      }
      return new ListingCursorState(true, offset, consistency, continuations(parts.get(3)));
    } catch (IllegalArgumentException exception) {
      return ListingCursorState.invalid();
    }
  }

  private ListObjectsCursor cursor(
      ListObjectsRequest request,
      EvaluationState state,
      int offset,
      Map<String, ListObjectsCursor> continuations) {
    String payload =
        String.join(
            "\n",
            encode(cursorBinding(request)),
            String.valueOf(offset),
            state.consistency.map(ConsistencyToken::value).orElse("-"),
            continuations(continuations));
    return cursorCodec.encode(payload);
  }

  private String cursorBinding(ListObjectsRequest request) {
    String attributes =
        request.attributes().entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(attribute -> attribute.name())))
            .map(entry -> entry.getKey().name() + "=" + entry.getValue())
            .collect(Collectors.joining("&"));
    return String.join(
        "\n",
        "v2",
        policy.fingerprint(),
        request.objectType(),
        request.permission().name(),
        request.subject().type(),
        request.subject().id(),
        attributes);
  }

  private static Optional<ConsistencyToken> token(String value) {
    return "-".equals(value) ? Optional.empty() : Optional.of(new ConsistencyToken(value));
  }

  private static String continuations(Map<String, ListObjectsCursor> cursors) {
    if (cursors.isEmpty()) {
      return "-";
    }
    return cursors.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(
            entry ->
                encode(entry.getKey())
                    + "="
                    + encode(entry.getValue().token()))
        .collect(Collectors.joining(","));
  }

  private static Map<String, ListObjectsCursor> continuations(String value) {
    if ("-".equals(value)) {
      return Map.of();
    }
    Map<String, ListObjectsCursor> cursors = new HashMap<>();
    for (String entry : value.split(",")) {
      int separator = entry.indexOf('=');
      if (separator < 1) {
        throw new NumberFormatException("invalid continuation entry");
      }
      cursors.put(
          decode(entry.substring(0, separator)),
          new ListObjectsCursor(decode(entry.substring(separator + 1))));
    }
    return cursors;
  }

  private static String reverseLookupKey(
      String objectType, RelationRef relation, ReverseLookupSubject subject) {
    return objectType + "\u001f" + relation.name() + "\u001f" + subjectKey(subject);
  }

  private static String subjectKey(ReverseLookupSubject subject) {
    if (subject instanceof DirectReverseLookupSubject direct) {
      return "direct:"
          + direct.subject().type()
          + "\u001f"
          + direct.subject().id();
    }
    if (subject instanceof SubjectSetReverseLookupSubject subjectSet) {
      return "set:"
          + subjectSet.subjectSet().object().type()
          + "\u001f"
          + subjectSet.subjectSet().object().id()
          + "\u001f"
          + subjectSet.subjectSet().relation().name();
    }
    throw new IllegalArgumentException("unsupported reverse lookup subject");
  }

  private static String encode(String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String decode(String value) {
    return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
  }

  private record BulkWork(
      CheckRequest request, ObjectRef object, PermissionExpression expression) {
  }

  private record BulkContinuation(
      CheckRequest request, Optional<PermissionExpression> traversalExpression) {
  }

  private record ListingCursorState(
      boolean valid,
      int offset,
      Optional<ConsistencyToken> consistency,
      Map<String, ListObjectsCursor> continuations) {

    private ListingCursorState {
      consistency = consistency == null ? Optional.empty() : consistency;
      continuations = Map.copyOf(continuations);
    }

    static ListingCursorState initial(Optional<ConsistencyToken> consistency) {
      return new ListingCursorState(true, 0, consistency, Map.of());
    }

    static ListingCursorState invalid() {
      return new ListingCursorState(false, 0, Optional.empty(), Map.of());
    }
  }

  private final class EvaluationState {

    private final Map<RelationLookupRequest, List<RelationshipEntry>> cache;

    private final Set<RelationLookupRequest> accountedLookups = new HashSet<>();

    private final Map<ReverseRelationLookupRequest, Set<ObjectRef>> reverseCache = new HashMap<>();

    private final Map<String, ListObjectsCursor> inputContinuationCursors = new HashMap<>();

    private final Map<String, ListObjectsCursor> outputContinuationCursors = new HashMap<>();

    private final List<ProofStep> proof = new ArrayList<>();

    private final Set<RelationLookupRequest> activePath = new HashSet<>();

    private int resolverCalls;

    private int visitedNodes;

    private final Optional<Instant> deadline;

    private Optional<ConsistencyToken> consistency = Optional.empty();

    private DecisionReason deniedReason = DecisionReason.NO_MATCH;

    EvaluationState() {
      this(new HashMap<>());
    }

    EvaluationState(Map<RelationLookupRequest, List<RelationshipEntry>> cache) {
      this.cache = Objects.requireNonNull(cache, "cache is required");
      deadline = limits.timeout().map(timeout -> Instant.now().plus(timeout));
    }

    EvaluationState(ListingCursorState cursorState) {
      this();
      consistency = cursorState.consistency();
      inputContinuationCursors.putAll(cursorState.continuations());
    }

    void beginDecision() {
      proof.clear();
      activePath.clear();
      deniedReason = DecisionReason.NO_MATCH;
    }

    void rollbackProof(int size) {
      proof.subList(size, proof.size()).clear();
    }

    boolean allowProgress(int depth) {
      if (deadline.filter(value -> !Instant.now().isBefore(value)).isPresent()) {
        deniedReason = DecisionReason.DEADLINE_EXCEEDED;
        return false;
      }
      if (depth > limits.maxDepth()) {
        deniedReason = DecisionReason.LIMIT_EXCEEDED;
        return false;
      }
      visitedNodes++;
      if (visitedNodes > limits.maxVisitedNodes()) {
        deniedReason = DecisionReason.LIMIT_EXCEEDED;
        return false;
      }
      return true;
    }

    boolean allowResolverCall() {
      resolverCalls++;
      if (resolverCalls > limits.maxResolverCalls()) {
        deniedReason = DecisionReason.LIMIT_EXCEEDED;
        return false;
      }
      return true;
    }

    boolean allowResolverCall(RelationLookupRequest request) {
      if (accountedLookups.contains(request)) {
        return true;
      }
      if (!allowResolverCall()) {
        return false;
      }
      accountedLookups.add(request);
      return true;
    }

    void forgetResolverCall(RelationLookupRequest request) {
      accountedLookups.remove(request);
    }

    boolean allowIntermediateResults(int count) {
      if (count > limits.maxIntermediateResults()) {
        deniedReason = DecisionReason.LIMIT_EXCEEDED;
        return false;
      }
      return true;
    }

    boolean enter(RelationLookupRequest request) {
      if (activePath.contains(request)) {
        deniedReason = DecisionReason.CYCLE_DETECTED;
        return false;
      }
      activePath.add(request);
      return true;
    }

    void exit(RelationLookupRequest request) {
      activePath.remove(request);
    }

    Optional<ListObjectsCursor> continuation(String key) {
      return Optional.ofNullable(inputContinuationCursors.get(key));
    }

    void recordContinuation(String key, Optional<ListObjectsCursor> cursor) {
      if (cursor.isPresent()) {
        outputContinuationCursors.put(key, cursor.orElseThrow());
      } else {
        outputContinuationCursors.remove(key);
      }
    }

    boolean hasContinuation() {
      return !outputContinuationCursors.isEmpty();
    }

    boolean acceptConsistency(Optional<ConsistencyToken> token) {
      if (token.isEmpty()) {
        return true;
      }
      if (consistency.isEmpty()) {
        consistency = token;
        return true;
      }
      if (!consistency.equals(token)) {
        deniedReason = DecisionReason.CONSISTENCY_CONFLICT;
        return false;
      }
      return true;
    }
  }
}
