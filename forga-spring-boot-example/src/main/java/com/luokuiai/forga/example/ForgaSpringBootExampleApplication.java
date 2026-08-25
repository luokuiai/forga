package com.luokuiai.forga.example;

import com.luokuiai.forga.spring.EnableForga;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Runnable Spring Boot reference integration for Forga. */
@EnableForga
@SpringBootApplication
public class ForgaSpringBootExampleApplication {

  /**
   * Starts the example application.
   *
   * @param args application arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(ForgaSpringBootExampleApplication.class, args);
  }
}
