package com.soundcloud.api;

import static junit.framework.Assert.fail;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soundcloud.api.fakehttp.FakeHttpLayer;
import com.soundcloud.api.fakehttp.RequestMatcher;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.AuthenticationHandler;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.RedirectHandler;
import org.apache.http.client.RequestDirector;
import org.apache.http.client.UserTokenHandler;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;
import org.apache.http.protocol.HttpRequestExecutor;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;


public class ApiWrapperTest {
    private ApiWrapper api;
    final FakeHttpLayer layer = new FakeHttpLayer();
    @Before
    public void setup() {
        api = new ApiWrapper("invalid", "invalid", URI.create("redirect://me"), null, Env.SANDBOX) {
            private static final long serialVersionUID = 12345; // silence warnings
            @Override
            protected RequestDirector getRequestDirector(HttpRequestExecutor requestExec,
                                                         ClientConnectionManager conman,
                                                         ConnectionReuseStrategy reustrat,
                                                         ConnectionKeepAliveStrategy kastrat,
                                                         HttpRoutePlanner rouplan,
                                                         HttpProcessor httpProcessor,
                                                         HttpRequestRetryHandler retryHandler,
                                                         RedirectHandler redirectHandler,
                                                         AuthenticationHandler targetAuthHandler,
                                                         AuthenticationHandler proxyAuthHandler,
                                                         UserTokenHandler stateHandler,
                                                         HttpParams params) {
                return new RequestDirector() {
                    @Override
                    public HttpResponse execute(HttpHost target, HttpRequest request, HttpContext context)
                            throws HttpException, IOException {
                        return layer.emulateRequest(target, request, context, this);
                    }
                };
            }
        };
        layer.clearHttpResponseRules();
    }

    @Test(expected = IllegalArgumentException.class)
    public void loginShouldThrowIllegalArgumentException() throws Exception {
        api.login(null, null);
    }

    @Test
    public void clientCredentialsShouldDefaultToSignupScope() throws Exception {
        layer.addPendingHttpResponse(200, "{\n" +
                "  \"access_token\":  \"04u7h-4cc355-70k3n\",\n" +
                "  \"expires_in\":    3600,\n" +
                "  \"scope\":         \"signup\",\n" +
                "  \"refresh_token\": \"04u7h-r3fr35h-70k3n\"\n" +
                "}");

        Token t = api.clientCredentials();
        assertThat(t.access, equalTo("04u7h-4cc355-70k3n"));
        assertThat(t.refresh, equalTo("04u7h-r3fr35h-70k3n"));
        assertThat(t.scope, equalTo("signup"));
        assertTrue(t.signupScoped());
        assertNotNull(t.getExpiresIn());
    }

    @Test(expected = CloudAPI.InvalidTokenException.class)
    public void clientCredentialsShouldThrowIfScopeCanNotBeObtained() throws Exception {
        layer.addPendingHttpResponse(200, "{\n" +
                "  \"access_token\":  \"04u7h-4cc355-70k3n\",\n" +
                "  \"expires_in\":    3600,\n" +
                "  \"scope\":         \"loser\",\n" +
                "  \"refresh_token\": \"04u7h-r3fr35h-70k3n\"\n" +
                "}");
        api.clientCredentials("unlimitedammo");
    }

    @Test
    public void exchangeOAuth1Token() throws Exception {
        layer.addPendingHttpResponse(200, "{\n" +
                "  \"access_token\":  \"04u7h-4cc355-70k3n\",\n" +
                "  \"expires_in\":    3600,\n" +
                "  \"scope\":         \"*\",\n" +
                "  \"refresh_token\": \"04u7h-r3fr35h-70k3n\"\n" +
                "}");
        api.exchangeOAuth1Token("oldtoken");
    }

    @Test(expected = IllegalArgumentException.class)
    public void exchangeOAuth1TokenWithEmptyTokenShouldThrow() throws Exception {
        api.exchangeOAuth1Token(null);
    }

    @Test
    public void shouldGetTokensWhenLoggingIn() throws Exception {
        layer.addPendingHttpResponse(200, "{\n" +
                "  \"access_token\":  \"04u7h-4cc355-70k3n\",\n" +
                "  \"expires_in\":    3600,\n" +
                "  \"scope\":         \"*\",\n" +
                "  \"refresh_token\": \"04u7h-r3fr35h-70k3n\"\n" +
                "}");

        Token t = api.login("foo", "bar");

        assertThat(t.access, equalTo("04u7h-4cc355-70k3n"));
        assertThat(t.refresh, equalTo("04u7h-r3fr35h-70k3n"));
        assertThat(t.scope, equalTo("*"));
        assertNotNull(t.getExpiresIn());
    }

    @Test
    public void shouldGetTokensWhenLoggingInWithNonExpiringScope() throws Exception {
        layer.addPendingHttpResponse(200, "{\n" +
                "  \"access_token\":  \"04u7h-4cc355-70k3n\",\n" +
                "  \"scope\":         \"* non-expiring\"\n" +
                "}");

        Token t = api.login("foo", "bar", Token.SCOPE_NON_EXPIRING);
        assertThat(t.access, equalTo("04u7h-4cc355-70k3n"));
        assertThat(t.refresh, is(nullValue()));
        assertThat(t.getExpiresIn(), is(nullValue()));
        assertThat(t.scoped(Token.SCOPE_NON_EXPIRING), is(true));
        assertThat(t.scoped(Token.SCOPE_DEFAULT), is(true));
    }

    @Test
    public void shouldGetTokensWhenLoggingInViaAuthorizationCode() throws Exception {
        layer.addPendingHttpResponse(200, "{\n" +
                "  \"access_token\":  \"04u7h-4cc355-70k3n\",\n" +
                "  \"expires_in\":    3600,\n" +
                "  \"scope\":         \"*\",\n" +
                "  \"refresh_token\": \"04u7h-r3fr35h-70k3n\"\n" +
                "}");

        Token t = api.authorizationCode("code");

        assertThat(t.access, equalTo("04u7h-4cc355-70k3n"));
        assertThat(t.refresh, equalTo("04u7h-r3fr35h-70k3n"));
        assertThat(t.scope, equalTo("*"));
        assertNotNull(t.getExpiresIn());
    }

    @Test
    public void shouldGetTokensWhenLoggingInViaAuthorizationCodeAndScope() throws Exception {
        layer.addPendingHttpResponse(200, "{\n" +
                "  \"access_token\":  \"04u7h-4cc355-70k3n\",\n" +
                "  \"scope\":         \"* non-expiring\"\n" +
                "}");

        Token t = api.authorizationCode("code", Token.SCOPE_NON_EXPIRING);

        assertThat(t.access, equalTo("04u7h-4cc355-70k3n"));
        assertThat(t.refresh, is(nullValue()));

        assertThat(t.scoped(Token.SCOPE_DEFAULT), is(true));
        assertThat(t.scoped(Token.SCOPE_NON_EXPIRING), is(true));
        assertNull(t.getExpiresIn());
    }

    @Test(expected = IOException.class)
    public void shouldThrowIOExceptionWhenLoginFailed() throws Exception {
        layer.addPendingHttpResponse(401, "{\n" +
                "  \"error\":  \"Error!\"\n" +
                "}");
        api.login("foo", "bar");
    }


    @Test(expected = IOException.class)
    public void shouldThrowIOExceptionWhenInvalidJSONReturned() throws Exception {
        layer.addPendingHttpResponse(200, "I'm invalid JSON!");
        api.login("foo", "bar");
    }

    @Test
    public void shouldContainInvalidJSONInExceptionMessage() throws Exception {
        layer.addPendingHttpResponse(200, "I'm invalid JSON!");
        try {
            api.login("foo", "bar");
            fail("expected IOException");
        } catch (IOException e) {
            assertThat(e.getMessage(), containsString("I'm invalid JSON!"));
        }
    }

    @Test
    public void shouldRefreshToken() throws Exception {
        layer.addPendingHttpResponse(200, "{\n" +
                "  \"access_token\":  \"fr3sh\",\n" +
                "  \"expires_in\":    3600,\n" +
                "  \"scope\":         null,\n" +
                "  \"refresh_token\": \"refresh\"\n" +
                "}");


        api.setToken(new Token("access", "refresh"));
        assertThat(api
                .refreshToken()
                .access,
                equalTo("fr3sh"));
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowIllegalStateExceptionWhenNoRefreshToken() throws Exception {
        api.refreshToken();
    }

    @Test
    public void shouldResolveUris() throws Exception {
        HttpResponse r = mock(HttpResponse.class);
        StatusLine line = mock(StatusLine.class);
        when(line.getStatusCode()).thenReturn(302);
        when(r.getStatusLine()).thenReturn(line);
        Header location = mock(Header.class);
        when(location.getValue()).thenReturn("http://api.soundcloud.com/users/1000");
        when(r.getFirstHeader(anyString())).thenReturn(location);
        layer.addHttpResponseRule(new RequestMatcher() {
            @Override
            public boolean matches(HttpRequest request) {
                return true;
            }
        }, r);
        assertThat(api.resolve("http://soundcloud.com/crazybob"), is(1000L));
    }

    @Test(expected = CloudAPI.ResolverException.class)
    public void resolveShouldReturnNegativeOneWhenInvalid() throws Exception {
        layer.addPendingHttpResponse(404, "Not found");
        api.resolve("http://soundcloud.com/nonexisto");
    }

    @Test
    public void shouldGetContent() throws Exception {
        layer.addHttpResponseRule("/some/resource?a=1", "response");
        assertThat(Http.getString(api.get(Request.to("/some/resource").with("a", "1"))),
                equalTo("response"));
    }

    @Test
    public void shouldPostContent() throws Exception {
        HttpResponse resp = mock(HttpResponse.class);
        layer.addHttpResponseRule("POST", "/foo/something", resp);
        assertThat(api.post(Request.to("/foo/something").with("a", 1)),
                equalTo(resp));
    }

    @Test
    public void shouldPutContent() throws Exception {
        HttpResponse resp = mock(HttpResponse.class);
        layer.addHttpResponseRule("PUT", "/foo/something", resp);
        assertThat(api.put(Request.to("/foo/something").with("a", 1)),
                equalTo(resp));
    }

    @Test
    public void shouldDeleteContent() throws Exception {
        HttpResponse resp = mock(HttpResponse.class);
        layer.addHttpResponseRule("DELETE", "/foo/something", resp);
        assertThat(api.delete(new Request("/foo/something")), equalTo(resp));
    }

    @Test
    public void testGetOAuthHeader() throws Exception {
        Header h = ApiWrapper.createOAuthHeader(new Token("foo", "refresh"));
        assertThat(h.getName(), equalTo("Authorization"));
        assertThat(h.getValue(), equalTo("OAuth foo"));
    }

    @Test
    public void testGetOAuthHeaderNullToken() throws Exception {
        Header h = ApiWrapper.createOAuthHeader(null);
        assertThat(h.getName(), equalTo("Authorization"));
        assertThat(h.getValue(), equalTo("OAuth invalidated"));
    }

    @Test
    public void shouldGenerateUrlWithoutParameters() throws Exception {
        assertThat(
                api.getURI(new Request("/my-resource"), true, true).toString(),
                equalTo("https://api.sandbox-soundcloud.com/my-resource")
        );
    }

    @Test
    public void shouldGenerateUrlWithoutSSL() throws Exception {
        assertThat(
                api.getURI(new Request("/my-resource"), true, false).toString(),
                equalTo("http://api.sandbox-soundcloud.com/my-resource")
        );
    }

    @Test
    public void shouldGenerateUrlWithParameters() throws Exception {
        assertThat(
                api.getURI(Request.to("/my-resource").with("foo", "bar"), true, true).toString(),
                equalTo("https://api.sandbox-soundcloud.com/my-resource?foo=bar")
        );
    }

    @Test
    public void shouldGenerateUrlForWebHost() throws Exception {
        assertThat(
                api.getURI(Request.to("/my-resource"), false, true).toString(),
                equalTo("https://sandbox-soundcloud.com/my-resource")
        );
    }


    @Test
    public void shouldGenerateURIForLoginAuthCode() throws Exception {
        assertThat(
            api.authorizationCodeUrl().toString(),
                equalTo("https://sandbox-soundcloud.com/connect"+
                        "?redirect_uri=redirect%3A%2F%2Fme&client_id=invalid&response_type=code")
            );
    }


    @Test
    public void shouldGenerateURIForLoginAuthCodeWithDifferentEndPoint() throws Exception {
        assertThat(
            api.authorizationCodeUrl(Endpoints.FACEBOOK_CONNECT).toString(),
                equalTo("https://sandbox-soundcloud.com/connect/via/facebook"+
                        "?redirect_uri=redirect%3A%2F%2Fme&client_id=invalid&response_type=code")
        );
    }

    @Test
    public void shouldIncludeScopeInAuthorizationUrl() throws Exception {
        assertThat(
            api.authorizationCodeUrl(Endpoints.FACEBOOK_CONNECT, Token.SCOPE_NON_EXPIRING).toString(),
                equalTo("https://sandbox-soundcloud.com/connect/via/facebook"+
                        "?redirect_uri=redirect%3A%2F%2Fme&client_id=invalid&response_type=code&scope=non-expiring")
        );
    }

    @Test
    public void shouldCallTokenStateListenerWhenTokenIsInvalidated() throws Exception {
        CloudAPI.TokenListener listener = mock(CloudAPI.TokenListener.class);
        api.setTokenListener(listener);
        final Token old = api.getToken();
        api.invalidateToken();
        verify(listener).onTokenInvalid(old);
    }

    @Test
    public void invalidateTokenShouldTryToGetAlternativeToken() throws Exception {
        CloudAPI.TokenListener listener = mock(CloudAPI.TokenListener.class);
        final Token cachedToken = new Token("new", "fresh");
        api.setTokenListener(listener);
        when(listener.onTokenInvalid(api.getToken())).thenReturn(cachedToken);
        assertThat(api.invalidateToken(), equalTo(cachedToken));
    }

    @Test
    public void invalidateTokenShouldReturnNullIfNoListenerAvailable() throws Exception {
        assertThat(api.invalidateToken(), is(nullValue()));
    }

    @Test
    public void shouldCallTokenStateListenerWhenTokenIsRefreshed() throws Exception {
        layer.addPendingHttpResponse(200, "{\n" +
                "  \"access_token\":  \"fr3sh\",\n" +
                "  \"expires_in\":    3600,\n" +
                "  \"scope\":         null,\n" +
                "  \"refresh_token\": \"refresh\"\n" +
                "}");

        CloudAPI.TokenListener listener = mock(CloudAPI.TokenListener.class);

        api.setToken(new Token("access", "refresh"));
        api.setTokenListener(listener);
        api.refreshToken();
        verify(listener).onTokenRefreshed(api.getToken());
    }

    @Test
    public void shouldSerializeAndDeserializeWrapper() throws Exception {
        ApiWrapper wrapper = new ApiWrapper("client", "secret", null, new Token("1", "2"), Env.SANDBOX);
        File ser = File.createTempFile("serialized_wrapper", "ser");
        wrapper.toFile(ser);
        ApiWrapper other = ApiWrapper.fromFile(ser);
        assertThat(wrapper.getToken(), equalTo(other.getToken()));
        assertThat(wrapper.env, equalTo(other.env));

        // make sure we can still use listeners after deserializing
        CloudAPI.TokenListener listener = mock(CloudAPI.TokenListener.class);
        other.setTokenListener(listener);

        final Token old = other.getToken();
        other.invalidateToken();
        verify(listener).onTokenInvalid(old);
    }

    @Test
    public void testAddScope() throws Exception {
        assertThat(ApiWrapper.addScope(new Request(), new String[] { "foo", "bar"}).getParams().get("scope"),
                equalTo("foo bar"));

        assertFalse(ApiWrapper.addScope(new Request(), new String[] {}).getParams().containsKey("scope"));
        assertFalse(ApiWrapper.addScope(new Request(), null).getParams().containsKey("scope"));
    }
}
