package io.quarkus.oidc.client.runtime;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.BiConsumer;

import org.jboss.logging.Logger;

import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.Tokens;
import io.smallrye.mutiny.Uni;

public class TokensHelper {
    private static final Logger LOG = Logger.getLogger(TokensHelper.class);

    @SuppressWarnings("unused")
    private volatile TokenRequestState tokenRequestState;

    private static final AtomicReferenceFieldUpdater<TokensHelper, TokenRequestState> tokenRequestStateUpdater = AtomicReferenceFieldUpdater
            .newUpdater(TokensHelper.class, TokenRequestState.class, "tokenRequestState");

    public void initTokens(OidcClient oidcClient) {
        initTokens(oidcClient, Map.of());
    }

    public void initTokens(OidcClient oidcClient, Map<String, String> additionalParameters) {
        Map<String, String> params = copy(additionalParameters);
        //init the tokens, this just happens in a blocking manner for now
        tokenRequestStateUpdater.set(this,
                new TokenRequestState(oidcClient.getTokens(params).await().indefinitely(), params));
    }

    public Uni<Tokens> getTokens(OidcClient oidcClient) {
        return getTokens(oidcClient, Map.of(), false);
    }

    public Uni<Tokens> getTokens(OidcClient oidcClient, Map<String, String> additionalParameters, boolean forceNewTokens) {
        final Map<String, String> params = copy(additionalParameters);
        TokenRequestState currentState = null;
        TokenRequestState newState = null;
        //if the tokens are expired we refresh them in an async manner
        //we use CAS to make sure we only make a single request
        for (;;) {
            currentState = tokenRequestStateUpdater.get(this);
            if (currentState == null) {
                //init the initial state
                //note that this can still happen at runtime as if there is an error then the state will be null
                newState = new TokenRequestState(prepareUni(oidcClient.getTokens(params), params), params);
                if (tokenRequestStateUpdater.compareAndSet(this, currentState, newState)) {
                    return newState.tokenUni;
                }
                //rerun the CAS loop
            } else if (currentState.tokenUni != null) {
                if (currentState.params.equals(params)) {
                    return currentState.tokenUni;
                }
                // a request for other parameters is in flight: do not join it and do not cache this one
                LOG.debugf("Tokens requested with different additional parameters are being acquired,"
                        + " acquiring separate tokens");
                return oidcClient.getTokens(params);
            } else if (forceNewTokens || !currentState.params.equals(params)) {
                if (forceNewTokens) {
                    LOG.debugf("Forcing acquisition of new tokens for client %s", currentState.tokens.getClientId());
                } else {
                    LOG.debugf("Additional parameters have changed, acquiring new tokens for client %s",
                            currentState.tokens.getClientId());
                }
                newState = new TokenRequestState(prepareUni(oidcClient.getTokens(params), params), params);
                if (tokenRequestStateUpdater.compareAndSet(this, currentState, newState)) {
                    return newState.tokenUni;
                }
                //rerun the CAS loop
            } else {
                Tokens tokens = currentState.tokens;

                if (tokens.isAccessTokenExpired() || tokens.isAccessTokenWithinRefreshInterval()) {
                    LOG.debugf("Starting refreshing the tokens for client %s", tokens.getClientId());
                    final boolean refreshTokenValid = tokens.getRefreshToken() != null && !tokens.isRefreshTokenExpired();
                    if (!refreshTokenValid) {
                        LOG.debugf("Refresh token is not available or has expired, "
                                + "acquiring new tokens instead for client %s", tokens.getClientId());
                    }
                    newState = new TokenRequestState(
                            prepareUni(refreshTokenValid
                                    ? oidcClient.refreshTokens(tokens.getRefreshToken(), params)
                                    : oidcClient.getTokens(params), params),
                            params);
                    if (tokenRequestStateUpdater.compareAndSet(this, currentState, newState)) {
                        return newState.tokenUni;
                    }
                    //rerun the CAS loop
                } else {
                    return Uni.createFrom().item(tokens);
                }
            }
        }
    }

    private static Map<String, String> copy(Map<String, String> additionalParameters) {
        return additionalParameters == null || additionalParameters.isEmpty() ? Map.of()
                : Collections.unmodifiableMap(new HashMap<>(additionalParameters));
    }

    private Uni<Tokens> prepareUni(Uni<Tokens> tokens, Map<String, String> params) {
        return tokens.onItemOrFailure().invoke(new BiConsumer<Tokens, Throwable>() {
            @Override
            public void accept(Tokens tokens, Throwable throwable) {
                //we only have a single outstanding request
                //so we don't need to CAS
                if (tokens != null) {
                    tokenRequestStateUpdater.set(TokensHelper.this, new TokenRequestState(tokens, params));
                } else {
                    tokenRequestStateUpdater.set(TokensHelper.this, null);
                }
            }
        })
                // prevent next subscriptions to trigger multiple times the HTTP request before the end of the first one
                .memoize().indefinitely();
    }

    static class TokenRequestState {
        final Tokens tokens;
        final Uni<Tokens> tokenUni;
        final Map<String, String> params;

        TokenRequestState(Tokens tokens, Map<String, String> params) {
            this.tokens = tokens;
            this.tokenUni = null;
            this.params = params;
        }

        TokenRequestState(Uni<Tokens> tokensUni, Map<String, String> params) {
            this.tokens = null;
            this.tokenUni = tokensUni;
            this.params = params;
        }
    }
}
