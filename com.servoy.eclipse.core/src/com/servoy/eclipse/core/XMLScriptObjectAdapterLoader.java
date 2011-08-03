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
package com.servoy.eclipse.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.servoy.eclipse.core.doc.IDocumentationManagerProvider;
import com.servoy.eclipse.model.util.ServoyLog;
import com.servoy.j2db.IBeanManagerInternal;
import com.servoy.j2db.documentation.DocumentationUtil;
import com.servoy.j2db.documentation.IDocumentationManager;
import com.servoy.j2db.documentation.IObjectDocumentation;
import com.servoy.j2db.documentation.XMLScriptObjectAdapter;
import com.servoy.j2db.scripting.IScriptObject;
import com.servoy.j2db.scripting.ScriptObjectRegistry;
import com.servoy.j2db.smart.plugins.PluginManager;
import com.servoy.j2db.util.JarManager;
import com.servoy.j2db.util.JarManager.Extension;

@SuppressWarnings("nls")
public class XMLScriptObjectAdapterLoader
{
	private static IDocumentationManager docManager;
	private static boolean coreLoaded = false;

	public static void loadCoreDocumentationFromXML()
	{
		if (!coreLoaded)
		{
			URL url = XMLScriptObjectAdapterLoader.class.getResource("doc/servoydoc.xml"); //$NON-NLS-1$
			IDocumentationManagerProvider documentationManagerProvider = Activator.getDefault().getDocumentationManagerProvider();
			if (documentationManagerProvider != null)
			{
				docManager = documentationManagerProvider.fromXML(url, null);
			}
			loadDocumentationFromXML(null, docManager);
			coreLoaded = true;
		}
	}

	public static void loadDocumentationFromXML(ClassLoader loader, IDocumentationManager docmgr)
	{
		Date start = new Date();

		if (docmgr != null)
		{
			int succeeded = 0;
			int failed = 0;
			for (IObjectDocumentation objDoc : docmgr.getObjects().values())
			{
				try
				{
					Class< ? > clazz = DocumentationUtil.loadClass(loader, objDoc.getQualifiedName());
					// see if there are already return types that the class itself specifies (to be exactly the same as a real client)
					IScriptObject scriptObjectForClass = ScriptObjectRegistry.getScriptObjectForClass(clazz);
					XMLScriptObjectAdapter adapter = new XMLScriptObjectAdapter(objDoc, scriptObjectForClass);
					if (scriptObjectForClass != null)
					{
						Class< ? >[] allReturnedTypes = scriptObjectForClass.getAllReturnedTypes();
						if (allReturnedTypes != null && allReturnedTypes.length > 0)
						{
							// if there are return types already set those.
							adapter.setReturnTypes(allReturnedTypes);
						}
					}
					ScriptObjectRegistry.registerScriptObjectForClass(clazz, adapter);
					succeeded++;
				}
				catch (ClassNotFoundException e)
				{
					System.out.println("Class " + objDoc.getQualifiedName() + " not found for script object registration.");
					failed++;
				}
			}

			System.out.print("Documentation loaded successfully. " + succeeded + " classes registered successfully.");
			if (failed > 0) System.out.print(" " + failed + " classes failed registration.");
			System.out.println();
		}

		Date stop = new Date();
		System.out.println("Documentation loaded and registered in " + (stop.getTime() - start.getTime()) + " ms.");
	}

	public static IObjectDocumentation getObjectDocumentation(Class< ? > clz)
	{
		if (docManager != null) return docManager.getObjectByQualifiedName(clz.getCanonicalName());
		return null;
	}

	@SuppressWarnings("nls")
	public static String getPluginDocXMLForClass(String clz)
	{
		String clzCanonical = clz.replace(".", "/");
		int idx = clzCanonical.lastIndexOf("/");
		String docFileName = "servoy-doc.xml";
		if (idx >= 0)
		{
			return clzCanonical.substring(0, idx + 1) + docFileName;
		}
		else
		{
			return docFileName;
		}
	}

	/**
	 * Tries to load documentation XMLs for available client plugins.
	 */
	public static void loadDocumentationForPlugins(PluginManager pluginManager, IDocumentationManagerProvider documentationManagerProvider)
	{
		for (Extension ext : pluginManager.loadClientPluginDefs())
		{
			URL url = ext.jarUrl;
			try
			{
				File urlFile = new File(new URI(url.toExternalForm()));
				ZipFile zf = new ZipFile(urlFile);
				String docXMLPath = XMLScriptObjectAdapterLoader.getPluginDocXMLForClass(ext.instanceClass.getCanonicalName());
				ZipEntry docEntry = zf.getEntry(docXMLPath);
				if (docEntry != null)
				{
					InputStream is = zf.getInputStream(docEntry);
					IDocumentationManager mgr = documentationManagerProvider.fromXML(is, pluginManager.getClassLoader());
					XMLScriptObjectAdapterLoader.loadDocumentationFromXML(pluginManager.getClassLoader(), mgr);
				}
			}
			catch (Exception e)
			{
				ServoyLog.logError("Exception while loading extension XML file from JAR file '" + url.toExternalForm() + "'.", e);
			}
		}
	}

	/**
	 * Tries to load documentation XMLs for available beans.
	 */
	public static void loadDocumentationForBeans(IBeanManagerInternal beanManager, IDocumentationManagerProvider documentationManagerProvider)
	{
		File beanDir = beanManager.getBeanDir();
		Map<String, Object> beans = beanManager.getLoadedBeanDefs();
		List<File> allJars = new ArrayList<File>();
		for (String beanName : beans.keySet())
		{
			Object entry = beans.get(beanName);
			if (entry instanceof String)
			{
				String jarName = (String)entry;
				allJars.add(new File(beanDir, jarName));
			}
			else if (entry instanceof List)
			{
				@SuppressWarnings("unchecked")
				List<String> jarNames = (List<String>)entry;
				for (String jarName : jarNames)
				{
					allJars.add(new File(beanDir, jarName));
				}
			}
		}
		for (File jarPath : allJars)
		{
			try
			{
				JarFile file = new JarFile(jarPath);
				Manifest mf = file.getManifest();
				if (mf != null)
				{
					List<String> beanClasses = JarManager.getBeanClassNames(mf);
					Set<String> docXMLs = new TreeSet<String>();
					for (String clz : beanClasses)
					{
						docXMLs.add(XMLScriptObjectAdapterLoader.getPluginDocXMLForClass(clz));
					}
					for (String docXMLPath : docXMLs)
					{
						ZipEntry docEntry = file.getEntry(docXMLPath);
						if (docEntry != null)
						{
							InputStream is = file.getInputStream(docEntry);
							IDocumentationManager mgr = documentationManagerProvider.fromXML(is, beanManager.getClassLoader());
							XMLScriptObjectAdapterLoader.loadDocumentationFromXML(beanManager.getClassLoader(), mgr);
						}
					}
				}
			}
			catch (IOException e)
			{
				ServoyLog.logError("Exception while loading extension XML files from JAR file '" + jarPath + "'.", e);
			}
		}
	}
}
