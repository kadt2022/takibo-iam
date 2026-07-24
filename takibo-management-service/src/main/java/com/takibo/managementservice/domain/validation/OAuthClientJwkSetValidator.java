package com.takibo.managementservice.domain.validation;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyOperation;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.takibo.managementservice.domain.exception.InvalidClientConfigurationException;
import com.takibo.managementservice.domain.model.OAuthClientRegistration;
import com.takibo.managementservice.domain.model.TokenEndpointAuthMethod;

import java.text.ParseException;
import java.util.List;
import java.util.Set;

public final class OAuthClientJwkSetValidator {

    private static final Set<Curve> SUPPORTED_EC_CURVES =
            Set.of(Curve.P_256, Curve.P_384, Curve.P_521);

    public void validate(OAuthClientRegistration registration) {
        boolean hasUri = hasText(registration.jwksUri());
        boolean hasJson = hasText(registration.jwksJson());

        if (hasUri && hasJson) {
            fail("jwksUri and jwksJson are mutually exclusive");
        }
        if (registration.tokenEndpointAuthMethod()
                == TokenEndpointAuthMethod.private_key_jwt
                && !hasUri && !hasJson) {
            fail("private_key_jwt requires jwksUri or jwksJson");
        }
        if (registration.tokenEndpointAuthMethod()
                == TokenEndpointAuthMethod.private_key_jwt
                && !hasText(registration.idTokenSignedAlg())) {
            fail("private_key_jwt requires idTokenSignedAlg");
        }
        if (registration.tokenEndpointAuthMethod()
                == TokenEndpointAuthMethod.private_key_jwt
                && Boolean.TRUE.equals(
                        registration.requireClientSecret()
                )) {
            fail("private_key_jwt must not require a client secret");
        }
        if (hasUri) {
            validateJwksUri(registration.jwksUri());
        }
        if (hasJson) {
            validateJwksJson(
                    registration.jwksJson(),
                    registration.idTokenSignedAlg()
            );
        }
    }

    private static void validateJwksUri(String rawUri) {
        if (rawUri.length() > 255) {
            fail("jwksUri exceeds 255 characters");
        }
        try {
            UriValidation.requireHttpsEndpoint(rawUri);
        } catch (RuntimeException exception) {
            fail(
                    "jwksUri must be an absolute HTTPS URL "
                            + "without user-info or fragment"
            );
        }
    }

    private static void validateJwksJson(
            String rawJson,
            String configuredAlgorithm
    ) {
        if (rawJson.length() > 32768) {
            fail("jwksJson exceeds 32768 characters");
        }
        try {
            List<JWK> keys = JWKSet.parse(rawJson).getKeys();
            if (keys.isEmpty() || keys.size() > 10) {
                fail(
                        "jwksJson must contain between 1 and 10 public keys"
                );
            }

            boolean compatibleKeyFound = false;
            for (JWK key : keys) {
                validatePublicJwk(key);
                compatibleKeyFound |= isCompatibleWithAlgorithm(
                        key,
                        configuredAlgorithm
                );
            }
            if (hasText(configuredAlgorithm) && !compatibleKeyFound) {
                fail(
                        "jwksJson does not contain a key compatible "
                                + "with idTokenSignedAlg"
                );
            }
        } catch (ParseException
                 | JOSEException
                 | IllegalArgumentException exception) {
            fail("jwksJson must be a valid JWK Set");
        }
    }

    private static void validatePublicJwk(JWK key) throws JOSEException {
        if (!(key instanceof RSAKey) && !(key instanceof ECKey)) {
            fail("jwksJson contains an unsupported key type");
        }
        if (key.isPrivate()) {
            fail("jwksJson must contain public key material only");
        }
        if (key.getKeyUse() != null
                && !KeyUse.SIGNATURE.equals(key.getKeyUse())) {
            fail("jwksJson keys must be usable for signatures");
        }
        if (key.getKeyOperations() != null
                && !key.getKeyOperations().isEmpty()
                && !key.getKeyOperations().contains(KeyOperation.VERIFY)) {
            fail("jwksJson keys must allow signature verification");
        }
        if (key instanceof RSAKey rsaKey) {
            rsaKey.toRSAPublicKey();
            if (rsaKey.size() < 2048) {
                fail("jwksJson RSA keys must be at least 2048 bits");
            }
            return;
        }
        if (key instanceof ECKey ecKey) {
            if (!SUPPORTED_EC_CURVES.contains(ecKey.getCurve())) {
                fail("jwksJson contains an unsupported EC curve");
            }
            ecKey.toECPublicKey();
            return;
        }
        fail("jwksJson contains an unsupported key type");
    }

    private static boolean isCompatibleWithAlgorithm(
            JWK key,
            String configuredAlgorithm
    ) {
        if (!hasText(configuredAlgorithm)) {
            return true;
        }
        if (key.getAlgorithm() != null
                && !configuredAlgorithm.equals(
                        key.getAlgorithm().getName()
                )) {
            return false;
        }
        return switch (configuredAlgorithm) {
            case "RS256", "RS384", "RS512",
                 "PS256", "PS384", "PS512" -> key instanceof RSAKey;
            case "ES256" -> key instanceof ECKey ecKey
                    && Curve.P_256.equals(ecKey.getCurve());
            case "ES384" -> key instanceof ECKey ecKey
                    && Curve.P_384.equals(ecKey.getCurve());
            case "ES512" -> key instanceof ECKey ecKey
                    && Curve.P_521.equals(ecKey.getCurve());
            default -> false;
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void fail(String message) {
        throw new InvalidClientConfigurationException(message);
    }
}
