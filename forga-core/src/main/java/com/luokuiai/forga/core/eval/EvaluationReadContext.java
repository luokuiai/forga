package com.luokuiai.forga.core.eval;

import com.luokuiai.forga.core.model.ConsistencyToken;
import java.time.Instant;
import java.util.Optional;

/** Opaque consistency state and absolute deadline for one evaluator host read. */
public record EvaluationReadContext(
    Optional<ConsistencyToken> consistency, Optional<Instant> deadline) {

  /** Creates an evaluation read context. */
  public EvaluationReadContext {
    consistency = consistency == null ? Optional.empty() : consistency;
    deadline = deadline == null ? Optional.empty() : deadline;
  }
}
