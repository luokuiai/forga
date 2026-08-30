package com.luokuiai.forga.core.eval;

import com.luokuiai.forga.core.model.AttributeRef;
import com.luokuiai.forga.core.model.ObjectRef;
import com.luokuiai.forga.core.model.RelationRef;
import com.luokuiai.forga.core.model.SubjectRef;
import com.luokuiai.forga.core.model.SubjectSetRef;
import com.luokuiai.forga.core.model.ConsistencyToken;
import com.luokuiai.forga.core.policy.CaveatExpression;
import com.luokuiai.forga.core.policy.CompiledPolicy;
import com.luokuiai.forga.core.policy.ExclusionExpression;
import com.luokuiai.forga.core.policy.GrantExpression;
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

  private final AttributeLookup attributes;

  private final PermissionGrantLookup grants;

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
    this(policy, relationships, null, limits, CaveatEvaluator.denyAll());
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
    this(policy, relationships, objectListings, limits, CaveatEvaluator.denyAll());
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
    this(
        policy,
        relationships,
        objectListings,
        limits,
        caveats,
        AttributeLookup.unavailable(),
        PermissionGrantLookup.denyAll());
  }

  /**
   * Creates an evaluator with object listing and dynamic grant support.
   *
   * @param policy compiled policy
   * @param relationships relationship lookup
   * @param objectListings reverse object listing lookup
   * @param limits evaluation limits
   * @param caveats caveat evaluator
   * @param attributes host-owned object attribute lookup
   * @param grants host-owned effective permission grant lookup
   */
  public AuthorizationEvaluator(
      CompiledPolicy policy,
      RelationshipLookup relationships,
      ObjectListingLookup objectListings,
      EvaluationLimits limits,
      CaveatEvaluator caveats,
      AttributeLookup attributes,
      PermissionGrantLookup grants) {
    this.policy = Objects.requireNonNull(policy, "policy is required");
    this.relationships = Objects.requireNonNull(relationships, "relationships is required");
    this.objectListings = objectListings;
    this.limits = Objects.requireNonNull(limits, "limits are required");
    this.caveats = Objects.requireNonNull(caveats, "caveats are required");
    this.attributes = Objects.requireNonNull(attributes, "attributes are required");
    this.grants = Objects.requireNonNull(grants, "grants are required");
    this.cursorCodec = new ListingCursorCodec();
  }

  /**
   * Evaluates one check request.
   *
   * <p>This method is intended for one independently identified object. Do not call it once per
   * row of a collection; each invocation has independent lookup state and can produce N+1 host
   * queries. Use {@link #bulkCheck(List)} for a bounded batch, or a set-oriented query constraint
   * for business list pages.
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
   * <p>Prefer this method when several already identified objects require decisions. Host grant
   * and relationship implementations should resolve submitted batches with set-oriented lookups
   * instead of querying once per request.
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
    Map<CheckRequest, Boolean> sharedGrantCache = new HashMap<>();
    Map<CheckRequest, DecisionReason> sharedGrantFailures = new HashMap<>();
    Map<AttributeLookupRequest, Map<AttributeRef, String>> sharedAttributeCache = new HashMap<>();
    Map<AttributeLookupRequest, DecisionReason> sharedAttributeFailures = new HashMap<>();
    EvaluationState prefetchState =
        new EvaluationState(
            sharedCache,
            sharedGrantCache,
            sharedGrantFailures,
            sharedAttributeCache,
            sharedAttributeFailures);
    prefetchBulk(immutableRequests, prefetchState);
    return immutableRequests.stream()
        .map(
            request ->
                check(
                    request,
                    new EvaluationState(
                        sharedCache,
                        sharedGrantCache,
                        sharedGrantFailures,
                        sharedAttributeCache,
                        sharedAttributeFailures,
                        prefetchState.consistency)))
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
      Set<CheckRequest> grantRequests = new LinkedHashSet<>();
      ArrayDeque<BulkWork> pending = new ArrayDeque<>(frontier);
      while (true) {
        List<BulkWork> caveatWorks = new ArrayList<>();
        while (!pending.isEmpty()) {
          expandBulkWork(
              pending.removeFirst(),
              pending,
              continuations,
              grantRequests,
              caveatWorks,
              visited);
        }
        if (caveatWorks.isEmpty()) {
          break;
        }
        if (!prefetchCaveatAttributes(caveatWorks, state)) {
          return;
        }
        for (BulkWork caveatWork : caveatWorks) {
          CaveatExpression caveatExpression = (CaveatExpression) caveatWork.expression();
          if (evaluateCaveat(
              caveatExpression.caveat(),
              caveatWork.request(),
              caveatWork.object(),
              caveatWork.request().subject(),
              state)) {
            pending.addLast(
                new BulkWork(
                    caveatWork.request(),
                    caveatWork.object(),
                    caveatExpression.expression()));
          }
        }
      }
      if (!prefetchGrants(grantRequests, state)) {
        return;
      }
      if (continuations.isEmpty()) {
        return;
      }
      if (!prefetchFrontier(continuations.keySet(), state)) {
        return;
      }
      frontier = nextBulkFrontier(continuations, state);
    }
  }

  private void expandBulkWork(
      BulkWork work,
      ArrayDeque<BulkWork> pending,
      Map<RelationLookupRequest, List<BulkContinuation>> continuations,
      Set<CheckRequest> grantRequests,
      List<BulkWork> caveatWorks,
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
    } else if (expression instanceof GrantExpression) {
      grantRequests.add(grantRequest(work.request(), work.object(), work.request().subject()));
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
    } else if (expression instanceof CaveatExpression) {
      caveatWorks.add(work);
    }
  }

  private boolean prefetchCaveatAttributes(List<BulkWork> works, EvaluationState state) {
    List<AttributeLookupRequest> requests = new ArrayList<>();
    for (BulkWork work : works) {
      CaveatExpression caveatExpression = (CaveatExpression) work.expression();
      Optional<AttributeLookupRequest> request =
          attributeRequest(caveatExpression.caveat(), work.object(), state);
      if (state.deniedReason != DecisionReason.NO_MATCH) {
        return false;
      }
      request.ifPresent(requests::add);
    }
    return prefetchAttributes(requests, state);
  }

  private boolean prefetchAttributes(
      List<AttributeLookupRequest> requests, EvaluationState state) {
    List<AttributeLookupRequest> missing =
        requests.stream()
            .distinct()
            .filter(request -> !state.attributeCache.containsKey(request))
            .filter(request -> !state.attributeFailures.containsKey(request))
            .toList();
    if (missing.isEmpty()) {
      return true;
    }
    if (!state.allowAttributeCalls(missing)) {
      return false;
    }
    BatchResolution<AttributeLookupRequest, Map<AttributeRef, String>> batch;
    try {
      batch = attributes.resolve(missing, state.readContext());
    } catch (RelationshipLookupException exception) {
      recordAttributeFailure(missing, state, exception.reason());
      return false;
    } catch (RuntimeException exception) {
      recordAttributeFailure(missing, state, DecisionReason.RESOLVER_FAILURE);
      return false;
    }
    if (batch == null || !completeAttributeBatch(missing, batch.values())) {
      recordAttributeFailure(missing, state, DecisionReason.RESOLVER_FAILURE);
      return false;
    }
    if (!state.acceptConsistency(batch.consistency())) {
      recordAttributeFailure(missing, state, state.deniedReason);
      return false;
    }
    for (AttributeLookupRequest request : missing) {
      Map<AttributeRef, String> values = Map.copyOf(batch.values().get(request));
      if (!request.attributes().containsAll(values.keySet())) {
        recordAttributeFailure(missing, state, DecisionReason.RESOLVER_FAILURE);
        return false;
      }
      if (!state.allowIntermediateResults(values.size())) {
        recordAttributeFailure(missing, state, state.deniedReason);
        return false;
      }
      state.attributeCache.put(request, values);
    }
    return true;
  }

  private static boolean completeAttributeBatch(
      List<AttributeLookupRequest> requests,
      Map<AttributeLookupRequest, Map<AttributeRef, String>> resolved) {
    return resolved != null
        && resolved.size() == requests.size()
        && resolved.keySet().equals(Set.copyOf(requests))
        && resolved.values().stream().allMatch(Objects::nonNull);
  }

  private static void recordAttributeFailure(
      List<AttributeLookupRequest> requests, EvaluationState state, DecisionReason reason) {
    requests.forEach(request -> state.attributeFailures.put(request, reason));
    state.deniedReason = reason;
  }

  private boolean prefetchGrants(Set<CheckRequest> requests, EvaluationState state) {
    List<CheckRequest> missing =
        requests.stream()
            .filter(request -> !state.grantCache.containsKey(request))
            .filter(request -> !state.grantFailures.containsKey(request))
            .toList();
    if (missing.isEmpty()) {
      return true;
    }
    BatchResolution<CheckRequest, Boolean> batch;
    try {
      batch = grants.resolve(missing, state.readContext());
    } catch (RelationshipLookupException exception) {
      recordGrantFailure(missing, state, exception.reason());
      return false;
    } catch (RuntimeException exception) {
      recordGrantFailure(missing, state, DecisionReason.RESOLVER_FAILURE);
      return false;
    }
    if (batch == null || !completeGrantBatch(missing, batch.values())) {
      recordGrantFailure(missing, state, DecisionReason.RESOLVER_FAILURE);
      return false;
    }
    if (!state.acceptConsistency(batch.consistency())) {
      recordGrantFailure(missing, state, state.deniedReason);
      return false;
    }
    batch.values().forEach(state.grantCache::put);
    return true;
  }

  private static boolean completeGrantBatch(
      List<CheckRequest> requests, Map<CheckRequest, Boolean> resolved) {
    return resolved != null
        && resolved.size() == requests.size()
        && resolved.keySet().equals(Set.copyOf(requests))
        && resolved.values().stream().allMatch(Objects::nonNull);
  }

  private static void recordGrantFailure(
      List<CheckRequest> requests, EvaluationState state, DecisionReason reason) {
    requests.forEach(request -> state.grantFailures.put(request, reason));
    state.deniedReason = reason;
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
    BatchResolution<RelationLookupRequest, List<RelationshipEntry>> batch;
    try {
      batch = relationships.resolve(missing, state.readContext());
    } catch (RelationshipLookupException exception) {
      state.deniedReason = exception.reason();
      return false;
    } catch (RuntimeException exception) {
      state.deniedReason = DecisionReason.RESOLVER_FAILURE;
      return false;
    }
    if (batch == null || !completeRelationshipBatch(missing, batch.values())) {
      state.deniedReason = DecisionReason.RESOLVER_FAILURE;
      return false;
    }
    if (!state.acceptConsistency(batch.consistency())) {
      return false;
    }
    for (RelationLookupRequest request : missing) {
      List<RelationshipEntry> entries = List.copyOf(batch.values().get(request));
      if (!state.allowIntermediateResults(entries.size())) {
        return false;
      }
      state.cache.put(request, entries);
    }
    return true;
  }

  private static boolean completeRelationshipBatch(
      List<RelationLookupRequest> requests,
      Map<RelationLookupRequest, List<RelationshipEntry>> resolved) {
    return resolved != null
        && resolved.size() == requests.size()
        && resolved.keySet().equals(Set.copyOf(requests))
        && resolved.values().stream().allMatch(Objects::nonNull);
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
    if (expression instanceof GrantExpression) {
      return resolveGrant(grantRequest(request, object, subject), state);
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
      return evaluateCaveat(
              caveatExpression.caveat(), request, object, subject, state)
          && evaluate(caveatExpression.expression(), request, object, subject, state, depth + 1);
    }
    return false;
  }

  private boolean evaluateCaveat(
      com.luokuiai.forga.core.model.CaveatRef caveat,
      CheckRequest request,
      ObjectRef object,
      SubjectRef subject,
      EvaluationState state) {
    Optional<AttributeLookupRequest> attributeRequest = attributeRequest(caveat, object, state);
    if (state.deniedReason != DecisionReason.NO_MATCH) {
      return false;
    }
    Map<AttributeRef, String> objectAttributes = Map.of();
    if (attributeRequest.isPresent()) {
      AttributeLookupRequest lookupRequest = attributeRequest.orElseThrow();
      if (!state.allowAttributeCall(lookupRequest)) {
        return false;
      }
      if (!prefetchAttributes(List.of(lookupRequest), state)) {
        return false;
      }
      DecisionReason failure = state.attributeFailures.get(lookupRequest);
      if (failure != null) {
        state.deniedReason = failure;
        return false;
      }
      objectAttributes = state.attributeCache.getOrDefault(lookupRequest, Map.of());
    }
    try {
      return caveats.evaluate(
          caveat, new CaveatEvaluationContext(request, object, subject, objectAttributes));
    } catch (RuntimeException exception) {
      state.deniedReason = DecisionReason.RESOLVER_FAILURE;
      return false;
    }
  }

  private Optional<AttributeLookupRequest> attributeRequest(
      com.luokuiai.forga.core.model.CaveatRef caveat,
      ObjectRef object,
      EvaluationState state) {
    try {
      if (!Set.copyOf(caveats.caveats()).contains(caveat)) {
        state.deniedReason = DecisionReason.RESOLVER_FAILURE;
        return Optional.empty();
      }
      Set<AttributeRef> required = Set.copyOf(caveats.requiredAttributes(caveat));
      return required.isEmpty()
          ? Optional.empty()
          : Optional.of(new AttributeLookupRequest(object, required));
    } catch (RuntimeException exception) {
      state.deniedReason = DecisionReason.RESOLVER_FAILURE;
      return Optional.empty();
    }
  }

  private boolean resolveGrant(CheckRequest request, EvaluationState state) {
    if (!state.allowGrantCall(request)) {
      return false;
    }
    DecisionReason cachedFailure = state.grantFailures.get(request);
    if (cachedFailure != null) {
      state.deniedReason = cachedFailure;
      return false;
    }
    Boolean granted = state.grantCache.get(request);
    if (granted == null) {
      BatchResolution<CheckRequest, Boolean> batch;
      try {
        batch = grants.resolve(List.of(request), state.readContext());
      } catch (RelationshipLookupException exception) {
        state.deniedReason = exception.reason();
        return false;
      } catch (RuntimeException exception) {
        state.deniedReason = DecisionReason.RESOLVER_FAILURE;
        return false;
      }
      if (batch == null || !completeGrantBatch(List.of(request), batch.values())) {
        state.deniedReason = DecisionReason.RESOLVER_FAILURE;
        return false;
      }
      if (!state.acceptConsistency(batch.consistency())) {
        return false;
      }
      granted = batch.values().get(request);
      state.grantCache.put(request, granted);
    }
    return granted;
  }

  private static CheckRequest grantRequest(
      CheckRequest request, ObjectRef object, SubjectRef subject) {
    return new CheckRequest(object, request.permission(), subject, request.attributes());
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
    BatchResolution<RelationLookupRequest, List<RelationshipEntry>> batch;
    try {
      batch = relationships.resolve(List.of(request), state.readContext());
    } catch (RelationshipLookupException exception) {
      state.forgetResolverCall(request);
      state.deniedReason = exception.reason();
      return List.of();
    } catch (RuntimeException exception) {
      state.forgetResolverCall(request);
      state.deniedReason = DecisionReason.RESOLVER_FAILURE;
      return List.of();
    }
    if (batch == null || !completeRelationshipBatch(List.of(request), batch.values())) {
      state.forgetResolverCall(request);
      state.deniedReason = DecisionReason.RESOLVER_FAILURE;
      return List.of();
    }
    if (!state.acceptConsistency(batch.consistency())) {
      state.forgetResolverCall(request);
      return List.of();
    }
    List<RelationshipEntry> entries = List.copyOf(batch.values().get(request));
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
      Set<ObjectRef> candidates =
          collectObjects(
              caveatExpression.expression(),
              request,
              objectType,
              resolverLimit,
              state,
              depth + 1);
      List<AttributeLookupRequest> attributeRequests = new ArrayList<>();
      for (ObjectRef candidate : candidates) {
        attributeRequest(caveatExpression.caveat(), candidate, state)
            .ifPresent(attributeRequests::add);
      }
      if (state.deniedReason != DecisionReason.NO_MATCH
          || !prefetchAttributes(attributeRequests, state)) {
        return Set.of();
      }
      Set<ObjectRef> accepted = new LinkedHashSet<>();
      for (ObjectRef candidate : candidates) {
        CheckRequest caveatRequest =
            new CheckRequest(
                candidate, request.permission(), request.subject(), request.attributes());
        if (evaluateCaveat(
            caveatExpression.caveat(),
            caveatRequest,
            candidate,
            request.subject(),
            state)) {
          accepted.add(candidate);
        }
      }
      return accepted;
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

    private final Map<CheckRequest, Boolean> grantCache;

    private final Map<CheckRequest, DecisionReason> grantFailures;

    private final Map<AttributeLookupRequest, Map<AttributeRef, String>> attributeCache;

    private final Map<AttributeLookupRequest, DecisionReason> attributeFailures;

    private final Set<RelationLookupRequest> accountedLookups = new HashSet<>();

    private final Set<CheckRequest> accountedGrants = new HashSet<>();

    private final Set<AttributeLookupRequest> accountedAttributes = new HashSet<>();

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
      this(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    EvaluationState(Map<RelationLookupRequest, List<RelationshipEntry>> cache) {
      this(cache, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    EvaluationState(
        Map<RelationLookupRequest, List<RelationshipEntry>> cache,
        Map<CheckRequest, Boolean> grantCache,
        Map<CheckRequest, DecisionReason> grantFailures,
        Map<AttributeLookupRequest, Map<AttributeRef, String>> attributeCache,
        Map<AttributeLookupRequest, DecisionReason> attributeFailures) {
      this(
          cache,
          grantCache,
          grantFailures,
          attributeCache,
          attributeFailures,
          Optional.empty());
    }

    EvaluationState(
        Map<RelationLookupRequest, List<RelationshipEntry>> cache,
        Map<CheckRequest, Boolean> grantCache,
        Map<CheckRequest, DecisionReason> grantFailures,
        Map<AttributeLookupRequest, Map<AttributeRef, String>> attributeCache,
        Map<AttributeLookupRequest, DecisionReason> attributeFailures,
        Optional<ConsistencyToken> consistency) {
      this.cache = Objects.requireNonNull(cache, "cache is required");
      this.grantCache = Objects.requireNonNull(grantCache, "grant cache is required");
      this.grantFailures =
          Objects.requireNonNull(grantFailures, "grant failures are required");
      this.attributeCache =
          Objects.requireNonNull(attributeCache, "attribute cache is required");
      this.attributeFailures =
          Objects.requireNonNull(attributeFailures, "attribute failures are required");
      this.consistency = consistency == null ? Optional.empty() : consistency;
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

    boolean allowGrantCall(CheckRequest request) {
      if (accountedGrants.contains(request)) {
        return true;
      }
      if (!allowResolverCall()) {
        return false;
      }
      accountedGrants.add(request);
      return true;
    }

    boolean allowAttributeCalls(List<AttributeLookupRequest> requests) {
      for (AttributeLookupRequest request : requests) {
        if (!allowAttributeCall(request)) {
          return false;
        }
      }
      return true;
    }

    boolean allowAttributeCall(AttributeLookupRequest request) {
      return !accountedAttributes.add(request) || allowResolverCall();
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

    EvaluationReadContext readContext() {
      return new EvaluationReadContext(consistency, deadline);
    }
  }
}
