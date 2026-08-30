package com.luokuiai.forga.spring;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class ForgaStartupBanner {

  private static final String VERSION_RESOURCE = "/META-INF/forga.version";

  private ForgaStartupBanner() { }

  static String render() {
    String newline = System.lineSeparator();
    return String.join(
        newline,
        "",
        "    ______",
        "   |  ____|",
        "   | |__ ___  _ __ __ _  __ _",
        "   |  __/ _ \\| '__/ _` |/ _` |",
        "   | | | (_) | | | (_| | (_| |",
        "   |_|  \\___/|_|  \\__, |\\__,_|",
        "                   |___/",
        "",
        " :: Forga :: Fine-grained Object-Relation Graph Authorization (v" + version() + ")");
  }

  static String version() {
    try (InputStream input = ForgaStartupBanner.class.getResourceAsStream(VERSION_RESOURCE)) {
      if (input != null) {
        String version = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        if (!version.isBlank() && !version.startsWith("${")) {
          return version;
        }
      }
    } catch (IOException ignored) {
      // Fall back to package metadata when the generated resource cannot be read.
    }
    String implementationVersion = ForgaStartupBanner.class.getPackage().getImplementationVersion();
    return implementationVersion == null ? "unknown" : implementationVersion;
  }
}
