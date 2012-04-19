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
package com.servoy.eclipse.ui.wizards;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.IPageChangedListener;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.PageChangedEvent;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IExportWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.json.JSONException;

import com.servoy.eclipse.core.ServoyModel;
import com.servoy.eclipse.core.ServoyModelManager;
import com.servoy.eclipse.core.util.SerialRule;
import com.servoy.eclipse.model.builder.ServoyBuilder;
import com.servoy.eclipse.model.nature.ServoyProject;
import com.servoy.eclipse.model.repository.DataModelManager;
import com.servoy.eclipse.model.repository.EclipseExportI18NHelper;
import com.servoy.eclipse.model.util.IFileAccess;
import com.servoy.eclipse.model.util.ServoyLog;
import com.servoy.eclipse.model.util.WorkspaceFileAccess;
import com.servoy.eclipse.ui.Activator;
import com.servoy.eclipse.ui.util.EditorUtil;
import com.servoy.j2db.ClientVersion;
import com.servoy.j2db.J2DBGlobals;
import com.servoy.j2db.dataprocessing.IDataServerInternal;
import com.servoy.j2db.persistence.AbstractRepository;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.IPersist;
import com.servoy.j2db.persistence.IRepository;
import com.servoy.j2db.persistence.Relation;
import com.servoy.j2db.persistence.RepositoryException;
import com.servoy.j2db.persistence.Solution;
import com.servoy.j2db.persistence.ValueList;
import com.servoy.j2db.server.shared.ApplicationServerSingleton;
import com.servoy.j2db.server.shared.IApplicationServerSingleton;
import com.servoy.j2db.server.shared.IUserManager;
import com.servoy.j2db.util.DataSourceUtils;
import com.servoy.j2db.util.Debug;
import com.servoy.j2db.util.Settings;
import com.servoy.j2db.util.Utils;
import com.servoy.j2db.util.xmlxport.IMetadataDefManager;
import com.servoy.j2db.util.xmlxport.ITableDefinitionsManager;
import com.servoy.j2db.util.xmlxport.IXMLExporter;
import com.servoy.j2db.util.xmlxport.MetadataDef;
import com.servoy.j2db.util.xmlxport.TableDef;

public class ExportSolutionWizard extends Wizard implements IExportWizard, IPageChangedListener
{
	private Solution activeSolution;
	private ExportSolutionModel exportModel;

	private FileSelectionPage fileSelectionPage;
	private ExportOptionsPage exportOptionsPage;
	private ModulesSelectionPage modulesSelectionPage;
	private PasswordPage passwordPage;

	private final IFileAccess workspace;

	public ExportSolutionWizard()
	{
		super();
		setWindowTitle("Solution Export"); //$NON-NLS-1$
		IDialogSettings workbenchSettings = Activator.getDefault().getDialogSettings();
		IDialogSettings section = workbenchSettings.getSection("SolutionExportWizard");//$NON-NLS-1$
		if (section == null)
		{
			section = workbenchSettings.addNewSection("SolutionExportWizard");//$NON-NLS-1$
		}
		setDialogSettings(section);
		workspace = new WorkspaceFileAccess(ResourcesPlugin.getWorkspace());
	}

	/**
	 * 
	 * @return true for errors, null for warnings, false for no markers
	 */
	private Boolean hasMarkers(String[] projects)
	{
		if (projects != null && projects.length > 0)
		{
			boolean hasWarnings = false;
			try
			{
				for (String moduleName : projects)
				{
					ServoyProject module = ServoyModelManager.getServoyModelManager().getServoyModel().getServoyProject(moduleName);
					if (module != null)
					{
						IMarker[] markers = module.getProject().findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
						for (IMarker marker : markers)
						{
							if (marker.getAttribute(IMarker.SEVERITY) != null && marker.getAttribute(IMarker.SEVERITY).equals(IMarker.SEVERITY_ERROR))
							{
								return Boolean.TRUE;
							}
							if (marker.getAttribute(IMarker.SEVERITY) != null && marker.getAttribute(IMarker.SEVERITY).equals(IMarker.SEVERITY_WARNING))
							{
								hasWarnings = true;
							}
						}
					}
				}
			}
			catch (Exception ex)
			{
				ServoyLog.logError(ex);
			}
			if (hasWarnings) return null;
		}
		return Boolean.FALSE;
	}

	/** 
	 * 
	 * @return true if the database is down (servers or tables are inaccessible)
	 */
	private Boolean hasDbErrorMarkers(String[] projects)
	{
		if (projects != null && projects.length > 0)
		{
			try
			{
				for (String moduleName : projects)
				{
					ServoyProject module = ServoyModelManager.getServoyModelManager().getServoyModel().getServoyProject(moduleName);
					if (module != null)
					{
						IMarker[] markers = module.getProject().findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE);
						for (IMarker marker : markers)
						{
							// db down errors = missing server (what other cases?)
							if (marker.getAttribute(IMarker.SEVERITY) != null && marker.getAttribute(IMarker.SEVERITY).equals(IMarker.SEVERITY_ERROR) &&
								ServoyBuilder.MISSING_SERVER.equals(marker.getType()))
							{
								return Boolean.TRUE;
							}
						}
					}
				}
			}
			catch (Exception ex)
			{
				ServoyLog.logError(ex);
			}
		}
		return Boolean.FALSE;
	}

	private ITableDefinitionsManager tableDefManager = null;
	private IMetadataDefManager metadataDefManager = null;

	@Override
	public boolean performFinish()
	{
		EditorUtil.saveDirtyEditors(getShell(), true);
		getDialogSettings().put("initialFileName", exportModel.getFileName());

		WorkspaceJob exportJob = new WorkspaceJob("Exporting solution '" + activeSolution.getName() + "'")
		{
			@Override
			public IStatus runInWorkspace(IProgressMonitor monitor) throws CoreException
			{
				int totalDuration = IProgressMonitor.UNKNOWN;
				if (exportModel.getModulesToExport() != null) totalDuration = (int)(1.42 * exportModel.getModulesToExport().length); // make the main export be 70% of the time, leave the rest for sample data
				monitor.beginTask("Exporting solution", totalDuration);

				AbstractRepository rep = (AbstractRepository)ServoyModel.getDeveloperRepository();

				final IApplicationServerSingleton as = ApplicationServerSingleton.get();
				IUserManager sm = as.getUserManager();
				EclipseExportUserChannel eeuc = new EclipseExportUserChannel(exportModel, monitor);
				EclipseExportI18NHelper eeI18NHelper = new EclipseExportI18NHelper(workspace);
				IXMLExporter exporter = as.createXMLExporter(rep, sm, eeuc, Settings.getInstance(), as.getDataServer(), as.getClientId(), eeI18NHelper);

				if (fileSelectionPage.dbDownErrors &&
					(exportModel.isExportMetaData() || exportModel.isExportSampleData() || exportModel.isExportI18NData() || exportModel.isExportUsers() || exportModel.isExportReferencedModules()))
				{
					prepareDbDownExportData();
				}

				try
				{
					exporter.exportSolutionToFile(activeSolution, new File(exportModel.getFileName()), ClientVersion.getVersion(),
						ClientVersion.getReleaseNumber(), exportModel.isExportMetaData(), exportModel.isExportSampleData(),
						exportModel.getNumberOfSampleDataExported(), exportModel.isExportI18NData(), exportModel.isExportUsers(),
						exportModel.isExportReferencedModules(), exportModel.isProtectWithPassword(), tableDefManager, metadataDefManager);
					monitor.done();

					if (fileSelectionPage.dbDownErrors) Display.getDefault().syncExec(new Runnable()
					{
						public void run()
						{
							MessageDialog.openError(Display.getDefault().getActiveShell(), "Solution exported with errors",
								"Solution has been exported with errors. This may prevent the solution from functioning well.\nOnly minimal database info has been exported.");
						}
					});

					return Status.OK_STATUS;
				}
				catch (final RepositoryException e)
				{
					ServoyLog.logError("Failed to export solution.", e);
					monitor.done();
					Display.getDefault().syncExec(new Runnable()
					{
						public void run()
						{
							String message;
							// Try to be nice with the user when presenting error message.
							if (e.getCause() != null) message = e.getCause().getMessage();
							else message = e.getMessage();
							MessageDialog.openError(Display.getDefault().getActiveShell(), "Failed to export the active solution", message);
						}
					});
					return Status.CANCEL_STATUS;
				}

			}
		};

		ISchedulingRule rule = new SerialRule();
		exportJob.setRule(rule);
		exportJob.setUser(true); // we want the progress to be visible in a dialog, not to stay in the status bar
		exportJob.schedule();

		return true;
	}

	/**
	 * This method takes care of minimal db info to be used in export in the case in which the db is down.
	 * It will create and initialize the table def manager and the metadata manager, which will contain server and table and metadata info
	 * to be used at solution export.
	 * 
	 * NOTE: if there are no dbi files created export info will be empty
	 * 
	 * @throws CoreException
	 */
	private void prepareDbDownExportData() throws CoreException
	{
		// A. get only the needed servers (and tables) 
		final Map<String, List<String>> neededServersTables = getNeededServerTables(activeSolution, exportModel.isExportReferencedModules(),
			exportModel.isExportI18NData());

		DataModelManager dmm = ServoyModelManager.getServoyModelManager().getServoyModel().getDataModelManager();

		// B. for needed tables, get dbi files (db is down)
		Map<String, List<IFile>> server_tableDbiFiles = new HashMap<String, List<IFile>>();
		for (String serverName : neededServersTables.keySet())
		{
			IFolder serverInformationFolder = dmm.getDBIFileContainer(serverName);
			final List<IFile> dbiz = new ArrayList<IFile>();
			final String srvnm = serverName;
			if (serverInformationFolder.exists())
			{
				serverInformationFolder.accept(new IResourceVisitor()
				{
					public boolean visit(IResource resource) throws CoreException
					{
						String extension = resource.getFileExtension();
						if (extension != null && extension.equalsIgnoreCase(DataModelManager.COLUMN_INFO_FILE_EXTENSION))
						{
							//we found a dbi file
							String tableName = resource.getName().substring(0,
								resource.getName().length() - DataModelManager.COLUMN_INFO_FILE_EXTENSION_WITH_DOT.length());
							if (neededServersTables.get(srvnm).contains(tableName)) dbiz.add((IFile)resource);
						}
						return true;
					}

				}, IResource.DEPTH_ONE, false);
			}
			server_tableDbiFiles.put(serverName, dbiz);
		}

		// C. deserialize table dbis to get tabledefs and metadata info
		Map<String, List<TableDef>> serverTableDefs = new HashMap<String, List<TableDef>>();
		Set<String> servers = server_tableDbiFiles.keySet();
		Iterator<String> it = servers.iterator();
		List<MetadataDef> metadataDefs = new ArrayList<MetadataDef>();
		while (it.hasNext())
		{
			String serverName = it.next();
			List<IFile> files = server_tableDbiFiles.get(serverName);
			List<TableDef> tableDefs = new ArrayList<TableDef>();
			for (IFile file : files)
			{
				if (file.exists())
				{
					InputStream is = file.getContents(true);
					String dbiFileContent = null;
					try
					{
						dbiFileContent = Utils.getTXTFileContent(is, Charset.forName("UTF8"));
					}
					finally
					{
						Utils.closeInputStream(is);
					}
					if (dbiFileContent != null)
					{
						try
						{
							TableDef tableInfo = dmm.deserializeTableInfo(dbiFileContent);
							tableDefs.add(tableInfo);
							if (exportModel.isExportMetaData() && tableInfo.isMetaData)
							{
								String ds = DataSourceUtils.createDBTableDataSource(serverName, tableInfo.name);
								IFile mdf = dmm.getMetaDataFile(ds);
								if (mdf != null && mdf.exists())
								{
									String wscontents = null;
									try
									{
										wscontents = new WorkspaceFileAccess(ServoyModel.getWorkspace()).getUTF8Contents(mdf.getFullPath().toString());
									}
									catch (IOException e)
									{
										ServoyLog.logError("Error while getting metadata file", e);
									}
									if (wscontents != null)
									{
										MetadataDef mdd = new MetadataDef(ds, wscontents);
										if (!metadataDefs.contains(mdd)) metadataDefs.add(mdd);
									}
								}
							}
						}
						catch (JSONException e)
						{
							e.printStackTrace();
						}
					}
				}
			}
			serverTableDefs.put(serverName, tableDefs);
		}

		// D. make use of tabledef info and metadata for the managers
		tableDefManager = new ITableDefinitionsManager()
		{
			private Map<String, List<TableDef>> serverTableDefsMap = new HashMap<String, List<TableDef>>();

			public void setServerTableDefs(Map<String, List<TableDef>> serverTableDefsMap)
			{
				this.serverTableDefsMap = serverTableDefsMap;
			}

			public Map<String, List<TableDef>> getServerTableDefs()
			{
				return this.serverTableDefsMap;
			}
		};
		tableDefManager.setServerTableDefs(serverTableDefs);

		metadataDefManager = new IMetadataDefManager()
		{
			private List<MetadataDef> metadataDefList = new ArrayList<MetadataDef>();

			public void setMetadataDefsList(List<MetadataDef> metadataDefList)
			{
				this.metadataDefList = metadataDefList;
			}

			public List<MetadataDef> getMetadataDefsList()
			{
				return this.metadataDefList;
			}
		};
		metadataDefManager.setMetadataDefsList(metadataDefs);
	}

	private void addServerTable(Map<String, List<String>> srvTbl, String serverName, String tableName)
	{
		List<String> tablesForServer = srvTbl.get(serverName);
		if (tablesForServer == null)
		{
			tablesForServer = new ArrayList<String>();
		}
		if (!tablesForServer.contains(tableName)) tablesForServer.add(tableName);
		srvTbl.put(serverName, tablesForServer);
	}

	private Map<String, List<String>> getNeededServerTables(Solution mainActiveSolution, boolean includeModules, boolean includeI18NData)
	{
		//get modules to export if needed, or just the active project
		List<Solution> exportedModules = new ArrayList<Solution>();
		if (includeModules)
		{
			ServoyProject[] modules = ServoyModelManager.getServoyModelManager().getServoyModel().getModulesOfActiveProject();
			for (ServoyProject module : modules)
			{
				exportedModules.add(module.getSolution());
			}
		}
		else exportedModules.add(mainActiveSolution);

		Map<String, List<String>> neededServersTablesMap = new HashMap<String, List<String>>();
		for (Solution solution : exportedModules)
		{
			Iterator<IPersist> it = solution.getAllObjects();
			while (it.hasNext())
			{
				IPersist object = it.next();
				int objectTypeId = object.getTypeID();
				if (objectTypeId == IRepository.FORMS)
				{
					Form form = ((Form)object);
					addServerTable(neededServersTablesMap, form.getServerName(), form.getTableName());
				}
				else if (objectTypeId == IRepository.RELATIONS)
				{
					Relation relation = ((Relation)object);
					addServerTable(neededServersTablesMap, relation.getPrimaryServerName(), relation.getPrimaryTableName());
					addServerTable(neededServersTablesMap, relation.getForeignServerName(), relation.getForeignTableName());
				}
				else if (objectTypeId == IRepository.VALUELISTS)
				{
					ValueList vl = ((ValueList)object);
					if (vl.getValueListType() == ValueList.DATABASE_VALUES && vl.getDatabaseValuesType() == ValueList.TABLE_VALUES)
					{
						addServerTable(neededServersTablesMap, vl.getServerName(), vl.getTableName());
					}
				}
			}
		}

		// check if i18n info is needed
		if (mainActiveSolution.getI18nDataSource() != null && includeI18NData) addServerTable(neededServersTablesMap, mainActiveSolution.getI18nServerName(),
			mainActiveSolution.getI18nTableName());

		return neededServersTablesMap;
	}

	public void init(IWorkbench workbench, IStructuredSelection selection)
	{
		ServoyProject activeProject = ServoyModelManager.getServoyModelManager().getServoyModel().getActiveProject();
		if (activeProject != null) activeSolution = activeProject.getSolution();

		exportModel = new ExportSolutionModel();
		String initialFileName = getDialogSettings().get("initialFileName");
		if (initialFileName == null)
		{
			ServoyModelManager.getServoyModelManager().getServoyModel();
			String dir = ServoyModel.getSettings().getProperty(J2DBGlobals.SERVOY_APPLICATION_SERVER_DIRECTORY_KEY);
			initialFileName = new File(dir, "solutions/" + activeSolution.getName() + ".servoy").getAbsolutePath(); //$NON-NLS-1$ //$NON-NLS-2$
		}
		else
		{
			// use the previous export directory, with the name of the active solution
			initialFileName = new File(new File(initialFileName).getParent(), activeSolution.getName() + ".servoy").getAbsolutePath(); //$NON-NLS-1$
		}
		exportModel.setFileName(initialFileName);
	}

	@Override
	public void addPages()
	{
		fileSelectionPage = new FileSelectionPage();
		addPage(fileSelectionPage);
		exportOptionsPage = new ExportOptionsPage();
		addPage(exportOptionsPage);
		modulesSelectionPage = new ModulesSelectionPage();
		addPage(modulesSelectionPage);
		passwordPage = new PasswordPage();
		addPage(passwordPage);
	}

	public void pageChanged(PageChangedEvent event)
	{
		if (event.getSelectedPage() == modulesSelectionPage)
		{
			modulesSelectionPage.checkStateChanged(null);
		}
	}

	@Override
	public boolean canFinish()
	{
		if (modulesSelectionPage.hasMarkers == Boolean.TRUE)
		{
			if (fileSelectionPage.dbDownErrors && fileSelectionPage.proceedWithExport) return true;
			return false;
		}
		if (this.getContainer().getCurrentPage() == fileSelectionPage) return false;
		if (exportModel.isExportReferencedModules() && this.getContainer().getCurrentPage() == exportOptionsPage) return false;
		return exportModel.canFinish();
	}

	private class FileSelectionPage extends WizardPage implements Listener
	{
		private Text fileNameText;
		private Button browseButton;
		private final Boolean hasMarkers;
		private boolean proceedWithExport = true;
		private boolean dbDownErrors = false;

		public FileSelectionPage()
		{
			super("page1"); //$NON-NLS-1$
			setTitle("Choose the destination file"); //$NON-NLS-1$
			setDescription("Select the file where you want your solution exported to"); //$NON-NLS-1$
			hasMarkers = hasMarkers(new String[] { ServoyModelManager.getServoyModelManager().getServoyModel().getActiveProject().getProject().getName() });
			if (hasMarkers == Boolean.TRUE)
			{
				if (hasDbErrorMarkers(new String[] { ServoyModelManager.getServoyModelManager().getServoyModel().getActiveProject().getProject().getName() }) == Boolean.TRUE)
				{
					dbDownErrors = true;
					Display.getDefault().syncExec(new Runnable()
					{
						public void run()
						{
							MessageDialog dlg = new MessageDialog(
								PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
								"Confirm solution export",
								null,
								"There are errors in the solution that will prevent it from functioning well; are you sure you want to proceed with the export?",
								MessageDialog.ERROR, new String[] { IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL }, 2);
							int result = dlg.open();
							if (result == 0) proceedWithExport = true;
							else proceedWithExport = false;
						}
					});
				}

				setErrorMessage("There are errors in the solution that will prevent it from functioning well. Solve errors from problems view first.");
			}
			if (hasMarkers == null)
			{
				setMessage(
					"There are warnings in the solution that may prevent it from functioning well. You may want to solve warnings from problems view first.", IMessageProvider.WARNING); //$NON-NLS-1$
			}
		}

		public void createControl(Composite parent)
		{
			GridLayout gridLayout = new GridLayout(2, false);
			Composite composite = new Composite(parent, SWT.NONE);
			composite.setLayout(gridLayout);

			fileNameText = new Text(composite, SWT.BORDER);
			fileNameText.addListener(SWT.KeyUp, this);
			GridData gd = new GridData(GridData.FILL_HORIZONTAL);
			fileNameText.setLayoutData(gd);
			if (exportModel.getFileName() != null) fileNameText.setText(exportModel.getFileName());

			browseButton = new Button(composite, SWT.PUSH);
			browseButton.setText("Browse..."); //$NON-NLS-1$
			browseButton.addListener(SWT.Selection, this);

			setControl(composite);
		}

		public void handleEvent(Event event)
		{
			if (event.widget == fileNameText)
			{
				String potentialFileName = fileNameText.getText();
				exportModel.setFileName(potentialFileName);
			}
			else if (event.widget == browseButton)
			{
				Shell shell = new Shell();
				GridLayout gridLayout = new GridLayout();
				shell.setLayout(gridLayout);
				FileDialog dlg = new FileDialog(shell, SWT.SAVE);
				if (exportModel.getFileName() != null)
				{
					File f = new File(exportModel.getFileName());
					if (f.isDirectory())
					{
						dlg.setFilterPath(f.getAbsolutePath());
						dlg.setFileName(null);
					}
					else
					{
						dlg.setFilterPath(f.getParent());
						dlg.setFileName(f.getName());
						String[] extensions = { "*.servoy" }; //$NON-NLS-1$
						dlg.setFilterExtensions(extensions);
					}
				}
				String chosenFileName = dlg.open();
				if (chosenFileName != null)
				{
					exportModel.setFileName(chosenFileName);
					fileNameText.setText(chosenFileName);
				}
			}
			getWizard().getContainer().updateButtons();
			getWizard().getContainer().updateMessage();
		}

		@Override
		public boolean canFlipToNextPage()
		{
			boolean result = true;
			if (hasMarkers == Boolean.TRUE)
			{
				if (!dbDownErrors) return false;
				else if (dbDownErrors && !proceedWithExport) result = false;
			}

			boolean messageSet = (hasMarkers == null);
			if (exportModel.getFileName() == null) return false;
			if (fileNameText.getText().length() == 0)
			{
				result = false;
			}
			else
			{
				File f = new File(exportModel.getFileName());
				if (f.exists())
				{
					if (f.isDirectory())
					{
						if (!messageSet) setMessage("Specified path points to an existing folder.", IMessageProvider.WARNING); //$NON-NLS-1$
						result = false;
						messageSet = true;
					}
					else
					{
						if (!messageSet) setMessage("Specified path points to an existing file.", IMessageProvider.INFORMATION); //$NON-NLS-1$
						messageSet = true;
					}
				}
			}
			if (!messageSet)
			{
				setMessage(null);
			}
			return result;
		}

		@Override
		public IWizardPage getNextPage()
		{
			if (exportModel.getFileName() == null) return null;

			File f = new File(exportModel.getFileName());
			if (f.exists())
			{
				if (!f.getAbsolutePath().equals(exportModel.getUserAcknowledgedFileToOverwrite()))
				{
					MessageBox msg = new MessageBox(this.getShell(), SWT.ICON_QUESTION | SWT.YES | SWT.NO | SWT.CANCEL);
					msg.setText("File already exists"); //$NON-NLS-1$
					msg.setMessage("The file you selected already exists on disk. Do you want to overwrite it?"); //$NON-NLS-1$
					if (msg.open() == SWT.YES)
					{
						exportModel.setUserAcknowledgedFileToOverwrite(f.getAbsolutePath());
						return exportOptionsPage;
					}
					else
					{
						return null;
					}
				}
				// User already acknowledged overwriting this file, and the file name was not
				// changed in the meantime.
				else
				{
					return exportOptionsPage;
				}
			}
			else
			{
				return exportOptionsPage;
			}
		}

	}

	private class ExportOptionsPage extends WizardPage implements Listener
	{
		private Button protectWithPasswordButton;
		private Button exportReferencedModulesButton;
		private Button exportAllTablesFromReferencedServers;
		private Button exportSampleDataButton;
		private Button exportMetadataTablesButton;
		private Button checkMetadataTablesButton;
		private Button exportI18NDataButton;
		private Button exportUsersButton;
		private Spinner nrOfExportedSampleDataSpinner;
		private Button rowsPerTableRadioButton;
		private Button allRowsRadioButton;

		public ExportOptionsPage()
		{
			super("page2"); //$NON-NLS-1$
			setTitle("Choose export options"); //$NON-NLS-1$
			setDescription("Specify the options for your export"); //$NON-NLS-1$
		}

		public void createControl(Composite parent)
		{
			GridLayout gridLayout = new GridLayout();
			Composite composite = new Composite(parent, SWT.NONE);
			composite.setLayout(gridLayout);

			protectWithPasswordButton = new Button(composite, SWT.CHECK);
			protectWithPasswordButton.setText("Protect solution with password"); //$NON-NLS-1$
			protectWithPasswordButton.setSelection(exportModel.isProtectWithPassword());
			protectWithPasswordButton.addListener(SWT.Selection, this);

			exportReferencedModulesButton = new Button(composite, SWT.CHECK);
			exportReferencedModulesButton.setText("Export referenced modules"); //$NON-NLS-1$
			exportReferencedModulesButton.setSelection(exportModel.isExportReferencedModules());
			exportReferencedModulesButton.addListener(SWT.Selection, this);

			exportAllTablesFromReferencedServers = new Button(composite, SWT.CHECK);
			exportAllTablesFromReferencedServers.setText("Export all tables from referenced servers"); //$NON-NLS-1$
			exportAllTablesFromReferencedServers.setSelection(exportModel.isExportAllTablesFromReferencedServers());
			exportAllTablesFromReferencedServers.addListener(SWT.Selection, this);

			exportMetadataTablesButton = new Button(composite, SWT.CHECK);
			exportMetadataTablesButton.setText("Export metadata tables."); //$NON-NLS-1$ 
			exportMetadataTablesButton.setSelection(exportModel.isExportMetaData());
			exportMetadataTablesButton.addListener(SWT.Selection, this);

			checkMetadataTablesButton = new Button(composite, SWT.CHECK);
			checkMetadataTablesButton.setText("Check metadata tables."); //$NON-NLS-1$ 
			checkMetadataTablesButton.setSelection(exportModel.isCheckMetadataTables());
			checkMetadataTablesButton.addListener(SWT.Selection, this);

			exportSampleDataButton = new Button(composite, SWT.CHECK);
			exportSampleDataButton.setText("Export solution sample data."); //$NON-NLS-1$ 
			exportSampleDataButton.setSelection(exportModel.isExportSampleData());
			exportSampleDataButton.addListener(SWT.Selection, this);

			Composite horizontalComposite = new Composite(composite, SWT.None);
			GridLayout hcGridLayout = new GridLayout();
			hcGridLayout.numColumns = 4;
			hcGridLayout.marginHeight = 0;
			hcGridLayout.marginWidth = 0;
			horizontalComposite.setLayout(hcGridLayout);

			GridData data1 = new GridData();
			Button emptyCBButton = new Button(horizontalComposite, SWT.CHECK);
			emptyCBButton.setVisible(false);
			emptyCBButton.setLayoutData(data1);

			GridData data2 = new GridData();
			rowsPerTableRadioButton = new Button(horizontalComposite, SWT.RADIO);
			rowsPerTableRadioButton.setEnabled(false);
			rowsPerTableRadioButton.setLayoutData(data2);
			rowsPerTableRadioButton.addListener(SWT.Selection, this);

			GridData data3 = new GridData();
			Label textLabel = new Label(horizontalComposite, SWT.NONE);
			textLabel.setText("Rows per table: ");
			textLabel.setLayoutData(data3);

			GridData data4 = new GridData();
			nrOfExportedSampleDataSpinner = new Spinner(horizontalComposite, SWT.BORDER);
			nrOfExportedSampleDataSpinner.setMinimum(1);
			nrOfExportedSampleDataSpinner.setMaximum(IDataServerInternal.MAX_ROWS_TO_RETRIEVE);
			nrOfExportedSampleDataSpinner.setSelection(10000);
			nrOfExportedSampleDataSpinner.setIncrement(100);
			nrOfExportedSampleDataSpinner.setEnabled(false);

			nrOfExportedSampleDataSpinner.setLayoutData(data4);

			nrOfExportedSampleDataSpinner.addModifyListener(new ModifyListener()
			{

				public void modifyText(ModifyEvent e)
				{
					int maxRowToRetrieve = nrOfExportedSampleDataSpinner.getSelection();
					if (maxRowToRetrieve == 0)
					{
						maxRowToRetrieve = IDataServerInternal.MAX_ROWS_TO_RETRIEVE;
					}
					exportModel.setNumberOfSampleDataExported(maxRowToRetrieve);
				}
			});

			GridData data5 = new GridData();
			Button emptyCBButton2 = new Button(horizontalComposite, SWT.CHECK);
			emptyCBButton2.setVisible(false);
			emptyCBButton2.setLayoutData(data5);

			GridData data6 = new GridData();
			allRowsRadioButton = new Button(horizontalComposite, SWT.RADIO);
			allRowsRadioButton.setEnabled(false);
			allRowsRadioButton.setLayoutData(data6);
			allRowsRadioButton.addListener(SWT.Selection, this);


			GridData data7 = new GridData();
			Label textLabel4 = new Label(horizontalComposite, SWT.NONE);
			textLabel4.setText("All rows.");
			textLabel4.setLayoutData(data7);

			exportI18NDataButton = new Button(composite, SWT.CHECK);
			exportI18NDataButton.setText("Export i18n data"); //$NON-NLS-1$
			exportI18NDataButton.setSelection(exportModel.isExportI18NData());
			exportI18NDataButton.addListener(SWT.Selection, this);

			exportUsersButton = new Button(composite, SWT.CHECK);
			exportUsersButton.setText("Export users"); //$NON-NLS-1$
			exportUsersButton.setSelection(exportModel.isExportUsers());
			exportUsersButton.addListener(SWT.Selection, this);

			setControl(composite);
		}

		public void handleEvent(Event event)
		{
			if (event.widget == protectWithPasswordButton) exportModel.setProtectWithPassword(protectWithPasswordButton.getSelection());
			else if (event.widget == exportReferencedModulesButton) exportModel.setExportReferencedModules(exportReferencedModulesButton.getSelection());
			else if (event.widget == checkMetadataTablesButton) exportModel.setCheckMetadataTables(checkMetadataTablesButton.getSelection());
			else if (event.widget == exportMetadataTablesButton)
			{
				exportModel.setExportMetaData(exportMetadataTablesButton.getSelection());
				checkMetadataTablesButton.setEnabled(exportMetadataTablesButton.getSelection());
			}
			else if (event.widget == exportSampleDataButton)
			{
				exportModel.setExportSampleData(exportSampleDataButton.getSelection());
				exportModel.setNumberOfSampleDataExported(10000);

				nrOfExportedSampleDataSpinner.setEnabled(exportSampleDataButton.getSelection());
				allRowsRadioButton.setEnabled(exportSampleDataButton.getSelection());
				allRowsRadioButton.setSelection(false);
				rowsPerTableRadioButton.setEnabled(exportSampleDataButton.getSelection());
				rowsPerTableRadioButton.setSelection(exportSampleDataButton.getSelection());
			}
			else if (event.widget == allRowsRadioButton)
			{
				nrOfExportedSampleDataSpinner.setSelection(IDataServerInternal.MAX_ROWS_TO_RETRIEVE);
				nrOfExportedSampleDataSpinner.setEnabled(!allRowsRadioButton.getSelection());

				rowsPerTableRadioButton.setSelection(!allRowsRadioButton.getSelection());

				exportModel.setExportSampleData(exportSampleDataButton.getSelection());
				exportModel.setNumberOfSampleDataExported(IDataServerInternal.MAX_ROWS_TO_RETRIEVE);
			}
			else if (event.widget == rowsPerTableRadioButton)
			{
				allRowsRadioButton.setSelection(!rowsPerTableRadioButton.getSelection());
				nrOfExportedSampleDataSpinner.setSelection(10000);
			}
			else if (event.widget == exportI18NDataButton) exportModel.setExportI18NData(exportI18NDataButton.getSelection());
			else if (event.widget == exportUsersButton) exportModel.setExportUsers(exportUsersButton.getSelection());
			else if (event.widget == exportAllTablesFromReferencedServers) exportModel.setExportAllTablesFromReferencedServers(exportAllTablesFromReferencedServers.getSelection());
			getWizard().getContainer().updateButtons();
		}

		@Override
		public IWizardPage getNextPage()
		{
			if (exportModel.isExportReferencedModules()) return modulesSelectionPage;
			else if (exportModel.isProtectWithPassword()) return passwordPage;
			else return null;
		}
	}

	private class ModulesSelectionPage extends WizardPage implements ICheckStateListener
	{
		CheckboxTreeViewer treeViewer;
		public Boolean hasMarkers = Boolean.FALSE;

		protected ModulesSelectionPage()
		{
			super("page3"); //$NON-NLS-1$
			setTitle("Choose modules to export"); //$NON-NLS-1$
			setDescription("Select additional modules that you want to have exported too"); //$NON-NLS-1$
		}

		public void createControl(Composite parent)
		{
			GridLayout gridLayout = new GridLayout();
			Composite composite = new Composite(parent, SWT.NONE);
			composite.setLayout(gridLayout);

			String[] moduleNames = null;
			try
			{
				Map<String, Solution> modules = new HashMap<String, Solution>();
				activeSolution.getReferencedModulesRecursive(modules);
				if (modules.containsKey(activeSolution.getName())) modules.remove(activeSolution.getName());
				moduleNames = modules.keySet().toArray(new String[modules.keySet().size()]);
			}
			catch (Exception e)
			{
				Debug.error("Failed to retrieve referenced modules for solution.", e); //$NON-NLS-1$
			}
			Arrays.sort(moduleNames);
			final String[] moduleNamesFinal = moduleNames;

			treeViewer = new CheckboxTreeViewer(composite);
			treeViewer.getTree().setLayoutData(new GridData(GridData.FILL_BOTH));
			treeViewer.setContentProvider(new ITreeContentProvider()
			{
				public Object[] getChildren(Object parentElement)
				{
					return null;
				}

				public Object getParent(Object element)
				{
					return null;
				}

				public boolean hasChildren(Object element)
				{
					return false;
				}

				public Object[] getElements(Object inputElement)
				{
					return moduleNamesFinal;
				}

				public void dispose()
				{
				}

				public void inputChanged(Viewer viewer, Object oldInput, Object newInput)
				{
				}
			});
			treeViewer.setInput(moduleNames);
			treeViewer.setCheckedElements(moduleNames);

			treeViewer.addCheckStateListener(this);
			setControl(composite);
		}

		public void checkStateChanged(CheckStateChangedEvent event)
		{
			initializeModulesToExport();
			hasMarkers = hasMarkers(exportModel.getModulesToExport());
			setErrorMessage(null);
			if (hasMarkers == Boolean.TRUE)
			{
				setErrorMessage("There are errors in the solution that will prevent it from functioning well. Solve errors from problems view first.");
			}
			if (hasMarkers == null)
			{
				setMessage(
					"There are warnings in the solution that may prevent it from functioning well. You may want to solve warnings from problems view first.", IMessageProvider.WARNING); //$NON-NLS-1$
			}
			if (isCurrentPage()) getWizard().getContainer().updateButtons();
		}

		@Override
		public IWizardPage getNextPage()
		{
			if (exportModel.isProtectWithPassword()) return passwordPage;
			else return null;
		}

		protected void initializeModulesToExport()
		{
			Object[] currentSelection = treeViewer.getCheckedElements();
			if (currentSelection.length > 0)
			{
				String[] moduleNames = new String[currentSelection.length];
				for (int i = 0; i < currentSelection.length; i++)
					moduleNames[i] = ((String)currentSelection[i]);
				exportModel.setModulesToExport(moduleNames);
			}
			else exportModel.setModulesToExport(null);
		}

		@Override
		public boolean canFlipToNextPage()
		{
			return (hasMarkers != Boolean.TRUE) && super.canFlipToNextPage();
		}
	}

	private class PasswordPage extends WizardPage implements Listener
	{
		Text passwordText;

		public PasswordPage()
		{
			super("page4"); //$NON-NLS-1$
			setTitle("Choose a password"); //$NON-NLS-1$
			setDescription("Provide the password that will be used to protect the exported solution"); //$NON-NLS-1$
		}

		public void createControl(Composite parent)
		{
			GridLayout gridLayout = new GridLayout();
			Composite composite = new Composite(parent, SWT.NONE);
			composite.setLayout(gridLayout);

			// On MacOS, SWT 3.5 does not send events to listeners on password fields.
			// See: http://www.eclipse.org/forums/index.php?t=msg&goto=508058&
			int style = SWT.BORDER;
			if (!Utils.isAppleMacOS()) style |= SWT.PASSWORD;
			passwordText = new Text(composite, style);
			if (Utils.isAppleMacOS()) passwordText.setEchoChar('\u2022');
			GridData gd = new GridData(GridData.FILL_HORIZONTAL);
			passwordText.setLayoutData(gd);
			passwordText.addListener(SWT.KeyUp, this);

			setControl(composite);
		}

		public void handleEvent(Event event)
		{
			if (event.widget == passwordText) exportModel.setPassword(passwordText.getText());
			getWizard().getContainer().updateButtons();
		}

		@Override
		public IWizardPage getNextPage()
		{
			return null;
		}
	}
}
