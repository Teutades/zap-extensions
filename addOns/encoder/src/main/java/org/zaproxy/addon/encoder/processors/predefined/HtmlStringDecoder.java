/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2018 The ZAP Development Team
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
package org.zaproxy.addon.encoder.processors.predefined;

import org.apache.commons.text.StringEscapeUtils;

public class HtmlStringDecoder extends DefaultEncodeDecodeProcessor {

    private static final HtmlStringDecoder INSTANCE = new HtmlStringDecoder();

    @Override
    protected String processInternal(String value) {
        return StringEscapeUtils.unescapeHtml4(value);
    }

    public static HtmlStringDecoder getSingleton() {
        return INSTANCE;
    }
}
