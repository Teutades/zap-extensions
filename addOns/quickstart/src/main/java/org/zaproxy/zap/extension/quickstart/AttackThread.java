/*
 * Zed Attack Proxy (ZAP) and its related class files.
 *
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 *
 * Copyright 2012 The ZAP Development Team
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
package org.zaproxy.zap.extension.quickstart;

import java.net.URL;
import javax.swing.SwingUtilities;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.control.Control;
import org.parosproxy.paros.extension.history.ExtensionHistory;
import org.parosproxy.paros.model.HistoryReference;
import org.parosproxy.paros.model.Model;
import org.parosproxy.paros.model.SiteNode;
import org.parosproxy.paros.network.HttpMessage;
import org.parosproxy.paros.network.HttpSender;
import org.parosproxy.paros.network.HttpStatusCode;
import org.zaproxy.addon.network.common.ZapUnknownHostException;
import org.zaproxy.zap.extension.ascan.ActiveScan;
import org.zaproxy.zap.extension.ascan.ExtensionActiveScan;
import org.zaproxy.zap.model.Target;

public class AttackThread extends Thread {

    public enum Progress {
        notstarted,
        started,
        spider,
        ajaxspider,
        ascan,
        failed,
        complete,
        stopped
    }

    private ExtensionQuickStart extension;
    private URL url;
    private HttpSender httpSender = null;
    private TraditionalSpider traditionalSpider;
    private PlugableSpider plugableSpider;
    private boolean stopAttack = false;
    private boolean useStdSpider;

    private static final Logger LOGGER = LogManager.getLogger(AttackThread.class);

    public AttackThread(ExtensionQuickStart ext, boolean useStdSpider) {
        super("ZAP-QuickStart-AttackThread");
        this.extension = ext;
        this.useStdSpider = useStdSpider;
    }

    public void setURL(URL url) {
        this.url = url;
    }

    public void setTraditionalSpider(TraditionalSpider traditionalSpider) {
        this.traditionalSpider = traditionalSpider;
    }

    public void setPlugableSpider(PlugableSpider plugableSpider) {
        this.plugableSpider = plugableSpider;
    }

    @Override
    public void run() {
        stopAttack = false;
        boolean completed = false;
        try {
            extension.notifyProgress(Progress.started);
            SiteNode startNode = this.accessNode(this.url);

            if (startNode == null) {
                LOGGER.debug("Failed to access URL {}", url);
                // Dont notify progress here - it will have been done where we know more about
                // the problem
                return;
            }
            if (stopAttack) {
                LOGGER.debug("Attack stopped manually");
                extension.notifyProgress(Progress.stopped);
                return;
            }
            Target target = new Target(startNode);
            target.setRecurse(true);
            if (this.useStdSpider) {

                if (traditionalSpider == null) {
                    LOGGER.error("No spider");
                    extension.notifyProgress(Progress.failed);
                    return;
                }

                extension.notifyProgress(Progress.spider);
                TraditionalSpider.Scan spiderScan =
                        traditionalSpider.startScan(target.getDisplayName(), target);

                // Give some time to the spider to finish to setup and start itself.
                sleep(1500);

                try {
                    // Wait for the spider to complete
                    while (!spiderScan.isStopped()) {
                        sleep(500);
                        if (this.stopAttack) {
                            spiderScan.stopScan();
                            break;
                        }
                        extension.notifyProgress(Progress.spider, spiderScan.getProgress());
                    }
                } catch (InterruptedException e) {
                    // Ignore
                }
                if (stopAttack) {
                    LOGGER.debug("Attack stopped manually");
                    extension.notifyProgress(Progress.stopped);
                    return;
                }

                // Pause after the spider seems to help
                sleep(2000);
            }

            if (stopAttack) {
                LOGGER.debug("Attack stopped manually");
                extension.notifyProgress(Progress.stopped);
                return;
            }

            // optionally invoke ajax spider here
            if (plugableSpider != null && plugableSpider.isSelected()) {
                plugableSpider.startScan(this.url.toURI());
                sleep(1500);

                try {
                    // Wait for the ajax spider to complete
                    while (plugableSpider.isRunning()) {
                        sleep(500);
                        if (this.stopAttack) {
                            plugableSpider.stopScan();
                            break;
                        }
                        extension.notifyProgress(Progress.ajaxspider);
                    }
                } catch (InterruptedException e) {
                    // Ignore
                }
            }

            if (startNode.isLeaf()
                    && !startNode.getParent().isRoot()
                    && !startNode.getParent().getParent().isRoot()) {
                // Start node is a leaf and isnt root or a top level app (eg
                // www.example.com/app1)
                // Go up a level
                startNode = startNode.getParent();
            }

            ExtensionActiveScan extAscan =
                    (ExtensionActiveScan)
                            Control.getSingleton()
                                    .getExtensionLoader()
                                    .getExtension(ExtensionActiveScan.NAME);
            int scanId;
            if (extAscan == null) {
                LOGGER.error("No active scanner");
                extension.notifyProgress(Progress.failed);
                return;
            } else {
                extension.notifyProgress(Progress.ascan);
                scanId = extAscan.startScan(target);
            }

            try {
                ActiveScan ascan = extAscan.getScan(scanId);
                // Wait for the active scanner to complete
                while (!ascan.isStopped()) {
                    sleep(500);
                    if (this.stopAttack) {
                        extAscan.stopScan(scanId);
                    }
                    extension.notifyProgress(Progress.ascan, ascan.getProgress());
                }
            } catch (InterruptedException e) {
                // Ignore
            }
            completed = true;

        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            extension.notifyProgress(
                    Progress.failed,
                    Constant.messages.getString(
                            "quickstart.progress.failed.reason", e.getMessage()));
        } finally {
            if (!completed) {
                // Already handled
            } else if (stopAttack) {
                LOGGER.debug("Attack stopped manually");
                extension.notifyProgress(Progress.stopped);
            } else {
                LOGGER.debug("Attack completed");
                extension.notifyProgress(Progress.complete);
            }
        }
    }

    private SiteNode accessNode(URL url) {
        SiteNode startNode = null;
        // Request the URL
        try {
            final HttpMessage msg = new HttpMessage(new URI(url.toString(), true));
            getHttpSender().sendAndReceive(msg, true);

            if (!HttpStatusCode.isSuccess(msg.getResponseHeader().getStatusCode())) {
                extension.notifyProgress(
                        Progress.failed,
                        Constant.messages.getString(
                                "quickstart.progress.failed.code",
                                msg.getResponseHeader().getStatusCode()));

                return null;
            }

            if (msg.getResponseHeader().isEmpty()) {
                extension.notifyProgress(Progress.failed);
                return null;
            }

            ExtensionHistory extHistory =
                    ((ExtensionHistory)
                            Control.getSingleton()
                                    .getExtensionLoader()
                                    .getExtension(ExtensionHistory.NAME));
            extHistory.addHistory(msg, HistoryReference.TYPE_PROXIED);

            SwingUtilities.invokeAndWait(
                    () ->
                            // Needs to be done on the EDT
                            Model.getSingleton()
                                    .getSession()
                                    .getSiteTree()
                                    .addPath(msg.getHistoryRef()));

            String urlStr = url.toString();
            if (urlStr.endsWith("/")) {
                // The sites tree treats URLs ending in a slash as leaf nodes
                urlStr = urlStr.substring(0, urlStr.length() - 1);
            }
            URI uri = new URI(urlStr, false);

            for (int i = 0; i < 10; i++) {
                startNode = Model.getSingleton().getSession().getSiteTree().findNode(uri);
                if (startNode != null) {
                    break;
                }
                try {
                    sleep(200);
                } catch (InterruptedException e) {
                    // Ignore
                }
            }
        } catch (ZapUnknownHostException e1) {
            if (e1.isFromOutgoingProxy()) {
                extension.notifyProgress(
                        Progress.failed,
                        Constant.messages.getString(
                                "quickstart.progress.failed.badhost.proxychain", e1.getMessage()));
            } else {
                extension.notifyProgress(
                        Progress.failed,
                        Constant.messages.getString(
                                "quickstart.progress.failed.badhost", e1.getMessage()));
            }
        } catch (URIException e) {
            extension.notifyProgress(
                    Progress.failed,
                    Constant.messages.getString(
                            "quickstart.progress.failed.reason", e.getMessage()));
        } catch (Exception e1) {
            LOGGER.error(e1.getMessage(), e1);
            extension.notifyProgress(
                    Progress.failed,
                    Constant.messages.getString(
                            "quickstart.progress.failed.reason", e1.getMessage()));
            return null;
        }
        return startNode;
    }

    private HttpSender getHttpSender() {
        if (httpSender == null) {
            httpSender = new HttpSender(HttpSender.MANUAL_REQUEST_INITIATOR);
        }
        return httpSender;
    }

    public void stopAttack() {
        this.stopAttack = true;
    }
}
