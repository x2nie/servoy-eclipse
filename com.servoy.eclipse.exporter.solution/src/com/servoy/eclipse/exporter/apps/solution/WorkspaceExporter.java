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

package com.servoy.eclipse.exporter.apps.solution;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.equinox.app.IApplicationContext;
import org.json.JSONException;

import com.servoy.eclipse.exporter.apps.common.AbstractWorkspaceExporter;
import com.servoy.eclipse.exporter.apps.common.IArgumentChest;
import com.servoy.eclipse.model.ServoyModelFinder;
import com.servoy.eclipse.model.nature.ServoyProject;
import com.servoy.eclipse.model.repository.EclipseExportI18NHelper;
import com.servoy.eclipse.model.util.ServoyExporterUtils;
import com.servoy.eclipse.model.util.ServoyLog;
import com.servoy.eclipse.model.util.WorkspaceFileAccess;
import com.servoy.j2db.ClientVersion;
import com.servoy.j2db.persistence.AbstractRepository;
import com.servoy.j2db.persistence.RepositoryException;
import com.servoy.j2db.persistence.Solution;
import com.servoy.j2db.server.shared.ApplicationServerSingleton;
import com.servoy.j2db.server.shared.IApplicationServerSingleton;
import com.servoy.j2db.server.shared.IUserManager;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Pair;
import com.servoy.j2db.util.Settings;
import com.servoy.j2db.util.xmlxport.IMetadataDefManager;
import com.servoy.j2db.util.xmlxport.ITableDefinitionsManager;
import com.servoy.j2db.util.xmlxport.IXMLExporter;

/**
 * Eclipse application that can be used for exporting servoy solutions in .servoy format (that can be used to import solutions afterwards in developer/app. server). 
 * 
 * @author acostescu
 */
public class WorkspaceExporter extends AbstractWorkspaceExporter
{

	@Override
	protected IArgumentChest createArgumentChest(IApplicationContext context)
	{
		return new ArgumentChest((String[])context.getArguments().get(IApplicationContext.APPLICATION_ARGS));
	}

	@Override
	protected void exportActiveSolution(IArgumentChest config)
	{
		ArgumentChest configuration = (ArgumentChest)config;

		IApplicationServerSingleton as = ApplicationServerSingleton.get();
		AbstractRepository rep = (AbstractRepository)as.getDeveloperRepository();
		IUserManager sm = as.getUserManager();

		EclipseExportI18NHelper eeI18NHelper = new EclipseExportI18NHelper(new WorkspaceFileAccess(ResourcesPlugin.getWorkspace()));
		IXMLExporter exporter = as.createXMLExporter(rep, sm, configuration, Settings.getInstance(), as.getDataServer(), as.getClientId(), eeI18NHelper);
		ServoyProject activeProject = ServoyModelFinder.getServoyModel().getActiveProject();
		Solution solution = activeProject.getSolution();

		if (solution != null)
		{
			ITableDefinitionsManager tableDefManager = null;
			IMetadataDefManager metadataDefManager = null;
			if (isDbDown() || configuration.getExportUsingDbiFileInfoOnly())
			{
				Pair<ITableDefinitionsManager, IMetadataDefManager> defManagers;
				try
				{
					defManagers = ServoyExporterUtils.getInstance().prepareDbiFilesBasedExportData(solution, configuration.shouldExportModules(),
						configuration.shouldExportI18NData(), configuration.getExportAllTablesFromReferencedServers(), configuration.shouldExportMetaData());
				}
				catch (CoreException e)
				{
					Debug.error(e);
					defManagers = null;
				}
				catch (JSONException e)
				{
					Debug.error(e);
					defManagers = null;
				}
				catch (IOException e)
				{
					Debug.error(e);
					defManagers = null;
				}
				if (defManagers != null)
				{
					tableDefManager = defManagers.getLeft();
					metadataDefManager = defManagers.getRight();
				}
			}

			try
			{
				exporter.exportSolutionToFile(solution, new File(configuration.getExportFileName()), ClientVersion.getVersion(),
					ClientVersion.getReleaseNumber(), configuration.shouldExportMetaData(), configuration.shouldExportSampleData(),
					configuration.getNumberOfSampleDataExported(), configuration.shouldExportI18NData(), configuration.shouldExportUsers(),
					configuration.shouldExportModules(), configuration.shouldProtectWithPassword(), tableDefManager, metadataDefManager);
			}
			catch (final RepositoryException e)
			{
				ServoyLog.logError("Failed to export solution.", e); //$NON-NLS-1$
				outputError("Exception while exporting solution. EXPORT FAILED for this solution. Check workspace log."); //$NON-NLS-1$
				exitCode = EXIT_EXPORT_FAILED;
			}
		}
		else
		{
			outputError("Solution in project '" + activeProject.getProject().getName() + "' is not valid. EXPORT FAILED for this solution."); //$NON-NLS-1$//$NON-NLS-2$
			exitCode = EXIT_EXPORT_FAILED;
		}
	}

}