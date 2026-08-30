package com.luokuiai.forga.scope;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Resolves explicit cross-scope grants for a bounded request batch. */
@FunctionalInterface
public interface CrossScopeGrantLookup {

  /**
   * Resolves one complete batch of exact cross-scope grant requests.
   *
   * <p>The returned map must contain exactly one non-null Boolean for every distinct submitted
   * request. Implementations should use a set-oriented repository query and participate in the host
   * request's transaction or equivalent snapshot boundary.
   *
   * @param requests distinct cross-scope grant requests
   * @return complete grant results
   */
  Map<CrossScopeAccessRequest, Boolean> resolve(List<CrossScopeAccessRequest> requests);

  /**
   * Returns a lookup that denies every cross-scope request.
   *
   * @return deny-all lookup
   */
  static CrossScopeGrantLookup denyAll() {
    return requests ->
        requests.stream()
            .distinct()
            .collect(Collectors.toUnmodifiableMap(request -> request, request -> false));
  }
}
