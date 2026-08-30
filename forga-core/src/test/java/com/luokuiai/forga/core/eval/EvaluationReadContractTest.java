package com.luokuiai.forga.core.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.luokuiai.forga.core.model.ConsistencyToken;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EvaluationReadContractTest {

  @Test
  void batchResolutionCopiesValuesAndPreservesConsistency() {
    Map<String, Boolean> values = new LinkedHashMap<>();
    values.put("one", true);
    ConsistencyToken token = new ConsistencyToken("v1");

    BatchResolution<String, Boolean> resolution =
        new BatchResolution<>(values, Optional.of(token));
    values.put("two", false);

    assertThat(resolution.values()).containsOnly(Map.entry("one", true));
    assertThat(resolution.consistency()).contains(token);
  }

  @Test
  void readContractsNormalizeAbsentOptionalWrappers() {
    EvaluationReadContext context = new EvaluationReadContext(null, null);
    BatchResolution<String, Boolean> resolution = new BatchResolution<>(Map.of(), null);

    assertThat(context.consistency()).isEmpty();
    assertThat(context.deadline()).isEmpty();
    assertThat(resolution.consistency()).isEmpty();
  }

  @Test
  void readContextPreservesEstablishedValues() {
    ConsistencyToken token = new ConsistencyToken("v1");
    Instant deadline = Instant.now().plusSeconds(5);

    EvaluationReadContext context =
        new EvaluationReadContext(Optional.of(token), Optional.of(deadline));

    assertThat(context.consistency()).contains(token);
    assertThat(context.deadline()).contains(deadline);
  }
}
