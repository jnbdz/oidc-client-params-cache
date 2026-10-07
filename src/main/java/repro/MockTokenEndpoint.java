package repro;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;

/**
 * Minimal token endpoint. Every token names the grant and the scope it was issued for, and every request is recorded.
 */
@Path("/mock")
public class MockTokenEndpoint {

    public static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
    public static final AtomicInteger COUNTER = new AtomicInteger();
    public static volatile long delayMillis = 0;
    public static volatile int expiresIn = 300;

    public static void reset() {
        REQUESTS.clear();
        delayMillis = 0;
        expiresIn = 300;
    }

    @POST
    @Path("/token")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public String token(MultivaluedMap<String, String> form) throws InterruptedException {
        String grant = form.getFirst("grant_type");
        String scope = form.getFirst("scope");
        String refreshToken = form.getFirst("refresh_token");
        REQUESTS.add("grant=" + grant + " scope=" + scope + (refreshToken == null ? "" : " refresh_token=" + refreshToken));
        if (delayMillis > 0) {
            Thread.sleep(delayMillis);
        }
        int id = COUNTER.incrementAndGet();
        String accessToken = "at" + id + "[" + grant + ",scope=" + scope + "]";
        String newRefreshToken = "rt" + id + "[scope=" + scope + "]";
        return "{\"access_token\":\"" + accessToken + "\",\"token_type\":\"Bearer\",\"expires_in\":" + expiresIn
                + ",\"refresh_token\":\"" + newRefreshToken + "\"}";
    }
}
