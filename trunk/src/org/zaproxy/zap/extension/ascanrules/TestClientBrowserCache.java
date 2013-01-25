/*
 *
 * Paros and its related class files.
 * 
 * Paros is an HTTP/HTTPS proxy for assessing web application security.
 * Copyright (C) 2003-2004 Chinotec Technologies Company
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Clarified Artistic License
 * as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Clarified Artistic License for more details.
 * 
 * You should have received a copy of the Clarified Artistic License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
// ZAP: 2012/01/02 Separate param and attack
// ZAP: 2012/04/25 Added @Override annotation to all appropriate methods.
// ZAP: 2013/01/25 Removed the "(non-Javadoc)" comments.

package org.zaproxy.zap.extension.ascanrules;

import java.util.regex.Pattern;

import org.parosproxy.paros.core.scanner.AbstractAppPlugin;
import org.parosproxy.paros.core.scanner.Alert;
import org.parosproxy.paros.core.scanner.Category;
import org.parosproxy.paros.network.HttpHeader;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.network.HttpStatusCode;



/**
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class TestClientBrowserCache extends AbstractAppPlugin {

    
    public final static Pattern patternNoCache	= Pattern.compile("\\QNo-cache\\E|\\QNo-store\\E", PATTERN_PARAM);

	// <meta http-equiv="Pragma" content="no-cache">
	// <meta http-equiv="Cache-Control" content="no-cache">
	public final static Pattern patternHtmlNoCache = Pattern.compile("<META[^>]+(Pragma|\\QCache-Control\\E)[^>]+(\\QNo-cache\\E|\\QNo-store\\E)[^>]*>", PATTERN_PARAM);

    @Override
    public int getId() {
        return 10001;
    }

    @Override
    public String getName() {
        return "Secure page browser cache";
    }

    @Override
    public String[] getDependency() {
        return null;
    }

    @Override
    public String getDescription() {
        return "Secure page can be cached in browser.  Cache control is not set in HTTP header nor HTML header.  Sensitive content can be recovered from browser storage.";
    }

    @Override
    public int getCategory() {
        return Category.BROWSER;
    }

    @Override
    public String getSolution() {
        String msg = "The best way is to set HTTP header with: 'Pragma: No-cache' and 'Cache-control: No-cache'." + CRLF
            + "Alternatively, this can be set in the HTML header by:" + CRLF
            + "<META HTTP-EQUIV='Pragma' CONTENT='no-cache'>" + CRLF
            + "<META HTTP-EQUIV='Cache-Control' CONTENT='no-cache'>" + CRLF
            + "but some browsers may have problem using this method.";
        return msg;
    }

    @Override
    public String getReference() {
        String msg = ". How to prevent caching in Internet Explorer - http://support.microsoft.com/default.aspx?kbid=234067" + CRLF
            + ". Pragma: No-cache Tag May Not Prevent Page from Being Cached - http://support.microsoft.com/default.aspx?kbid=222064";
        return msg;
    }

    @Override
    public void init() {

    }

    @Override
    public void scan() {

        HttpMessage msg = getBaseMsg();
		boolean result = false;
		
		if (!msg.getRequestHeader().getSecure()) {
		    // no need to if non-secure page;
		    return;
		} else if (msg.getRequestHeader().isImage()) {
		    // does not bother if image is cached
		    return;
		} else if (msg.getResponseBody().length() == 0) {
		    return;
		} else if (HttpStatusCode.isClientError(msg.getResponseHeader().getStatusCode())) {
			// These typically dont return 'real' data, so can be ignored
			return;
		}
		
		if (!matchHeaderPattern(msg, HttpHeader.CACHE_CONTROL, patternNoCache)
		        && !matchHeaderPattern(msg, HttpHeader.PRAGMA, patternNoCache)
		        && !matchBodyPattern(msg, patternHtmlNoCache, null)) {
		    
		    result = true;
		}
		
		if (result) {
		    bingo(Alert.RISK_MEDIUM, Alert.WARNING, null, null, "", "", msg);
		}

    }

	@Override
	public int getRisk() {
		return Alert.RISK_MEDIUM;
	}

}
