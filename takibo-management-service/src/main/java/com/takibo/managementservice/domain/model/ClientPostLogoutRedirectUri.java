package com.takibo.managementservice.domain.model;// package com.takibo.managementservice.domain.model.client;

import com.takibo.managementservice.domain.exception.InvalidPostLogoutRedirectUriException;
import com.takibo.managementservice.domain.validation.UriValidation;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.net.URI;
import java.util.*;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class ClientPostLogoutRedirectUri {

  @EqualsAndHashCode.Include
  private final String uri;

  private ClientPostLogoutRedirectUri(String normalized) { this.uri = normalized; }

  public static Set<ClientPostLogoutRedirectUri> ofAll(Set<String> rawValues) {
    if (rawValues == null || rawValues.isEmpty()) return Set.of();

    Set<String> invalidValues = new HashSet<>();
    Set<ClientPostLogoutRedirectUri> validatedValues = new HashSet<>();

    for (String raw : rawValues) {
      String candidate = raw == null ? "" : raw.trim();
      try {
        URI parsed = UriValidation.requireOAuthRedirectUrl(candidate);
        validatedValues.add(new ClientPostLogoutRedirectUri(parsed.toString()));
      } catch (Exception e) {
        invalidValues.add(raw);
      }
    }

    if (!invalidValues.isEmpty()) throw new InvalidPostLogoutRedirectUriException(invalidValues);
    return Collections.unmodifiableSet(validatedValues);
  }

  @Override public String toString() { return uri; }

  public static Set<String> toStrings(Set<ClientPostLogoutRedirectUri> values) {
    return values.stream().map(ClientPostLogoutRedirectUri::getUri).collect(java.util.stream.Collectors.toUnmodifiableSet());
  }
}
