package repro;

import java.util.Map;

import io.quarkus.oidc.client.runtime.AbstractTokensProducer;

/**
 * A custom producer whose additional parameters change per call, as a custom filter would do.
 * Each subclass gets its own token cache (its own TokensHelper).
 */
public abstract class ParamsTokensProducer extends AbstractTokensProducer {

    private volatile Map<String, String> params = Map.of();

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    @Override
    protected Map<String, String> additionalParameters() {
        return params;
    }
}
