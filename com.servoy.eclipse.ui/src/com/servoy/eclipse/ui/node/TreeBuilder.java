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
package com.servoy.eclipse.ui.node;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import org.eclipse.swt.graphics.Image;

import com.servoy.eclipse.core.Activator;
import com.servoy.eclipse.core.ServoyModelManager;
import com.servoy.eclipse.core.XMLScriptObjectAdapterLoader;
import com.servoy.eclipse.core.doc.IDocumentationManagerProvider;
import com.servoy.eclipse.ui.views.solutionexplorer.SolutionExplorerListContentProvider;
import com.servoy.j2db.documentation.ClientSupport;
import com.servoy.j2db.documentation.IDocumentationManager;
import com.servoy.j2db.documentation.IFunctionDocumentation;
import com.servoy.j2db.documentation.IObjectDocumentation;

public class TreeBuilder
{
	private static IDocumentationManager docManager;
	private static boolean docManagerLoaded = false;

	private static IDocumentationManager getDocManager()
	{
		if (!docManagerLoaded)
		{
			URL url = XMLScriptObjectAdapterLoader.class.getResource("doc/servoydoc_jslib.xml");
			IDocumentationManagerProvider documentationManagerProvider = Activator.getDefault().getDocumentationManagerProvider();
			if (documentationManagerProvider != null)
			{
				docManager = documentationManagerProvider.fromXML(url, null);
			}
			docManagerLoaded = true;
		}
		return docManager;
	}

	/**
	 * @param prefix
	 * @return
	 */
	public static UserNode[] createLengthAndArray(IImageLookup imageLookup, String prefix)
	{
		Image propertiesIcon = imageLookup.loadImage("properties_icon.gif");
		List<UserNode> dlm = new ArrayList<UserNode>();
		dlm.add(new UserNode(
			"allnames", UserNodeType.ARRAY, prefix + ".allnames", prefix + ".allnames", "Get all names as an array", null, imageLookup.loadImage("special_properties_icon.gif")));
		dlm.add(new UserNode("length", UserNodeType.ARRAY, prefix + ".length", prefix + ".length", "Get the length of the array", null, propertiesIcon));
		dlm.add(new UserNode("['name']", UserNodeType.ARRAY, prefix + "['name']", prefix + "['name']", "Get an element by name", null, propertiesIcon));
		return dlm.toArray(new UserNode[dlm.size()]);
	}

	public static UserNode[] createJSArray(IImageLookup imageLookup)
	{
		return createTypedArray(imageLookup, com.servoy.j2db.documentation.scripting.docs.Array.class, UserNodeType.ARRAY, null);
	}

	public static UserNode[] createJSDate(IImageLookup imageLookup)
	{
		return createTypedArray(imageLookup, com.servoy.j2db.documentation.scripting.docs.Date.class, UserNodeType.DATE, null);
	}

	public static UserNode[] createJSString(IImageLookup imageLookup)
	{
		return createTypedArray(imageLookup, com.servoy.j2db.documentation.scripting.docs.String.class, UserNodeType.STRING, null);
	}

	public static UserNode[] createJSMathFunctions(IImageLookup imageLookup)
	{
		return createTypedArray(imageLookup, com.servoy.j2db.documentation.scripting.docs.Math.class, UserNodeType.FUNCTIONS_ITEM, null);
	}

	public static UserNode[] createJSONFunctions(IImageLookup imageLookup)
	{
		return createTypedArray(imageLookup, com.servoy.j2db.documentation.scripting.docs.JSON.class, UserNodeType.JSON, null);
	}

	public static UserNode[] createFlows(IImageLookup imageLookup)
	{
		return createTypedArray(imageLookup, com.servoy.j2db.documentation.scripting.docs.Statements.class, UserNodeType.STATEMENTS_ITEM, null);
	}

	public static UserNode[] createXMLListMethods(IImageLookup imageLookup)
	{
		return createTypedArray(imageLookup, com.servoy.j2db.documentation.scripting.docs.XMLList.class, UserNodeType.XML_LIST_METHODS, null);
	}

	public static UserNode[] createXMLMethods(IImageLookup imageLookup)
	{
		return createTypedArray(imageLookup, com.servoy.j2db.documentation.scripting.docs.XML.class, UserNodeType.XML_METHODS, null);
	}

	public static UserNode[] createJSRegexp(IImageLookup imageLookup)
	{
		return createTypedArray(imageLookup, com.servoy.j2db.documentation.scripting.docs.RegExp.class, UserNodeType.REGEXP, null);
	}

	public static UserNode[] createTypedArray(IImageLookup imageLookup, Class< ? > clazz, UserNodeType type, List<UserNode> existingDlm)
	{
		Image constructorIcon = imageLookup.loadImage("constructor.gif");
		Image functionIcon = imageLookup.loadImage("function.gif");
		Image propertiesIcon = imageLookup.loadImage("properties_icon.gif");

		List<UserNode> dlm = existingDlm != null ? new ArrayList<UserNode>(existingDlm) : new ArrayList<UserNode>();

		IDocumentationManager dm = getDocManager();
		if (dm != null)
		{
			IObjectDocumentation objDoc = dm.getObjectByQualifiedName(clazz.getCanonicalName());
			if (objDoc != null)
			{
				// We need constructors listed before anything else.
				for (IFunctionDocumentation fdoc : objDoc.getFunctions())
				{
					if (fdoc.getType() == IFunctionDocumentation.TYPE_CONSTRUCTOR && !fdoc.isDeprecated())
					{
						dlm.add(fdoc2usernode(fdoc, type, constructorIcon));
					}
				}
				// We need properties listed before functions.
				for (IFunctionDocumentation fdoc : objDoc.getFunctions())
				{
					if (fdoc.getType() == IFunctionDocumentation.TYPE_PROPERTY && !fdoc.isDeprecated())
					{
						dlm.add(fdoc2usernode(fdoc, type, propertiesIcon));
					}
				}
				for (IFunctionDocumentation fdoc : objDoc.getFunctions())
				{
					if (fdoc.getType() == IFunctionDocumentation.TYPE_FUNCTION && !fdoc.isDeprecated())
					{
						dlm.add(fdoc2usernode(fdoc, type, functionIcon));
					}
				}
			}
		}

		return dlm.toArray(new UserNode[dlm.size()]);
	}

	private static UserNode fdoc2usernode(IFunctionDocumentation fdoc, UserNodeType type, Image functionIcon)
	{
		ClientSupport clientType = ServoyModelManager.getServoyModelManager().getServoyModel().getActiveSolutionClientType();
		String tooltip = "<html><body><b>" + fdoc.getFullSignature(true, true) + "</b><br>" + fdoc.getDescription(clientType) + "</body></html>";
		UserNode un = new UserNode(fdoc.getFullSignature(false, true), type, fdoc.getSignature("."), fdoc.getSample(clientType), tooltip, null,
			functionIcon);
		un.setClientSupport(fdoc.getClientSupport());
		return un;
	}

	public static UserNode[] docToNodes(Class< ? > clz, IImageLookup imageLookup, UserNodeType type, String codePrefix, List<SimpleUserNode> existingDlm)
	{
		List<SimpleUserNode> dlm = docToNodesInternal(clz, imageLookup, type, codePrefix, existingDlm, null, null);
		return dlm.toArray(new UserNode[dlm.size()]);
	}

	public static List<SimpleUserNode> docToSomeNodes(Class< ? > clz, IImageLookup imageLookup, UserNodeType type, String codePrefix,
		List<SimpleUserNode> existingDlm, Map<String, Object> onlyThese)
	{
		return docToNodesInternal(clz, imageLookup, type, codePrefix, existingDlm, onlyThese, null);
	}

	public static List<SimpleUserNode> docToOneNode(Class< ? > clz, IImageLookup imageLookup, UserNodeType type, String codePrefix,
		List<SimpleUserNode> existingDlm, String name, Object realObject, Image icon)
	{
		Map<String, Object> onlyThese = new HashMap<String, Object>();
		onlyThese.put(name, realObject);
		return docToNodesInternal(clz, imageLookup, type, codePrefix, existingDlm, onlyThese, icon);
	}

	public static List<SimpleUserNode> docToNodesInternal(Class< ? > clz, IImageLookup imageLookup, UserNodeType type, String codePrefix,
		List<SimpleUserNode> existingDlm, Map<String, Object> onlyThese, Image icon)
	{
		Image functionIcon = imageLookup.loadImage("function.gif");
		Image propertiesIcon = imageLookup.loadImage("properties_icon.gif");
		Image specialPropertiesIcon = imageLookup.loadImage("special_properties_icon.gif");

		List<SimpleUserNode> dlm = existingDlm != null ? existingDlm : new ArrayList<SimpleUserNode>();

		IObjectDocumentation objDoc = XMLScriptObjectAdapterLoader.getObjectDocumentation(clz);
		if (objDoc != null)
		{
			// We need properties listed before functions.
			visitDocs(objDoc.getFunctions(), IFunctionDocumentation.TYPE_PROPERTY, dlm, onlyThese, type, codePrefix, icon != null ? icon : propertiesIcon,
				icon != null ? icon : specialPropertiesIcon);
			visitDocs(objDoc.getFunctions(), IFunctionDocumentation.TYPE_FUNCTION, dlm, onlyThese, type, codePrefix, icon != null ? icon : functionIcon,
				icon != null ? icon : functionIcon);
		}

		return dlm;
	}

	private static void visitDocs(SortedSet<IFunctionDocumentation> fdocs, Integer typeFilter, List<SimpleUserNode> dlm, Map<String, Object> onlyThese,
		UserNodeType type, String codePrefix, Image icon, Image specialIcon)
	{
		for (IFunctionDocumentation fdoc : fdocs)
		{
			String answeredName = answersTo(fdoc, onlyThese);
			if ((onlyThese == null || answeredName != null) && fdoc.getType() == typeFilter && !fdoc.isDeprecated())
			{
				Object realObject = null;
				if (answeredName != null) realObject = onlyThese.get(answeredName);

				String toolTip = fdoc.getDescription(ServoyModelManager.getServoyModelManager().getServoyModel().getActiveSolutionClientType());
				String tmp = "<html><body><b>" + SolutionExplorerListContentProvider.getReturnTypeString(fdoc.getReturnedType()) + " " + fdoc.getMainName() + "</b>";
				if ("".equals(toolTip))
				{
					toolTip = tmp + "</body></html>";
				}
				else
				{
					toolTip = tmp + "<br><pre>" + toolTip + "</pre></body></html>";
				}
				String fdocSample = fdoc.getSample(ServoyModelManager.getServoyModelManager().getServoyModel().getActiveSolutionClientType());
				dlm.add(new UserNode(fdoc.getMainName(), type, fdoc.getSignature(codePrefix != null ? codePrefix : null), fdocSample, toolTip, realObject,
					fdoc.isSpecial() ? specialIcon : icon));
			}
		}
	}

	private static String answersTo(IFunctionDocumentation fdoc, Map<String, Object> onlyThese)
	{
		String result = null;
		if (onlyThese != null)
		{
			for (String name : onlyThese.keySet())
				if (fdoc.answersTo(name))
				{
					result = name;
					break;
				}
		}
		return result;
	}
}
