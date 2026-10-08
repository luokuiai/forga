package com.luokuiai.forga.example;

import com.luokuiai.forga.spring.web.EndpointAuthorizationException;
import com.luokuiai.forga.spring.web.RequiresPermission;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/documents")
final class DocumentController {

  @RequiresPermission("view_document")
  @GetMapping("/{documentId}")
  ResponseEntity<Map<String, String>> document(@PathVariable String documentId) {
    return ResponseEntity.ok(Map.of("id", documentId, "title", "Forga example document"));
  }

  @ExceptionHandler(EndpointAuthorizationException.class)
  ResponseEntity<Void> denied(EndpointAuthorizationException exception) {
    HttpStatus status =
        "AUTHENTICATION_REQUIRED".equals(exception.reason())
            ? HttpStatus.UNAUTHORIZED
            : HttpStatus.FORBIDDEN;
    return ResponseEntity.status(status).build();
  }
}
