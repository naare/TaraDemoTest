package ee.ria.tara;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.SignedJWT;
import ee.ria.tara.config.IntegrationTest;
import ee.ria.tara.config.TestConfiguration;
import ee.ria.tara.utils.SystemPropertyActiveProfileResolver;
import ee.ria.tara.config.TestTaraProperties;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.response.Response;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.text.ParseException;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.*;

@RunWith(SpringRunner.class)
@Category(IntegrationTest.class)
@ContextConfiguration(classes = TestConfiguration.class)
@ActiveProfiles(profiles = {"dev"}, resolver = SystemPropertyActiveProfileResolver.class)
public abstract class TestsBase {

    @Autowired
    protected TestTaraProperties testTaraProperties;

    protected JWKSet jwkSet;
    protected String tokenIssuer;
    public CookieFilter cookieFilter;
    protected String state;
    protected String nonce;

    @Before
    public void setUp() throws IOException, ParseException {
        URL url = new URL(testTaraProperties.getTargetUrl());
        port = url.getPort();
        baseURI = url.getProtocol() + "://" + url.getHost();

        jwkSet = JWKSet.load(new URL(testTaraProperties.getFullJwksUrl()));

        tokenIssuer = getIssuer(testTaraProperties.getTargetUrl()+testTaraProperties.getConfigurationUrl());
        Security.addProvider(new BouncyCastleProvider());
        cookieFilter = new CookieFilter();
    }

    private String getIssuer(String url) {
        return given()
//                .log().all()
                .when()
                .get(url)
                .then()
//                .log().all()
                .extract().response().getBody().jsonPath().getString("issuer");
    }

    protected String getAuthorization(String id ,String secret) {
        return String.format("Basic %s", Base64.getEncoder().encodeToString(String.format("%s:%s", id, secret).getBytes(StandardCharsets.UTF_8)));
    }

    protected String getCode (String url) throws URISyntaxException {
        List<NameValuePair> params = URLEncodedUtils.parse(new URI(url), "UTF-8");

        Map<String, String> queryParams = params.stream().collect(
                Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
        if (queryParams.get("state").equals(Base64.getEncoder().encodeToString(DigestUtils.sha256(state)))) {
            return queryParams.get("code");
        }
        else {
            throw new RuntimeException("State value do not match!");
        }
    }

    protected Boolean isTokenSignatureValid(SignedJWT signedJWT) throws JOSEException {
        List<JWK> matches = new JWKSelector(new JWKMatcher.Builder()
                .keyType(KeyType.RSA)
                .build())
                .select(jwkSet);

        RSAKey rsaKey = (RSAKey) matches.get(0);

        JWSVerifier verifier = new RSASSAVerifier(rsaKey);
        return signedJWT.verify(verifier);
    }

    protected SignedJWT verifyTokenAndReturnSignedJwtObject(String token) throws ParseException, JOSEException {
        SignedJWT signedJWT =  SignedJWT.parse(token);
        if (isTokenSignatureValid(signedJWT)) {
            if (signedJWT.getJWTClaimsSet().getAudience().get(0).equals(testTaraProperties.getClientId())) {
                if (signedJWT.getJWTClaimsSet().getIssuer().equals(tokenIssuer)) {
                    Date date = new Date();
                    if (date.after(signedJWT.getJWTClaimsSet().getNotBeforeTime()) && date.before(signedJWT.getJWTClaimsSet().getExpirationTime())) {
                        if (signedJWT.getJWTClaimsSet().getClaim("nonce").equals(Base64.getEncoder().encodeToString(DigestUtils.sha256(nonce)))) {
                            return signedJWT;
                        }
                        else {
                            throw new RuntimeException("Calculated nonce do not match the received one!");
                        }
                    }
                    else {
                        throw new RuntimeException("Token validity period is not valid! current: " + date + " nbf: "+signedJWT.getJWTClaimsSet().getNotBeforeTime()+" exp: "+signedJWT.getJWTClaimsSet().getExpirationTime());
                    }
                }
                else {
                    throw new RuntimeException("Token Issuer is not valid! Expected: "+tokenIssuer+" actual: "+signedJWT.getJWTClaimsSet().getIssuer());
                }
            }
            else {
                throw new RuntimeException("Token Audience is not valid! Expected: "+testTaraProperties.getClientId()+" actual: "+signedJWT.getJWTClaimsSet().getAudience().get(0));
            }
        }
        else {
            throw new RuntimeException("Token Signature is not valid!");
        }
    }

    protected String pollForAuthentication(String execution, Integer intervalMillis) throws InterruptedException {
        DateTime endTime = new DateTime().plusMillis(intervalMillis*3 + 200);
        while(new DateTime().isBefore(endTime)) {
            Thread.sleep(intervalMillis);
            Response response = given()
                    .filter(cookieFilter)
                    .relaxedHTTPSValidation()
                    .redirects().follow(false)
                    .formParam("execution", execution)
                    .formParam("_eventId", "check")
                    .queryParam("client_id", testTaraProperties.getClientId())
                    .queryParam("redirect_uri", testTaraProperties.getTestRedirectUri())
//                .log().all()
                    .when()
                    .post(testTaraProperties.getLoginUrl())
                    .then()
//                .log().all()
                    .extract().response();
            if (response.statusCode() == 302) {
                return response.getHeader("location");
            }
        }
        throw new RuntimeException("No MID response in: "+ (intervalMillis*3 + 200) +" millis");
    }

    protected String authenticateAndGetAuthorizationCode(String location, String idCode, String mobileNo) throws InterruptedException {
        String execution = given()
                .filter(cookieFilter)
                .relaxedHTTPSValidation()
                .when()
                .redirects().follow(false)
                .urlEncodingEnabled(false)
//                .log().all()
                .get(location)
                .then()
//                .log().all()
                .extract().response()
                .getBody().htmlPath().getString("**.findAll { it.@name == 'execution' }[0].@value");

        String execution2 = given()
                .filter(cookieFilter).relaxedHTTPSValidation()
                .formParam("execution", execution)
                .formParam("_eventId", "submit")
                .formParam("mobileNumber", mobileNo)
                .formParam("moblang", "et")
                .formParam("principalCode", idCode)
//                .queryParam("service", testTaraProperties.getServiceUrl())
//                .queryParam("client_name", testTaraProperties.getCasClientId())
                .queryParam("client_id", testTaraProperties.getClientId())
                .queryParam("redirect_uri", testTaraProperties.getTestRedirectUri())
//                .log().all()
                .when()
                .post(testTaraProperties.getLoginUrl())
                .then()
//                .log().all()
                .extract().response()
                .htmlPath().getString("**.findAll { it.@name == 'execution' }[0].@value");

        String location2 = pollForAuthentication(execution2, 7000);

        String location3 = given()
                .filter(cookieFilter)
                .relaxedHTTPSValidation()
                .redirects().follow(false)
//                .log().all()
                .when()
                .urlEncodingEnabled(false)
                .get(location2)
                .then()
//                .log().all()
                .extract().response()
                .getHeader("location");

        String location4 = given()
                .filter(cookieFilter)
                .relaxedHTTPSValidation()
//                .log().all()
                .when()
                .redirects().follow(false)
                .urlEncodingEnabled(false)
                .get(location3)
                .then()
//                .log().all()
                .extract().response()
                .getHeader("Location");
        return location4;
    }
}
