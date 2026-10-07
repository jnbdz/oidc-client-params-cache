package repro;

import jakarta.enterprise.context.ApplicationScoped;

public class Producers {

    @ApplicationScoped
    public static class CachedCase extends ParamsTokensProducer {
    }

    @ApplicationScoped
    public static class InFlightCase extends ParamsTokensProducer {
    }

    @ApplicationScoped
    public static class RefreshCase extends ParamsTokensProducer {
    }
}
