package repro;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class TokenProviderParamsTest {

    @BeforeEach
    void reset() {
        MockTokenEndpoint.reset();
    }

    @Test
    void tokenProviderReturnsCachedTokenForDifferentParams() {
        String tokenA = given().queryParam("scope", "a").get("/token-provider").then().statusCode(200).extract().asString();
        String tokenB = given().queryParam("scope", "b").get("/token-provider").then().statusCode(200).extract().asString();

        TokenCacheParamsTest.report("4-token-provider", "GET ?scope=a -> " + tokenA, "GET ?scope=b -> " + tokenB);
        assertThat(tokenB).as("TokenProvider.getAccessToken(Map.of(\"scope\", \"b\"))").contains("scope=b");
    }
}
