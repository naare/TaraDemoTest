package ee.ria.tara;


import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.SignedJWT;
import ee.ria.tara.config.IntegrationTest;
import io.restassured.response.Response;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Base64;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertEquals;

@SpringBootTest(classes = MobileIdTest.class)
@Category(IntegrationTest.class)
public class MobileIdTest extends TestsBase {

    @Test
    public void mobileIdAuthenticationSuccess() throws InterruptedException, URISyntaxException, ParseException, JOSEException {

        state = RandomStringUtils.random(16);
        String sha256StateBase64 = Base64.getEncoder().encodeToString(DigestUtils.sha256(state));
        nonce = RandomStringUtils.random(16);
        String sha256NonceBase64 = Base64.getEncoder().encodeToString(DigestUtils.sha256(nonce));

        String location = given()
                .filter(cookieFilter)
                .queryParam("scope", "openid")
                .queryParam("response_type", "code")
                .queryParam("client_id", testTaraProperties.getClientId())
                .queryParam("redirect_uri", testTaraProperties.getTestRedirectUri())
                .queryParam("state", sha256StateBase64)
                .queryParam("nonce", sha256NonceBase64)
                .when()
                .redirects().follow(false)
                .log().all()
                .get(testTaraProperties.getAuthorizeUrl())
                .then()
                .log().all()
                .extract().response()
                .getHeader("location");

        String returnedUrlWithCode = authenticateAndGetAuthorizationCode(location, "60001019906", "00000766");

        String authorizationCode = getCode(returnedUrlWithCode);

        Response response = given()
                .queryParam("grant_type", "authorization_code")
                .queryParam("code", authorizationCode)
                .queryParam("redirect_uri", testTaraProperties.getTestRedirectUri())
                .log().all()
                .when()
                .header("Authorization", getAuthorization(testTaraProperties.getClientId(), testTaraProperties.getClientSecret()))
                .urlEncodingEnabled(true)
                .post(testTaraProperties.getTokenUrl())
                .then()
                .log().all()
                .extract().response();

        String idToken = response.getBody().jsonPath().getString("id_token");

        SignedJWT signedJWT = verifyTokenAndReturnSignedJwtObject(idToken);

        assertEquals("EE60001019906", signedJWT.getJWTClaimsSet().getSubject());
        assertEquals("MARY ÄNN", signedJWT.getJWTClaimsSet().getJSONObjectClaim("profile_attributes").getAsString("given_name"));
        assertEquals("O’CONNEŽ-ŠUSLIK TESTNUMBER", signedJWT.getJWTClaimsSet().getJSONObjectClaim("profile_attributes").getAsString("family_name"));
        assertEquals("+37200000766", signedJWT.getJWTClaimsSet().getJSONObjectClaim("profile_attributes").getAsString("mobile_number"));
    }
}
