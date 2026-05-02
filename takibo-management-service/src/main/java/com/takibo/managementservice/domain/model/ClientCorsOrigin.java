package com.takibo.managementservice.domain.model;// package com.takibo.managementservice.domain.model.client;

import com.takibo.managementservice.domain.exception.InvalidCorsOriginException;
import com.takibo.managementservice.domain.validation.UriValidation;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class ClientCorsOrigin {

  @EqualsAndHashCode.Include
  private final String origin;

  private ClientCorsOrigin(String normalized) { this.origin = normalized; }

  public static Set<ClientCorsOrigin> ofAll(Set<String> rawValues) {
    if (rawValues == null || rawValues.isEmpty()) return Set.of();

    Set<String> invalidValues = new HashSet<>();
    Set<ClientCorsOrigin> validatedValues = new HashSet<>();

    for (String raw : rawValues) {
      String candidate = raw == null ? "" : raw.trim();
      try {
        URI normalized = UriValidation.requireHttpHttpsOrigin(candidate);
        validatedValues.add(new ClientCorsOrigin(normalized.toString()));
      } catch (Exception e) {
        invalidValues.add(raw);
      }
    }

    if (!invalidValues.isEmpty()) throw new InvalidCorsOriginException(invalidValues);
    return Collections.unmodifiableSet(validatedValues);
  }

  @Override public String toString() { return origin; }

  public static Set<String> toStrings(Set<ClientCorsOrigin> values) {
    return values.stream().map(ClientCorsOrigin::getOrigin).collect(Collectors.toUnmodifiableSet());
  }
}
