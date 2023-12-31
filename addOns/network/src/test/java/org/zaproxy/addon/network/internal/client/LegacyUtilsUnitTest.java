/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2022 The ZAP Development Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zaproxy.addon.network.internal.client;

import static org.apache.hc.client5.http.cookie.Cookie.DOMAIN_ATTR;
import static org.apache.hc.client5.http.cookie.Cookie.PATH_ATTR;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.HttpState;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.junit.jupiter.api.Test;

/** Unit test for {@link LegacyUtils}. */
class LegacyUtilsUnitTest {

    @Test
    void shouldConvertNullHttpStateToEmptyCookieStore() {
        // Given
        HttpState httpState = null;
        // When
        CookieStore cookieStore = LegacyUtils.httpStateToCookieStore(httpState);
        // Then
        assertThat(cookieStore.getCookies(), is(empty()));
    }

    @Test
    void shouldConvertHttpStateToCookieStore() {
        // Given
        HttpState httpState = new HttpState();
        String domain = "example.org";
        String name = "Name";
        String value = "Value";
        Date expiryDate = new Date(System.currentTimeMillis() + 60_000L);
        httpState.addCookie(cookieLegacy(domain, name, value, expiryDate));
        // When
        CookieStore cookieStore = LegacyUtils.httpStateToCookieStore(httpState);
        // Then
        assertThat(cookieStore.getCookies(), hasSize(1));
        org.apache.hc.client5.http.cookie.Cookie cookie = cookieStore.getCookies().get(0);
        assertThat(cookie.getDomain(), is(equalTo(domain)));
        assertThat(cookie.getName(), is(equalTo(name)));
        assertThat(cookie.getValue(), is(equalTo(value)));
        assertThat(cookie.getExpiryInstant(), is(equalTo(expiryDate.toInstant())));
        assertThat(cookie.getAttribute(DOMAIN_ATTR), is(nullValue()));
        assertThat(cookie.getAttribute(PATH_ATTR), is(nullValue()));
    }

    @Test
    void shouldConvertHttpStateToCookieStoreRetainingDomainAttribute() {
        // Given
        HttpState httpState = new HttpState();
        String domain = "example.org";
        Cookie cookieLegacy = cookieLegacy(domain, "Name", "Value", null);
        cookieLegacy.setDomainAttributeSpecified(true);
        httpState.addCookie(cookieLegacy);
        // When
        CookieStore cookieStore = LegacyUtils.httpStateToCookieStore(httpState);
        // Then
        assertThat(cookieStore.getCookies(), hasSize(1));
        assertThat(cookieStore.getCookies().get(0).getAttribute(DOMAIN_ATTR), is(equalTo(domain)));
    }

    @Test
    void shouldConvertHttpStateToCookieStoreRetainingPathAttribute() {
        // Given
        HttpState httpState = new HttpState();
        String path = "/path";
        Cookie cookieLegacy = cookieLegacy("example.org", "Name", "Value", null);
        cookieLegacy.setPath(path);
        cookieLegacy.setPathAttributeSpecified(true);
        httpState.addCookie(cookieLegacy);
        // When
        CookieStore cookieStore = LegacyUtils.httpStateToCookieStore(httpState);
        // Then
        assertThat(cookieStore.getCookies(), hasSize(1));
        assertThat(cookieStore.getCookies().get(0).getAttribute(PATH_ATTR), is(equalTo(path)));
    }

    @Test
    void shouldConvertHttpStateToCookieStoreWithCookiesWithoutExpiryDate() {
        // Given
        HttpState httpState = new HttpState();
        Date expiryDate = null;
        httpState.addCookie(cookieLegacy("example.org", "Name", "Value", expiryDate));
        // When
        CookieStore cookieStore = LegacyUtils.httpStateToCookieStore(httpState);
        // Then
        assertThat(cookieStore.getCookies(), hasSize(1));
        org.apache.hc.client5.http.cookie.Cookie cookie = cookieStore.getCookies().get(0);
        assertThat(cookie.getExpiryInstant(), is(nullValue()));
    }

    @Test
    void shouldUpdateHttpStateWithCookieStore() {
        // Given
        BasicCookieStore cookieStore = new BasicCookieStore();
        String domain = "example.org";
        String name = "Name";
        String value = "Value";
        Instant expiryDate = Instant.now().plusSeconds(60).truncatedTo(ChronoUnit.MILLIS);
        cookieStore.addCookie(cookie(domain, name, value, expiryDate));
        HttpState httpState = new HttpState();
        // When
        LegacyUtils.updateHttpState(httpState, cookieStore);
        // Then
        assertThat(httpState.getCookies().length, is(equalTo(1)));
        Cookie cookie = httpState.getCookies()[0];
        assertThat(cookie.getDomain(), is(equalTo(domain)));
        assertThat(cookie.getName(), is(equalTo(name)));
        assertThat(cookie.getValue(), is(equalTo(value)));
        assertThat(cookie.getExpiryDate().toInstant(), is(expiryDate));
        assertThat(cookie.isDomainAttributeSpecified(), is(equalTo(false)));
        assertThat(cookie.isPathAttributeSpecified(), is(equalTo(false)));
    }

    @Test
    void shouldUpdateHttpStateWithCookieStoreRetainingDomainAttribute() {
        // Given
        BasicCookieStore cookieStore = new BasicCookieStore();
        String domain = "example.org";
        BasicClientCookie clientCookie = cookie(domain, "Name", "Value", null);
        clientCookie.setAttribute(DOMAIN_ATTR, domain);
        cookieStore.addCookie(clientCookie);
        HttpState httpState = new HttpState();
        // When
        LegacyUtils.updateHttpState(httpState, cookieStore);
        // Then
        assertThat(httpState.getCookies().length, is(equalTo(1)));
        assertThat(httpState.getCookies()[0].isDomainAttributeSpecified(), is(equalTo(true)));
    }

    @Test
    void shouldUpdateHttpStateWithCookieStoreRetainingPathAttribute() {
        // Given
        BasicCookieStore cookieStore = new BasicCookieStore();
        String path = "/path";
        BasicClientCookie clientCookie = cookie("example.org", "Name", "Value", null);
        clientCookie.setPath(path);
        clientCookie.setAttribute(PATH_ATTR, path);
        cookieStore.addCookie(clientCookie);
        HttpState httpState = new HttpState();
        // When
        LegacyUtils.updateHttpState(httpState, cookieStore);
        // Then
        assertThat(httpState.getCookies().length, is(equalTo(1)));
        assertThat(httpState.getCookies()[0].isPathAttributeSpecified(), is(equalTo(true)));
    }

    @Test
    void shouldUpdateHttpStateWithCookiesWithoutExpiryDate() {
        // Given
        BasicCookieStore cookieStore = new BasicCookieStore();
        Instant expiryDate = null;
        cookieStore.addCookie(cookie("example.org", "Name", "Value", expiryDate));
        HttpState httpState = new HttpState();
        // When
        LegacyUtils.updateHttpState(httpState, cookieStore);
        // Then
        assertThat(httpState.getCookies().length, is(equalTo(1)));
        Cookie cookie = httpState.getCookies()[0];
        assertThat(cookie.getExpiryDate(), is(nullValue()));
    }

    private static Cookie cookieLegacy(String domain, String name, String value, Date expiryDate) {
        Cookie cookie = new Cookie(domain, name, value);
        cookie.setExpiryDate(expiryDate);
        return cookie;
    }

    private static BasicClientCookie cookie(
            String domain, String name, String value, Instant expiryDate) {
        BasicClientCookie cookie = new BasicClientCookie(name, value);
        cookie.setDomain(domain);
        cookie.setExpiryDate(expiryDate);
        return cookie;
    }
}
