/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2013 Servoy BV

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU Affero General Public License as published by the Free
 Software Foundation; either version 3 of the License, or (at your option) any
 later version.

 This program is distributed in the hope that it will be useful, but WITHOUT
 ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License along
 with this program; if not, see http://www.gnu.org/licenses or write to the Free
 Software Foundation,Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301
 */

package com.servoy.eclipse.jsunit.mobile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.servoy.base.test.IJSUnitSuiteHandler;
import com.servoy.eclipse.jsunit.runner.CodeFinderUtils;
import com.servoy.eclipse.jsunit.runner.JSUnitTestListenerHandler;
import com.servoy.eclipse.jsunit.runner.JSUnitToJavaRunner;
import com.servoy.eclipse.jsunit.runner.TestTreeHandler;
import com.servoy.j2db.util.StaticSingletonMap;

/**
 * A simulated JUnit test suite helper that is driven by something else behind the scenes. (eg. a jsUnit mobile suite running in a browser)
 *
 * @author acostescu
 */
public class SuiteBridge implements IJSUnitSuiteHandler
{

	public final static int DEFAULT_TEST_TREE_WAIT_TIMEOUT = 30 * 1000;
	private final static int DEFAULT_STOP_REQUESTED_WAIT = 25 * 1000;

	private static int idCount = 1;
	private static final Logger log = LoggerFactory.getLogger(JSUnitToJavaRunner.class);

	private final int id;

	private final Object testTreeLock = new Object();
	private String[] testTree = null;
	private int testTreeWaitTimeout = DEFAULT_TEST_TREE_WAIT_TIMEOUT;
	private int stopRequestedWait = DEFAULT_STOP_REQUESTED_WAIT;
	private List<Test> testList;
	private JSUnitTestListenerHandler<String, Throwable> junitHandler;

	private final Object runLock = new Object();
	String unexpectedRunProblemMessage = null;
	private Throwable unexpectedRunThrowable = null;
	private boolean doneTesting = false;
	private TestCycleListener testCycleListener;

	private String[] credentials = null;

	public static SuiteBridge prepareNewInstance(int clientConnectTimeout, String userName, String password)
	{
		SuiteBridge bridge = new SuiteBridge(clientConnectTimeout, -1);

		bridge.setCredentials(userName, password);

		// perform the automated mobile export and start app. using [serverUrl]/MobileTestClient/servoy_mobile_test.html?noinitsmc=true&bid=[bridgeObjId]
		// as start URL; deploy that .war just like it's done in the .war exporter to Servoy Developer Tomcat
		// the service solution URL should use &nodebug = true when ran from developer
		Map<String, Object> sharedMap = StaticSingletonMap.instance();
		synchronized (sharedMap)
		{
			sharedMap.put(IJSUnitSuiteHandler.SERVOY_BRIDGE_KEY, bridge);
		}

		return bridge;
	}

	protected SuiteBridge()
	{
		this.id = idCount++;
//		this.id = 0; // use this one instead for debugging test client
	}

	/**
	 * @param treeWaitTimeout in seconds; the time to wait for mobile client to connect with tests. After it expires tests will fail and stop execution. -1 will fall back to default value.
	 * @param stopRequestedWait in seconds; the time to wait for client tests to finish nicely when the user presses the "stop" buton on tests; after it expires the tests will be terminated anyway. -1 will fallback to default value.
	 */
	protected SuiteBridge(int treeWaitTimeout, int stopRequestedWait)
	{
		this();
		this.testTreeWaitTimeout = treeWaitTimeout > 0 ? treeWaitTimeout * 1000 : DEFAULT_TEST_TREE_WAIT_TIMEOUT;
//		this.testTreeWaitTimeout = 600000; // use this one instead for debugging test client
		this.stopRequestedWait = stopRequestedWait > 0 ? stopRequestedWait * 1000 : DEFAULT_STOP_REQUESTED_WAIT;
	}

	public void setCredentials(String userName, String password)
	{
		if (userName != null && password != null)
		{
			credentials = new String[] { userName, password };
		}
	}

	/**
	 * This id identifies the JUnit run session. It helps to make sure the right bridge instance is used in case of multiple executions...
	 */
	public int getId()
	{
		return id;
	}

	@Override
	public String[] getCredentials()
	{
		return credentials;
	}

	public String[] getJsUnitJavascriptCode()
	{
		log.trace("[.......] Getting javascript library code");
		String[] libs = new String[3];
		libs[0] = CodeFinderUtils.getFixedJSUtilScriptFromResource();
		libs[1] = CodeFinderUtils.getFixedJSUnitScriptFromResource();
		libs[2] = CodeFinderUtils.getScriptAsStringFromResource("this.JsUnitToJavaLoaded", JSUnitToJavaRunner.class, "JsUnitToJava.js");
		return libs;
	}

	/**
	 * Waits for the client to transmit the flattened test tree then reconstructs it as a JUnit test suite hierarchy.
	 * @param testSuite the root test-suite to use.
	 * @param staticLaunchMonitor launch monitor that can be used to check for stop requests from the user.
	 */
	public void createTestTree(TestSuite testSuite, ICancelMonitor staticLaunchMonitor)
	{
		synchronized (testTreeLock)
		{
			long ct = System.currentTimeMillis();
			while (testTree == null && (System.currentTimeMillis() - ct) < testTreeWaitTimeout && !unexpectedProblemOccurred())
			{
				try
				{
					testTreeLock.wait(1000);
					if (staticLaunchMonitor != null && staticLaunchMonitor.isCanceled())
					{
						// if the user is in a hurry or not going to wait for "testTreeWaitTimeout" when something went wrong
						testSuite.setName("Stop requested");
						unexpectedRunProblemMessage = "Stop requested after " +
							((System.currentTimeMillis() - ct) / 1000 + " sec of waiting for the mobile test client to connect... ");
						break;
					}
				}
				catch (InterruptedException e)
				{
					log.error("", e);
				}
			}
			if (testTree == null && !unexpectedProblemOccurred())
			{
				testSuite.setName("Connection problem");
				unexpectedRunProblemMessage = "Timed out - " + ((System.currentTimeMillis() - ct) / 1000 + " sec - waiting for mobile client to connect... ");
			}
			if (unexpectedProblemOccurred())
			{
				if (testSuite.getName() == null) testSuite.setName("Test session failed");
				testSuite.addTest(new TestCase(getUnexpectedProblemDescription())
				{
				});
			}
		}
		testList = new ArrayList<Test>();
		testList.add(testSuite);

		TestTreeHandler tth = new TestTreeHandler(testTree, testSuite);
		if (testTree != null) tth.createDummyTestTree();
		tth.fillTestListSequencialOrder(testList);
	}

	private String getUnexpectedProblemDescription()
	{
		return unexpectedRunProblemMessage != null ? unexpectedRunProblemMessage : (unexpectedRunThrowable != null ? unexpectedRunThrowable.getMessage() : "");
	}

	/**
	 * Uses the given result object to "run" the dummy JUnit test suite controlled remotely by the bridge. It will return only after remote tests are over (either passed, failure or error).
	 */
	public void runClientTests(TestResult result)
	{
		junitHandler = new JSUnitMobileTestListenerHandler(result, testList);

		if (testCycleListener != null) testCycleListener.started();
		else log.info("When starting test run, runStartListener is null (in bridge).");

		synchronized (runLock)
		{
			if (unexpectedProblemOccurred())
			{
				// if it has already errored out, we need to fake some starts to simulate the dummy testcase start
				junitHandler.startTest(((TestSuite)testList.get(0)).getName());
				junitHandler.startTest(getUnexpectedProblemDescription());
			}
			else
			{
				while (!doneTesting && !unexpectedProblemOccurred())
				{
					try
					{
						runLock.wait(1000);
						if (junitHandler.shouldStop())
						{
							// normally when this happens a "done" should be generated clientside;
							// but if something bad happened and the client is no longer available, just end it after a reasonable amount of time
							runLock.wait(stopRequestedWait);
							if (!doneTesting && !unexpectedProblemOccurred())
							{
								log.warn("Stop requested; Shutting down server side because client side didn't report as stopped in under " +
									(stopRequestedWait / 1000) + " seconds.");
								break; // end anyway
							}
						}
					}
					catch (InterruptedException e)
					{
						log.error("", e);
					}
				}
			}
		}
		showUnexpectedThrowableIfNeeded();
		if (testCycleListener != null) testCycleListener.finished();
		log.trace("Test session finished.");
	}

	private void showUnexpectedThrowableIfNeeded()
	{
		if (unexpectedProblemOccurred())
		{
			if (unexpectedRunThrowable == null)
			{
				// if the message is large enough it gets truncated when used as a test name...
				unexpectedRunThrowable = new Exception(getUnexpectedProblemDescription());
				unexpectedRunThrowable.setStackTrace(new StackTraceElement[0]);
			}
			junitHandler.addError(getUnexpectedProblemDescription(), unexpectedRunThrowable);
		}
	}

	private boolean unexpectedProblemOccurred()
	{
		return (unexpectedRunProblemMessage != null) || (unexpectedRunThrowable != null);
	}

	@Override
	public void setFlattenedTestTree(String[] testTree)
	{
		log.trace("[.......] setFlattenedTestTree - " + Arrays.asList(testTree).toString());
		synchronized (testTreeLock)
		{
			this.testTree = testTree;
			testTreeLock.notifyAll();
		}
	}

	public void addError(final String testName, final Throwable throwable)
	{
		log.trace("[.......] addError - " + testName + "; throwable: " + throwable);
		junitHandler.addError(testName, throwable);
	}

	public void addFailure(final String testName, final Throwable throwable)
	{
		log.trace("[.......] addFailure - " + testName + "; throwable: " + throwable);
		junitHandler.addFailure(testName, throwable);
	}

	public void startTest(final String testName)
	{
		log.trace("[.......] startTest - " + testName);
		junitHandler.startTest(testName);
	}

	public void endTest(final String testName)
	{
		log.trace("[.......] endTest - " + testName);
		junitHandler.endTest(testName);
	}

	public boolean isStopped()
	{
		return junitHandler != null ? junitHandler.shouldStop() : false;
	}

	@Override
	public void doneTesting()
	{
		log.trace("[.......] DONE TESTING.");
		synchronized (runLock)
		{
			doneTesting = true;
			runLock.notifyAll();
		}
	}

	public void reportUnexpectedThrowable(String msg, Throwable t)
	{
		log.error("[.......] unexpected throwable: ", t);

		synchronized (runLock)
		{
			synchronized (testTreeLock)
			{
				unexpectedRunProblemMessage = msg;
				unexpectedRunThrowable = t;
				runLock.notifyAll();
				testTreeLock.notifyAll();
			}
		}
	}

	@Override
	public void registerRunStartListener(TestCycleListener l)
	{
		this.testCycleListener = l;
	}

	public List<Test> getTestList()
	{
		return testList;
	}

	public static interface ICancelMonitor
	{

		boolean isCanceled();

	}

}
