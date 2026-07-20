package com.takibo.managementservice.domain.model;// package com.takibo.managementservice.domain.model.client;

import com.takibo.managementservice.domain.exception.InvalidRedirectUriException;
import com.takibo.managementservice.domain.validation.UriValidation;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class ClientRedirectUri {

  @EqualsAndHashCode.Include
  private final String uri;

  private ClientRedirectUri(String normalized) { this.uri = normalized; }

  public static Set<ClientRedirectUri> ofAll(Set<String> rawValues) {
    if (rawValues == null || rawValues.isEmpty()) return Set.of();

    Set<String> invalidValues = new HashSet<>();
    Set<ClientRedirectUri> validatedValues = new HashSet<>();

    for (String raw : rawValues) {
      String candidate = raw == null ? "" : raw.trim();
      try {
        URI parsed = UriValidation.requireOAuthRedirectUrl(candidate);
        validatedValues.add(new ClientRedirectUri(parsed.toString()));
      } catch (Exception e) {
        invalidValues.add(raw);
      }
    }

    if (!invalidValues.isEmpty()) throw new InvalidRedirectUriException(invalidValues);
    return Collections.unmodifiableSet(validatedValues);
  }

  @Override public String toString() { return uri; }

  public static Set<String> toStrings(Set<ClientRedirectUri> values) {
    return values.stream().map(ClientRedirectUri::getUri).collect(Collectors.toUnmodifiableSet());
  }
}
