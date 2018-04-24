# TARA OpenID Connect näidistest


## Testide seadistamine ja käivitamine

**NB!** Vajalik on Java VM eelnev installatsioon. Arenduseks on kasutatud Oracle Java jdk 1.8.0_162 versiooni.

**NB!** Vajalik on juurdepääs TARA teenusele, selleks peab kas liituma RIA TARA testteenusega või paigaldama lokaalse TARA teenuse.

1. Hangi TARA testid:

 `git clone https://github.com/naare/TaraDemoTest.git`

2. Seadista testid vastavaks testitava TARA rakenduse otspunktidele. Selleks on kaks võimalust:

a) Võimalik on ette anda kahe erineva "profiili" properties faile "dev" ja "test" - vastavad properties failid [application-dev.properties](https://github.com/naare/TaraDemoTest/blob/master/src/test/resources/application-dev.properties) ja [application-test.properties](https://github.com/naare/TaraDemoTest/blob/master/src/test/resources/application-test.properties). Vaikeväärtusena on kasutusel profiil "dev", kuid seda on võimalik käivitamisel muuta parameetriga. Testide vaikeväärtused on seadistatud [application.properties](https://github.com/naare/TaraDemoTest/blob/master/src/test/resources/application.properties) failis.

 `-Dspring.profiles.active=test`

b) Andes vastavad parameetrid ette testide käivitamisel (kirjeldus testide käivitamise punktis)

Parameetrite kirjeldus:

| Parameeter | Vaikeväärtus | Kirjeldus |
|------------|--------------|-----------|
| test.tara.testRedirectUri | https://localhost:8451/oauth/response | TARA OpenID Connect teenuses registreeritud return URI. |
| test.tara.clientId | registeredClientId | TARA OpenID Connect teenuses registreeritud kliendi id. |
| test.tara.clientSecret | sharedSecret | TARA OpenID Connect teenuses registreeritud saladus. |
| test.tara.targetUrl | https://localhost:443 | TARA teenuse URL. |
| test.tara.jwksUrl | /oidc/jwks | TARA OpenID Connect avaliku võtme otspunkt. |
| test.tara.authorizeUrl | /oidc/authorize | TARA autentimise alustamise otspunkt. |
| test.tara.tokenUrl | /oidc/token | TARA tokeni otspunkt. |
| test.tara.loginUrl | /login | TARA sisse logimise otspunkt. |
| test.tara.configurationUrl | /oidc/.well-known/openid-configuration | TARA konfiguratsiooni otspunkt. |

4. Käivita test (kasutades profiiliga määratletud või vaikeväärtustega parameetreid):

`./mvnw clean install`

Testidele parameetrite ette andmine käivitamisel:

`./mvnw clean install -Dtest.tara.targetUrl=http://localhost:1881`

Test väljastavab konsooli RelayingParty-sse sisse tulevad ja välja minevad HTTP päringud
