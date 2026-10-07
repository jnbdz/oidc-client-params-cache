package repro;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.oidc.client.Tokens;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;

@QuarkusTest
public class TokenCacheParamsTest {

    private static final Duration WAIT = Duration.ofSeconds(20);

    @Inject
    Producers.CachedCase cachedCase;

    @Inject
    Producers.InFlightCase inFlightCase;

    @Inject
    Producers.RefreshCase refreshCase;

    @BeforeEach
    void reset() {
        MockTokenEndpoint.reset();
    }

    @Test
    void cachedTokenIsReturnedForDifferentParams() {
        cachedCase.setParams(Map.of("scope", "a"));
        String tokenA = cachedCase.getTokens().await().atMost(WAIT).getAccessToken();
        cachedCase.setParams(Map.of("scope", "b"));
        String tokenB = cachedCase.getTokens().await().atMost(WAIT).getAccessToken();

        report("1-cached", "scope=a -> " + tokenA, "scope=b -> " + tokenB);
        assertThat(tokenB).as("token returned for scope=b").contains("scope=b");
    }

    @Test
    void inFlightRequestIsSharedByDifferentParams() {
        MockTokenEndpoint.delayMillis = 1500;
        inFlightCase.setParams(Map.of("scope", "a"));
        Uni<Tokens> first = inFlightCase.getTokens();
        CompletableFuture<Tokens> firstResult = first.subscribeAsCompletionStage().toCompletableFuture();
        inFlightCase.setParams(Map.of("scope", "b"));
        Uni<Tokens> second = inFlightCase.getTokens();
        CompletableFuture<Tokens> secondResult = second.subscribeAsCompletionStage().toCompletableFuture();

        String tokenA = firstResult.join().getAccessToken();
        String tokenB = secondResult.join().getAccessToken();

        report("2-in-flight", "scope=a -> " + tokenA, "scope=b -> " + tokenB);
        assertThat(tokenB).as("token returned for scope=b while scope=a was in flight").contains("scope=b");
    }

    @Test
    void refreshUsesOtherCallersParams() throws InterruptedException {
        MockTokenEndpoint.expiresIn = 2;
        refreshCase.setParams(Map.of("scope", "a"));
        String tokenA = refreshCase.getTokens().await().atMost(WAIT).getAccessToken();

        Thread.sleep(3000);

        refreshCase.setParams(Map.of("scope", "b"));
        String tokenB = refreshCase.getTokens().await().atMost(WAIT).getAccessToken();
        refreshCase.setParams(Map.of("scope", "a"));
        String tokenAAgain = refreshCase.getTokens().await().atMost(WAIT).getAccessToken();

        report("3-refresh", "scope=a -> " + tokenA, "after expiry, scope=b -> " + tokenB,
                "then scope=a -> " + tokenAAgain);
        assertThat(tokenAAgain).as("token returned for scope=a after another caller refreshed with scope=b")
                .contains("scope=a");
    }

    static void report(String scenario, String... lines) {
        List<String> requests = List.copyOf(MockTokenEndpoint.REQUESTS);
        StringBuilder sb = new StringBuilder("RESULT ").append(scenario).append('\n');
        for (String line : lines) {
            sb.append("RESULT   ").append(line).append('\n');
        }
        sb.append("RESULT   token endpoint requests (").append(requests.size()).append("):\n");
        for (String request : requests) {
            sb.append("RESULT     ").append(request).append('\n');
        }
        System.out.print(sb);
    }
}
