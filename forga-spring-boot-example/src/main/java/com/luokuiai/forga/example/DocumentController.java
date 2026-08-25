package com.luokuiai.forga.example;

import com.luokuiai.forga.core.context.AuthenticatedSubjectProvider;
import com.luokuiai.forga.core.context.AuthorizationAttributesProvider;
import com.luokuiai.forga.core.eval.AuthorizationEvaluator;
import com.luokuiai.forga.core.eval.CheckDecision;
import com.luokuiai.forga.core.eval.CheckRequest;
import com.luokuiai.forga.core.model.ObjectRef;
import com.luokuiai.forga.core.model.SubjectRef;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/documents")
final class DocumentController {

  private final AuthorizationEvaluator evaluator;

  private final AuthenticatedSubjectProvider subjects;

  private final AuthorizationAttributesProvider attributes;

  DocumentController(
      AuthorizationEvaluator evaluator,
      AuthenticatedSubjectProvider subjects,
      AuthorizationAttributesProvider attributes) {
    this.evaluator = evaluator;
    this.subjects = subjects;
    this.attributes = attributes;
  }

  @GetMapping("/{documentId}")
  ResponseEntity<Map<String, String>> document(@PathVariable String documentId) {
    Optional<SubjectRef> subject = subjects.currentSubject();
    if (subject.isEmpty()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    ObjectRef document = new ObjectRef("document", documentId);
    CheckDecision decision =
        evaluator.check(
            new CheckRequest(
                document,
                ExampleAuthorizationConfiguration.VIEW_DOCUMENT,
                subject.orElseThrow(),
                attributes.attributes()));
    if (!decision.allowed()) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    return ResponseEntity.ok(Map.of("id", documentId, "title", "Forga example document"));
  }
}
