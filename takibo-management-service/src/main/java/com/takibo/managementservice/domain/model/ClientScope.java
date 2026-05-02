package com.takibo.managementservice.domain.model;// package com.takibo.managementservice.domain.model.client;

import com.takibo.managementservice.domain.exception.InvalidScopeException;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.*;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class ClientScope {

  @EqualsAndHashCode.Include
  private final String value;

  private ClientScope(String normalized) { this.value = normalized; }

  public static Set<ClientScope> ofAll(Set<String> rawValues) {
    if (rawValues == null || rawValues.isEmpty()) return Set.of();

    Set<String> invalidValues = new HashSet<>();
    Set<ClientScope> validatedValues = new HashSet<>();

    for (String raw : rawValues) {
      String candidate = raw == null ? "" : raw.trim();
      if (candidate.isEmpty()) invalidValues.add(raw);
      else validatedValues.add(new ClientScope(candidate));
    }

    if (!invalidValues.isEmpty()) throw new InvalidScopeException(invalidValues);
    return Collections.unmodifiableSet(validatedValues);
  }

  @Override public String toString() { return value; }
}
