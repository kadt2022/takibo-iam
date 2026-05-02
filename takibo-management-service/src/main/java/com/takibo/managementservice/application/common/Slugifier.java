package com.takibo.managementservice.application.common;

import java.text.Normalizer;
import java.util.Locale;

public final class Slugifier {
  private Slugifier() {}
  public static String slug(String input) {
    if (input == null) return "";
    var n = Normalizer.normalize(input.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    n = n.replaceAll("[^a-z0-9]+", "-");
    return n.replaceAll("(^-|-$)", "");
  }
}
