/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2015 Servoy BV

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

package com.servoy.eclipse.model.war.exporter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.apache.wicket.util.io.IOUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.dltk.ast.ASTNode;
import org.eclipse.dltk.javascript.ast.AbstractNavigationVisitor;
import org.eclipse.dltk.javascript.ast.CallExpression;
import org.eclipse.dltk.javascript.ast.Script;
import org.eclipse.dltk.javascript.parser.JavaScriptParser;
import org.sablo.specification.WebComponentSpecProvider;
import org.sablo.specification.WebServiceSpecProvider;

import com.servoy.eclipse.model.ServoyModelFinder;
import com.servoy.eclipse.model.nature.ServoyProject;
import com.servoy.eclipse.model.repository.SolutionSerializer;
import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.IFormElement;
import com.servoy.j2db.persistence.IPersist;
import com.servoy.j2db.persistence.IRootObject;
import com.servoy.j2db.persistence.ISupportChilds;
import com.servoy.j2db.server.ngclient.template.FormTemplateGenerator;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Pair;

/**
 * Base class for the war export model used in the developer and the one used in command line export.
 * @author emera
 */
public abstract class AbstractWarExportModel implements IWarExportModel
{

	private final Set<String> usedComponents;
	private final Set<String> usedServices;

	public AbstractWarExportModel()
	{
		FlattenedSolution solution = ServoyModelFinder.getServoyModel().getActiveProject().getFlattenedSolution();
		Iterator<Form> forms = solution.getForms(false);
		usedComponents = new TreeSet<String>();
		usedServices = new TreeSet<String>();
		while (forms.hasNext())
		{
			Form form = forms.next();
			findUsedComponents(form);
			extractUsedComponentsAndServices(SolutionSerializer.getRelativePath(form, false) + form.getName() + SolutionSerializer.JS_FILE_EXTENSION);
			if (form.getNavigatorID() == Form.NAVIGATOR_DEFAULT)
			{
				usedComponents.add("servoycore-navigator");
				usedComponents.add("servoycore-slider");
			}
		}

		for (Pair<String, IRootObject> scope : solution.getAllScopes())
		{
			extractUsedComponentsAndServices(SolutionSerializer.getRelativePath(scope.getRight(), false) + scope.getLeft() +
					SolutionSerializer.JS_FILE_EXTENSION);
		}

		//these are always required
		usedComponents.add("servoycore-errorbean");
		usedComponents.add("servoycore-portal");
	}

	private void findUsedComponents(ISupportChilds parent)
	{
		Iterator<IPersist> persists = parent.getAllObjects();
		while (persists.hasNext())
		{
			IPersist persist = persists.next();
			if (persist instanceof IFormElement)
			{
				usedComponents.add(FormTemplateGenerator.getComponentTypeName((IFormElement)persist));
			}
			if (persist instanceof ISupportChilds)
			{
				findUsedComponents((ISupportChilds)persist);
			}
		}
	}

	public void extractUsedComponentsAndServices(String scriptPath)
	{
		IFile scriptFile = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(scriptPath));
		if (scriptFile.exists())
		{
			try
			{
				InputStream is = scriptFile.getContents();
				String source = IOUtils.toString(is);
				is.close();
				if (source != null)
				{
					JavaScriptParser parser = new JavaScriptParser();
					Script script = parser.parse(source, null);
					script.visitAll(new AbstractNavigationVisitor<ASTNode>()
					{

						/*
						 * (non-Javadoc)
						 *
						 * @see org.eclipse.dltk.javascript.ast.AbstractNavigationVisitor#visitCallExpression(org.eclipse.dltk.javascript.ast.CallExpression)
						 */
						@Override
						public ASTNode visitCallExpression(CallExpression node)
						{
							if (node.getExpression().getChilds().size() > 0)
							{
								String expr = node.getExpression().getChilds().get(0).toString();
								if (expr.startsWith("plugins."))
								{
									String[] parts = expr.split("\\.");
									if (parts.length > 1 && WebServiceSpecProvider.getInstance().getWebServiceSpecification(parts[1]) != null)
									{
										usedServices.add(WebServiceSpecProvider.getInstance().getWebServiceSpecification(parts[1]).getName());
									}
								}
								else if (expr.contains("newWebComponent"))
								{
									if (node.getArguments().size() > 1)
									{
										String componentName = node.getArguments().get(1).toString();
										if (componentName.startsWith("\"") || componentName.startsWith("'"))
										{
											componentName = componentName.replaceAll("'|\"", "");
											if (WebComponentSpecProvider.getInstance().getWebComponentSpecification(componentName) != null)
											{
												usedComponents.add(componentName);
											}
										}
									}
								}
							}
							return super.visitCallExpression(node);
						}
					});
				}
			}
			catch (CoreException e)
			{
				Debug.error(e);
			}
			catch (IOException e)
			{
				Debug.error(e);
			}
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.eclipse.model.war.exporter.IWarExportModel#getUsedComponents()
	 */
	@Override
	public Set<String> getUsedComponents()
	{
		return usedComponents;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.eclipse.model.war.exporter.IWarExportModel#getUsedServices()
	 */
	@Override
	public Set<String> getUsedServices()
	{
		return usedServices;
	}

	@Override
	public String[] getModulesToExport()
	{
		ServoyProject[] modules = ServoyModelFinder.getServoyModel().getModulesOfActiveProject();
		String[] toExport = new String[modules.length];
		for (int i = 0; i < modules.length; i++)
		{
			toExport[i] = modules[i].getSolution().getName();
		}
		return toExport;
	}

	@Override
	public boolean isProtectWithPassword()
	{
		return false;
	}

	@Override
	public String getPassword()
	{
		return null;
	}

	@Override
	public boolean isExportReferencedModules()
	{
		return true;
	}
}
