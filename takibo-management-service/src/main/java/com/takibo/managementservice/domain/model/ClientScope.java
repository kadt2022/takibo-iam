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
      if (candidate.isEmpty() || !isScopeToken(candidate)) invalidValues.add(raw);
      else validatedValues.add(new ClientScope(candidate));
    }

    if (!invalidValues.isEmpty()) throw new InvalidScopeException(invalidValues);
    return Collections.unmodifiableSet(validatedValues);
  }

  /**
   * RFC 6749 §3.3 : {@code scope-token = 1*( %x21 / %x23-5B / %x5D-7E )}. C'est aussi la règle
   * de Spring Authorization Server : un scope hors syntaxe rendrait le client inutilisable.
   */
  private static boolean isScopeToken(String candidate) {
    return candidate.chars().allMatch(c -> c == 0x21 || (c >= 0x23 && c <= 0x5B) || (c >= 0x5D && c <= 0x7E));
  }

  @Override public String toString() { return value; }
}
