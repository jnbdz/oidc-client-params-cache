# OidcClient token cache ignores additional parameters

Reproducer for [quarkusio/quarkus#57231](https://github.com/quarkusio/quarkus/issues/57231).

The application contains its own token endpoint (`MockTokenEndpoint`). Every request it receives is recorded, and every
access token names the grant and the `scope` it was issued for, for example `at1[client_credentials,scope=a]`.

The tests request tokens with `scope=a` and `scope=b` and assert that each caller gets a token for its own scope:

| Test | Case |
|---|---|
| `TokenCacheParamsTest#cachedTokenIsReturnedForDifferentParams` | token cached for `scope=a`, then a call with `scope=b` |
| `TokenCacheParamsTest#inFlightRequestIsSharedByDifferentParams` | call with `scope=b` while the `scope=a` request is in flight |
| `TokenCacheParamsTest#refreshUsesOtherCallersParams` | `scope=a` token expires, then a call with `scope=b`, then `scope=a` again |
| `TokenProviderParamsTest#tokenProviderReturnsCachedTokenForDifferentParams` | `TokenProvider.getAccessToken(Map)` (4.0 only, `-Pv4`) |

The first three use a subclass of `AbstractTokensProducer` that overrides `additionalParameters()`, which works on 3.x.
Each test prints `RESULT` lines with the tokens returned and the requests the endpoint received.

## Run

```
./mvnw clean test -Dquarkus.version=3.40.1
./mvnw clean test -Dquarkus.version=4.0.0.Beta1 -Pv4
./mvnw clean test -Dquarkus.version=999-SNAPSHOT -Pv4   # after installing Quarkus main locally
```

## Results

| Case | 3.40.1 | 4.0.0.Beta1 | main 670cb610 | main + `prototype/TokensHelper.java` |
|---|---|---|---|---|
| cached token | fails | fails | fails | passes |
| in-flight request | fails | fails | fails | passes |
| refresh | fails | fails | fails | passes |
| `TokenProvider` | n/a | fails | fails | passes |

`prototype/TokensHelper.java` is a replacement for
`extensions/oidc-client/runtime/src/main/java/io/quarkus/oidc/client/runtime/TokensHelper.java` where the single cache
entry remembers the parameters it was acquired with. `run-all.sh` and `run-prototype.sh` are the scripts used to produce
the table above (they build Quarkus main from a shallow clone in a separate Maven repository).
