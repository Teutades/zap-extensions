/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2019 The ZAP Development Team
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
package org.zaproxy.addon.commonlib.http.domains;

import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RegexTrust implements Trust {
    private static final Logger LOGGER = LogManager.getLogger(RegexTrust.class);

    static final Pattern SIMPLE_URL_REGEX = Pattern.compile("https?://");

    private final Pattern regex;

    public RegexTrust(String regex) {
        if (SIMPLE_URL_REGEX.matcher(regex).find()) {
            LOGGER.warn(
                    "Trusted Domains regex seems to contain a URL pattern not just a domain pattern: {}",
                    regex);
        }

        this.regex = Pattern.compile(regex);
    }

    @Override
    public boolean isTrusted(String url) {
        return regex.matcher(url).matches();
    }
}
