package com.takibo.managementservice.domain.validation;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class UriValidation {

  private static final String HTTPS_SCHEME = "https";

  private UriValidation() {}

  /** URL http(s) complète : schema+host (+port) + chemin/query/fragment autorisés */
  public static URI requireHttpHttpsUrl(String rawValue) {
    URI parsed = URI.create(requireText(rawValue, "URI value is blank").trim());
    String scheme = parsed.getScheme();
    requireState(scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase(HTTPS_SCHEME)),
        "Only http/https scheme is allowed");
    requireState(hasText(parsed.getHost()), "Host is required");
    return parsed;
  }

  public static URI requireOAuthRedirectUrl(String rawValue) {
    URI parsed = requireHttpHttpsUrl(rawValue);
    requireState(parsed.getUserInfo() == null, "Redirect URI must not include user-info");
    requireState(parsed.getFragment() == null, "Redirect URI must not include a fragment");
    requireSecureTransport(parsed);
    return parsed;
  }

  public static URI requireHttpsEndpoint(String rawValue) {
    URI parsed = requireHttpHttpsUrl(rawValue);
    requireState(HTTPS_SCHEME.equalsIgnoreCase(parsed.getScheme()), "HTTPS is required");
    requireState(parsed.getUserInfo() == null, "Endpoint must not include user-info");
    requireState(parsed.getFragment() == null, "Endpoint must not include a fragment");
    return parsed;
  }

  /** Origin http(s) stricte : schema+host (+port), PAS de chemin (sauf éventuel '/'), NI query, NI fragment */
  public static URI requireHttpHttpsOrigin(String rawValue) {
    URI parsed = requireHttpHttpsUrl(rawValue);

    requireState(parsed.getUserInfo() == null, "Origin must not include user-info");
    requireSecureTransport(parsed);

    String path = parsed.getPath();
    requireState(path == null || path.isEmpty() || "/".equals(path), "Origin must not include a path");

    requireState(parsed.getQuery() == null, "Origin must not include a query");
    requireState(parsed.getFragment() == null, "Origin must not include a fragment");

    return normalizeOrigin(parsed);
  }

  private static void requireSecureTransport(URI parsed) {
    if (HTTPS_SCHEME.equalsIgnoreCase(parsed.getScheme())) {
      return;
    }
    requireState(isLoopbackHost(parsed.getHost()), "HTTP is only allowed for loopback hosts");
  }

  private static boolean isLoopbackHost(String host) {
    String normalized = host.toLowerCase(Locale.ROOT);
    if ("localhost".equals(normalized)
            || "::1".equals(normalized)
            || "[::1]".equals(normalized)) {
      return true;
    }

    String[] octets = normalized.split("\\.", -1);
    if (octets.length != 4 || !"127".equals(octets[0])) {
      return false;
    }
    for (int index = 1; index < octets.length; index++) {
      if (!isIpv4Octet(octets[index])) {
        return false;
      }
    }
    return true;
  }

  private static boolean isIpv4Octet(String value) {
    if (value.isEmpty() || value.length() > 3 || !value.chars().allMatch(Character::isDigit)) {
      return false;
    }
    return Integer.parseInt(value) <= 255;
  }

  /** Normalise l’origin (minuscule sur scheme/host, path/query/fragment vides) */
  private static URI normalizeOrigin(URI u) {
    try {
      int port = u.getPort(); // -1 si absent
      String scheme = u.getScheme().toLowerCase(Locale.ROOT);
      String host   = u.getHost().toLowerCase(Locale.ROOT);
      return new URI(scheme, null, host, port, null, null, null);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Unable to normalize origin", e);
    }
  }

  private static boolean hasText(String s) { return s != null && !s.isBlank(); }

  private static String requireText(String value, String message) {
    if (!hasText(value)) {
      throw new IllegalArgumentException(message);
    }
    return value;
  }

  private static void requireState(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }
}
