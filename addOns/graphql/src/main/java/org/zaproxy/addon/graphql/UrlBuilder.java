/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2020 The ZAP Development Team
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
package org.zaproxy.addon.graphql;

import java.net.URL;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.parosproxy.paros.network.HttpHeader;

public final class UrlBuilder {

    private UrlBuilder() {}

    public static URI build(String urlStr) throws URIException {
        if (urlStr.isEmpty()) {
            throw new URIException("URL cannot be empty.");
        } else if (HttpHeader.SCHEME_HTTP.equals(urlStr)
                || HttpHeader.SCHEME_HTTPS.equals(urlStr)) {
            throw new URIException("URL is incomplete.");
        }
        try {
            new URL(urlStr).toURI();
            return new URI(urlStr, true);
        } catch (Exception e) {
            throw new URIException(e.getMessage());
        }
    }

    public static boolean isValidUrl(String urlStr) {
        try {
            build(urlStr);
            return true;
        } catch (URIException e) {
            return false;
        }
    }
}
