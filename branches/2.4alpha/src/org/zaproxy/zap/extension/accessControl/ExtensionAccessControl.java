/*
 * Zed Attack Proxy (ZAP) and its related class files.
 * 
 * ZAP is an HTTP/HTTPS proxy for assessing web application security.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0 
 *   
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License. 
 */
package org.zaproxy.zap.extension.accessControl;

import java.awt.Dimension;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.Logger;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.control.Control.Mode;
import org.parosproxy.paros.extension.ExtensionAdaptor;
import org.parosproxy.paros.extension.ExtensionHook;
import org.parosproxy.paros.extension.ExtensionHookView;
import org.parosproxy.paros.extension.SessionChangedListener;
import org.parosproxy.paros.model.Session;
import org.parosproxy.paros.view.View;
import org.zaproxy.zap.extension.accessControl.AccessControlScannerThread.AccessControlScanStartOptions;
import org.zaproxy.zap.extension.authentication.ExtensionAuthentication;
import org.zaproxy.zap.extension.authorization.ExtensionAuthorization;
import org.zaproxy.zap.extension.users.ExtensionUserManagement;
import org.zaproxy.zap.model.Context;
import org.zaproxy.zap.scan.BaseScannerThreadManager;

/**
 * An extension that adds a set of tools that allows users to test Access Control issues in web
 * application.
 */
public class ExtensionAccessControl extends ExtensionAdaptor implements SessionChangedListener {

	private static Logger log = Logger.getLogger(ExtensionAccessControl.class);

	/** The NAME of the extension. */
	public static final String NAME = "ExtensionAccessControl";

	/** The Constant EXTENSION DEPENDENCIES. */
	private static final List<Class<?>> EXTENSION_DEPENDENCIES;
	static {
		// Prepare a list of Extensions on which this extension depends
		List<Class<?>> dependencies = new ArrayList<>(1);
		dependencies.add(ExtensionUserManagement.class);
		dependencies.add(ExtensionAuthentication.class);
		dependencies.add(ExtensionAuthorization.class);
		EXTENSION_DEPENDENCIES = Collections.unmodifiableList(dependencies);
	}

	private AccessControlStatusPanel statusPanel;
	private AccessControlScannerThreadManager threadManager;

	private AccessControlScanOptionsDialog customScanDialog;

	public ExtensionAccessControl() {
		super(NAME);
		this.setOrder(601);
		this.threadManager = new AccessControlScannerThreadManager();
	}

	@Override
	public void hook(ExtensionHook extensionHook) {
		super.hook(extensionHook);
		// Register this where needed
		// Model.getSingleton().addContextDataFactory(this);
		extensionHook.addSessionListener(this);
		if (getView() != null) {
			ExtensionHookView viewHook = extensionHook.getHookView();
			// getView().addContextPanelFactory(this);
			viewHook.addStatusPanel(getStatusPanel());
		}
	}

	private AccessControlStatusPanel getStatusPanel() {
		if (statusPanel == null)
			statusPanel = new AccessControlStatusPanel(this, threadManager);
		return statusPanel;
	}

	public void showScanOptionsDialog(Context context) {
		if (customScanDialog == null) {
			customScanDialog = new AccessControlScanOptionsDialog(this, View.getSingleton().getMainFrame(),
					new Dimension(700, 500));
		}
		if (customScanDialog.isVisible()) {
			return;
		}
		customScanDialog.init(context);
		customScanDialog.setVisible(true);
	}

	public void startScan(AccessControlScanStartOptions startOptions) {
		int contextId = startOptions.targetContext.getIndex();
		AccessControlScannerThread scannerThread = threadManager.getScannerThread(contextId);
		if (scannerThread.isRunning()) {
			log.warn("Access control scan already running for context: " + contextId);
			throw new IllegalStateException("A scan is already running for context: " + contextId);
		}

		scannerThread = threadManager.recreateScannerThreadIfHasRun(contextId);
		if (getView() != null)
			scannerThread.addScanListener(getStatusPanel());
		scannerThread.setStartOptions(startOptions);
		scannerThread.start();
	}

	@Override
	public boolean canUnload() {
		return true;
	}

	@Override
	public String getAuthor() {
		return Constant.ZAP_TEAM;
	}

	@Override
	public String getDescription() {
		return Constant.messages.getString("accessControl.desc");
	}

	@Override
	public URL getURL() {
		try {
			return new URL(Constant.ZAP_EXTENSIONS_PAGE);
		} catch (MalformedURLException e) {
			return null;
		}
	}

	@Override
	public List<Class<?>> getDependencies() {
		return EXTENSION_DEPENDENCIES;
	}

	private static class AccessControlScannerThreadManager extends
			BaseScannerThreadManager<AccessControlScannerThread> {

		@Override
		public AccessControlScannerThread createNewScannerThread(int contextId) {
			return new AccessControlScannerThread(contextId);
		}
	}

	@Override
	public void sessionChanged(Session session) {
		if (getView() != null) {
			getStatusPanel().contextsChanged();
			getStatusPanel().reset();
		}
	}

	@Override
	public void sessionAboutToChange(Session session) {
		// TODO Auto-generated method stub

	}

	@Override
	public void sessionScopeChanged(Session session) {
		// TODO Auto-generated method stub

	}

	@Override
	public void sessionModeChanged(Mode mode) {
		// TODO Auto-generated method stub

	}

}