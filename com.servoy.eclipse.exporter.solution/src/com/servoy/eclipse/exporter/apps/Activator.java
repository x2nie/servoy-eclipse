/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2011 Servoy BV

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

package com.servoy.eclipse.exporter.apps;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.Plugin;
import org.osgi.framework.BundleContext;

import com.servoy.eclipse.exporter.apps.common.IBundleStopListener;
import com.servoy.j2db.server.ngclient.property.types.Types;

/**
 * The activator class controls the plug-in life cycle
 */
public class Activator extends Plugin
{

	// The plug-in ID
	public static final String PLUGIN_ID = "com.servoy.eclipse.client";

	// The shared instance
	private static Activator plugin;

	private List<IBundleStopListener> stopListeners = new ArrayList<IBundleStopListener>();

	/**
	 * The constructor
	 */
	public Activator()
	{
	}

	@Override
	public void start(BundleContext context) throws Exception
	{
		super.start(context);
		plugin = this;
		Types.getTypesInstance().registerTypes();
	}

	@Override
	public void stop(BundleContext context) throws Exception
	{
		for (IBundleStopListener l : stopListeners)
		{
			l.bundleStopping(context);
		}
		stopListeners.clear();
		stopListeners = null;
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static Activator getDefault()
	{
		return plugin;
	}

	public void addBundleStopListener(IBundleStopListener listener)
	{
		if (!stopListeners.contains(listener)) stopListeners.add(listener);
	}

}
