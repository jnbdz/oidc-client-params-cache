package repro;

import java.util.Map;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import io.quarkus.oidc.client.spi.TokenProvider;
import io.smallrye.mutiny.Uni;

@Path("/token-provider")
public class TokenProviderResource {

    @Inject
    TokenProvider tokenProvider;

    @GET
    public Uni<String> token(@QueryParam("scope") String scope) {
        return tokenProvider.getAccessToken(Map.of("scope", scope));
    }
}
