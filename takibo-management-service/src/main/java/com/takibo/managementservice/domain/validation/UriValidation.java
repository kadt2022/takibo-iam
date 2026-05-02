package com.takibo.managementservice.domain.validation;

import org.springframework.util.Assert;

import java.net.URI;
import java.net.URISyntaxException;

public final class UriValidation {

  private UriValidation() {}

  /** URL http(s) complète : schema+host (+port) + chemin/query/fragment autorisés */
  public static URI requireHttpHttpsUrl(String rawValue) {
    Assert.hasText(rawValue, "URI value is blank");
    URI parsed = URI.create(rawValue.trim());
    String scheme = parsed.getScheme();
    Assert.state(scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")),
        "Only http/https scheme is allowed");
    Assert.state(hasText(parsed.getHost()), "Host is required");
    return parsed;
  }

  /** Origin http(s) stricte : schema+host (+port), PAS de chemin (sauf éventuel '/'), NI query, NI fragment */
  public static URI requireHttpHttpsOrigin(String rawValue) {
    URI parsed = requireHttpHttpsUrl(rawValue);

    String path = parsed.getPath();
    Assert.state(path == null || path.isEmpty() || "/".equals(path), "Origin must not include a path");

    Assert.state(parsed.getQuery() == null, "Origin must not include a query");
    Assert.state(parsed.getFragment() == null, "Origin must not include a fragment");

    return normalizeOrigin(parsed);
  }

  /** Normalise l’origin (minuscule sur scheme/host, path/query/fragment vides) */
  private static URI normalizeOrigin(URI u) {
    try {
      int port = u.getPort(); // -1 si absent
      String scheme = u.getScheme() == null ? null : u.getScheme().toLowerCase();
      String host   = u.getHost()   == null ? null : u.getHost().toLowerCase();
      return new URI(scheme, null, host, port, null, null, null);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Unable to normalize origin", e);
    }
  }

  private static boolean hasText(String s) { return s != null && !s.isBlank(); }
}
