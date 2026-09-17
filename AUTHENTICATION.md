# Deployment authentication validation

awesome-sites has its own auth system. https://github.com/Adobe-DesignTechnology/awesome-api It sets a GWT token in the `awesome_auth_token` cookie. If I move the site to be an awesome site, the games backend will need to authenticate against their system rather than using the current hashed shared auth token.

## Validate a session

```bash
curl --silent --show-error --include \
  --cookie "awesome_auth_token=$TOKEN" \
  'https://<site-plug>.awesome-sites.corp.adobe.com/_auth/api/webapp/session'The endpoint validates the token for this specific deployed webapp. The
```

| Response                                    | Meaning                                              |
| ------------------------------------------- | ---------------------------------------------------- |
| `200` with JSON payload                     | The cookie is valid for this site.                   |
| `401` or `403`                              | The cookie is missing, expired, revoked, or invalid. |
| Error JSON indicating no or invalid session | The cookie is not valid for this site.               |

Test only `awesome_auth_token`. Supplying an entire browser cookie jar can
produce an ambiguous result when another cookie authenticates the request.

## ## Revoke a compromised session

If the cookie value is exposed, revoke it and sign in again:

```bash
curl --silent --show-error --include \
  --cookie "awesome_auth_token=$TOKEN" \
  'https://analytics-coworker-enablement.awesome-sites.corp.adobe.com/_auth/api/webapp/logout'
```
