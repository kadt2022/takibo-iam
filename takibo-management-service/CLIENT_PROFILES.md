# OAuth2 Client Profiles (Takibo)

Validation is the source of truth; this document is guidance.

## Security baseline

- At least one grant type is required. Supported values are `authorization_code`,
  `refresh_token`, `client_credentials` and
  `urn:ietf:params:oauth:grant-type:device_code`.
- Redirect and post-logout URIs, as well as CORS origins, require HTTPS. Plain HTTP
  is accepted only for loopback development hosts (`localhost`, `127.0.0.0/8`,
  `::1`). Redirect URIs cannot contain user-info or fragments.
- Token TTL values must be positive. Access and ID tokens are limited to 24 hours,
  refresh tokens to 365 days; when both are set, the refresh-token TTL must be
  greater than the access-token TTL.
- Client-secret expiration must be in the future and is forbidden for public or
  key-authenticated clients.
- `jwksUri` and `jwksJson` are mutually exclusive. Remote JWKS endpoints require
  HTTPS, and embedded JWK Sets may contain public asymmetric keys only.
- ID-token signing algorithm `none` and symmetric algorithms are rejected.

## SPA (Public + auth code)

- `clientType`: `PUBLIC`
- `tokenEndpointAuthMethod`: `none`
- `requireClientSecret`: `false`
- `grantTypes`: `["authorization_code","refresh_token"]` (no `client_credentials`)
- `requirePkce`: `true`
- `redirectUris`: required
- `corsOrigins`: required

## Web App / BFF (Confidential + auth code)

- `clientType`: `CONFIDENTIAL`
- `tokenEndpointAuthMethod`: not `none`
- `requireClientSecret`: `true`
- `grantTypes`: includes `authorization_code` (no `client_credentials`)
- `redirectUris`: required
- `requirePkce`: optional

## Backend Service (client_credentials only)

If `client_credentials` is present, Takibo applies the backend profile.

Normalized before validation:
- `redirectUris`: `[]`
- `postLogoutRedirectUris`: `[]`
- `corsOrigins`: `[]`
- `requirePkce`: `false`
- `requireConsent`: `false`
- `clientType`: `CONFIDENTIAL`
- `tokenEndpointAuthMethod`: default `client_secret_basic` if absent
- `requireClientSecret`: `true`

Validation rules:
- `grantTypes` must be exactly `["client_credentials"]`
- `tokenEndpointAuthMethod` must not be `none`
- `redirectUris/postLogoutRedirectUris/corsOrigins` must be empty
