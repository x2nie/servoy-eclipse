/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2012 Servoy BV

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

package com.servoy.eclipse.marketplace;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.servoy.eclipse.core.util.UIUtils;
import com.servoy.eclipse.model.util.ServoyLog;
import com.servoy.extension.ExtensionUtils;
import com.servoy.extension.parser.Content;
import com.servoy.j2db.util.Debug;


/**
 * 
 * Content installer for Servoy Marketplace
 * @author gboros
 *
 */
public class ContentInstaller
{
	private final ContentWrapper contentWrapper;

	public ContentInstaller(File expFile, Content content, File installDir, boolean noFilesCopiedYet)
	{
		this.contentWrapper = new ContentWrapper(expFile, content, installDir, noFilesCopiedYet);
	}

	/**
	 * This should only be called from the SWT UI thread. After installing all items it will perform a clean.
	 */
	public void installAll()
	{
		try
		{
			install(getAllInstallItems());
		}
		finally
		{
			clean();
		}
	}

	/**
	 * Disposes of any temporary used resources.
	 */
	public void clean()
	{
		contentWrapper.cleanTemp();
	}

	public void install(ArrayList<InstallItem> installItems)
	{
		for (final InstallItem installItem : installItems)
		{
			try
			{
				MarketplaceProgressMonitorDialog progressMonitorDialog = new MarketplaceProgressMonitorDialog(UIUtils.getActiveShell(), installItem.getName());
				progressMonitorDialog.run(true, true, new IRunnableWithProgress()
				{

					public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException
					{
						try
						{
							installItem.install(monitor);
						}
						catch (OperationCanceledException ex)
						{
							// canceled on purpose by the user
						}
						catch (final Exception ex)
						{
							Display.getDefault().syncExec(new Runnable()
							{
								public void run()
								{
									Throwable cause = ex.getCause();
									String msg = cause != null ? cause.getMessage() : ex.getMessage();
									MessageDialog.openError(UIUtils.getActiveShell(), "Extension install task", "Error installing " + installItem.getName() +
										".\n\n" + msg);
								}
							});
						}
					}
				});
			}
			catch (Exception ex1)
			{
				ServoyLog.logError(ex1);
			}
		}
	}

	public ArrayList<InstallItem> getAllInstallItems()
	{
		ArrayList<InstallItem> allInstallItems = new ArrayList<InstallItem>();
		allInstallItems.addAll(getSolutionInstallItems());
		allInstallItems.addAll(getUpdateURLInstallItems());
		allInstallItems.addAll(getTeamProjectSetInstallItems());

		StylesInstall styleInstallItem = getStylesInstallItem();
		if (styleInstallItem != null) allInstallItems.add(styleInstallItem);

		return allInstallItems;
	}

	public ArrayList<InstallItem> getSolutionInstallItems()
	{
		ArrayList<InstallItem> solutionInstallItems = new ArrayList<InstallItem>();

		File[] solutionFiles = contentWrapper.getSolutionFiles();
		if (solutionFiles != null)
		{
			for (File solutionFile : solutionFiles)
				solutionInstallItems.add(new SolutionInstall(solutionFile));
		}

		return solutionInstallItems;
	}

	public ArrayList<InstallItem> getUpdateURLInstallItems()
	{
		ArrayList<InstallItem> updateURLInstallItems = new ArrayList<InstallItem>();

		if (contentWrapper.getEclipseUpdateSiteURLs() != null)
		{
			for (String updateURL : contentWrapper.getEclipseUpdateSiteURLs())
				updateURLInstallItems.add(new UpdateURLInstall(updateURL));
		}

		return updateURLInstallItems;
	}

	public StylesInstall getStylesInstallItem()
	{
		File[] styleFiles = contentWrapper.getStyleFiles();
		return styleFiles != null ? new StylesInstall(styleFiles) : null;
	}

	public ArrayList<InstallItem> getTeamProjectSetInstallItems()
	{
		ArrayList<InstallItem> teamProjectSetInstallItems = new ArrayList<InstallItem>();

		File[] teamProjectSetFiles = contentWrapper.getTeamProjectSets();
		if (teamProjectSetFiles != null)
		{
			for (File teamProjectSetFile : teamProjectSetFiles)
				teamProjectSetInstallItems.add(new TeamProjectSetInstall(teamProjectSetFile));
		}

		return teamProjectSetInstallItems;
	}

	class ContentWrapper
	{
		private final static String CONTENT_TMP = ".tmpContent";

		private final File expFileObj;
		private final Content contentObj;
		private final File installDir;
		private final boolean noFilesCopiedYet;

		ContentWrapper(File expFile, Content content, File installDir, boolean noFilesCopiedYet)
		{
			expFileObj = expFile;
			contentObj = content;
			this.noFilesCopiedYet = noFilesCopiedYet;
			this.installDir = installDir;
			cleanTemp();
		}

		File[] getTeamProjectSets()
		{
			return getFilesForImportPaths(contentObj.teamProjectSetPaths);
		}

		File[] getSolutionFiles()
		{
			return getFilesForImportPaths(contentObj.solutionToImportPaths);
		}

		File[] getStyleFiles()
		{
			return getFilesForImportPaths(contentObj.styleToImportPaths);
		}

		String[] getEclipseUpdateSiteURLs()
		{
			return contentObj.eclipseUpdateSiteURLs;
		}

		void cleanTemp()
		{
			// cleanup any temporary files that we might have created
			File tmpFolder = new File(installDir, ExtensionUtils.EXPFILES_FOLDER);
			tmpFolder = new File(tmpFolder, CONTENT_TMP);
			FileUtils.deleteQuietly(tmpFolder);
		}

		private File[] getFilesForImportPaths(String[] paths)
		{
			File[] files = null;

			if (paths != null)
			{
				files = new File[paths.length];
				for (int i = 0; i < paths.length; i++)
				{
					File file = new File(installDir, paths[i]);
					if (noFilesCopiedYet || !file.exists())
					{
						// extract file to a temporary location
						file = new File(installDir, ExtensionUtils.EXPFILES_FOLDER);
						file = new File(file, CONTENT_TMP);
						file = new File(file, paths[i]);
						try
						{
							file.getParentFile().mkdirs();
							ExtensionUtils.extractZipEntryToFile(expFileObj, paths[i], file);
						}
						catch (Exception ex)
						{
							Debug.error(ex);
						}
					}
					files[i] = file;
				}
			}

			return files;
		}
	}

	class MarketplaceProgressMonitorDialog extends ProgressMonitorDialog
	{
		private final String installName;

		public MarketplaceProgressMonitorDialog(Shell parent, String installName)
		{
			super(parent);
			this.installName = installName;
		}

		@Override
		protected void configureShell(final Shell shell)
		{
			super.configureShell(shell);
			shell.setText("Extension install task: '" + installName + "'...");
		}
	}
}
