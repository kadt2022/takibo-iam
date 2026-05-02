package com.takibo.managementservice.interfaces.rest.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import com.takibo.audit.infrastructure.service.MaskingLogger;

public record AccountInput(
  @Email String email,
  @Size(min=8, max=200) String password
) {

    @Override
    public String toString() {
        // Délégué au moteur de masquage Takibo.
        return MaskingLogger.safeToString(this);
    }
}