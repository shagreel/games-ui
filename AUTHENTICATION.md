# Deployment authentication validation

This site is protected by the Awesome deployment authentication service. Its
browser session is represented by the `awesome_auth_token` cookie.

## Validate a session

Use the site's auth-service endpoint, not the Awesome API endpoint:

```bash
read -s TOKEN

curl --silent --show-error --include \
  --cookie "awesome_auth_token=$TOKEN" \
  'https://analytics-coworker-enablement.awesome-sites.corp.adobe.com/_auth/api/webapp/session'

unset TOKEN
```

The endpoint validates the token for this specific deployed webapp. The
deployment edge verifies the JWT and checks its session against the
deployment-auth session store.

| Response | Meaning |
| --- | --- |
| `200` with session JSON | The cookie is valid for this site. |
| `401` or `403` | The cookie is missing, expired, revoked, or invalid. |
| Error JSON indicating no or invalid session | The cookie is not valid for this site. |

Test only `awesome_auth_token`. Supplying an entire browser cookie jar can
produce an ambiguous result when another cookie authenticates the request.

## Why other endpoints are not valid checks

`https://awesome-api.adobe.io/api/auth/whoami` belongs to the separate
Awesome API Better Auth system. It uses database-backed cookies with the
`awesome-server` prefix, so a `401` from that endpoint does not establish
whether `awesome_auth_token` is valid.

The deployed site's root URL is not a reliable validation endpoint. Depending
on the request, it can return a `200` response for the auth application even
when the supplied cookie is invalid.

## Revoke a compromised session

If the cookie value is exposed, revoke it and sign in again:

```bash
read -s TOKEN

curl --silent --show-error --include \
  --cookie "awesome_auth_token=$TOKEN" \
  'https://analytics-coworker-enablement.awesome-sites.corp.adobe.com/_auth/api/webapp/logout'

unset TOKEN
```

Do not paste tokens into commands, source files, issue comments, or chat
messages. `read -s` prevents the value from being echoed and avoids putting it
directly into shell history.
