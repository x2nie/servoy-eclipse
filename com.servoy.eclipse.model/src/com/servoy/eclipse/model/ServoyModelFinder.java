/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2010 Servoy BV

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

package com.servoy.eclipse.model;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;

import com.servoy.eclipse.model.extensions.IServoyEnvironmentProvider;
import com.servoy.eclipse.model.extensions.IServoyModel;
import com.servoy.eclipse.model.util.ServoyLog;
import com.servoy.j2db.IServiceProvider;

/**
 * Class that gives access to a IServoyModel instance by searching the platform for a registered IServoyEnvironmentProvider extension.
 * @author acostescu
 */
public class ServoyModelFinder
{

	private static IServoyEnvironmentProvider modelProvider = null;

	public static IServoyModel getServoyModel()
	{
		initializeServoyModelProvider(null);
		return (modelProvider != null) ? modelProvider.getServoyModel() : null;
	}

	public static IServiceProvider getServiceProvider()
	{
		initializeServoyModelProvider(null);
		return modelProvider == null ? null : modelProvider.getServiceProvider();
	}

	public static IServoyEnvironmentProvider getServoyModelProvider()
	{
		if (modelProvider == null) initializeServoyModelProvider(null);
		return modelProvider;
	}

	public static void initializeServoyModelProvider(String instance)
	{
		// if the instance name is given, make sure that that one is created.
		if (modelProvider == null || instance != null)
		{
			IExtensionRegistry reg = Platform.getExtensionRegistry();
			IExtensionPoint ep = reg.getExtensionPoint(IServoyEnvironmentProvider.EXTENSION_ID);
			IExtension[] extensions = ep.getExtensions();

			if (extensions == null || extensions.length == 0)
			{
				ServoyLog.logError("Could not find servoy model provider extension (extension point " + IServoyEnvironmentProvider.EXTENSION_ID + ")", null);
			}
			else
			{
				IExtension extension = extensions[0];
				if (extensions.length > 1)
				{
					for (IExtension ext : extensions)
					{
						String instanceString = ext.getConfigurationElements()[0].getAttribute("instance");
						if ((instanceString == null && instance == null) || instanceString != null && instance != null && instance.equals(instanceString))
						{
							extension = ext;
							break;
						}
					}
				}

				IConfigurationElement[] ce = extension.getConfigurationElements();
				if (ce == null || ce.length == 0)
				{
					ServoyLog.logError(
						"Could not read servoy model provider extension element (extension point " + IServoyEnvironmentProvider.EXTENSION_ID + ")", null);
				}
				else
				{
					if (ce.length > 1)
					{
						ServoyLog.logError(
							"Multiple servoy model provider extension elements found (extension point " + IServoyEnvironmentProvider.EXTENSION_ID + ")", null);
					}
					try
					{
						modelProvider = (IServoyEnvironmentProvider)ce[0].createExecutableExtension("class");
						if (modelProvider == null)
						{
							ServoyLog.logError("Could not load servoy model provider (extension point " + IServoyEnvironmentProvider.EXTENSION_ID + ")", null);
						}
					}
					catch (CoreException e)
					{
						ServoyLog.logError("Could not load servoy model provider (extension point " + IServoyEnvironmentProvider.EXTENSION_ID + ")", e);
					}
				}
			}
		}
	}

}
