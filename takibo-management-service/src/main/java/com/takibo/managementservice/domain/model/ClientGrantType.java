package com.takibo.managementservice.domain.model;// package com.takibo.managementservice.domain.model.client;

import com.takibo.managementservice.domain.exception.InvalidGrantTypeException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.util.Assert;

import java.util.*;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public final class ClientGrantType {

  private static final Set<String> ALLOWED = Set.of(
      "authorization_code", "refresh_token", "client_credentials",
      "password", "urn:ietf:params:oauth:grant-type:device_code"
  );

  @EqualsAndHashCode.Include
  private final String value;

  private ClientGrantType(String normalized) { this.value = normalized; }

  public static Set<ClientGrantType> ofAll(Set<String> rawValues) {
    if (rawValues == null || rawValues.isEmpty()) return Set.of();

    Set<String> invalidValues = new HashSet<>();
    Set<ClientGrantType> validatedValues = new HashSet<>();

    for (String raw : rawValues) {
      String candidate = raw == null ? "" : raw.trim().toLowerCase();
      Assert.hasText(candidate, "grant_type is blank");
      boolean supported = candidate.startsWith("urn:") || ALLOWED.contains(candidate);
      if (!supported) invalidValues.add(raw);
      else validatedValues.add(new ClientGrantType(candidate));
    }

    if (!invalidValues.isEmpty()) throw new InvalidGrantTypeException(invalidValues);
    return Collections.unmodifiableSet(validatedValues);
  }

  @Override public String toString() { return value; }
}
