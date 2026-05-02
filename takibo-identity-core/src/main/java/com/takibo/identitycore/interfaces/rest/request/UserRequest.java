package com.takibo.identitycore.interfaces.rest.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;


/**
 * @param rawPassword Validation de base. La politique est plus détaillée.
 * @param firstName   Ces champs peuvent être null/vides si non obligatoires
 */
public record UserRequest(
        @NotBlank(message = "Username cannot be empty") @Size(min = 5, max = 50, message = "Username must be between 3 and 50 characters")
        String username,
        @NotBlank(message = "Email cannot be empty") @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Password cannot be empty") @Size(min = 8, message = "Password must be at least 8 characters long")
        String rawPassword,
        String firstName,
        String lastName,
        Map<String, Object> metadata){
}