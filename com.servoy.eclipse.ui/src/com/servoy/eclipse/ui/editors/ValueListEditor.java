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
package com.servoy.eclipse.ui.editors;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.UpdateValueStrategy;
import org.eclipse.core.databinding.beans.PojoObservables;
import org.eclipse.core.databinding.conversion.Converter;
import org.eclipse.core.databinding.observable.ChangeEvent;
import org.eclipse.core.databinding.observable.IChangeListener;
import org.eclipse.core.databinding.observable.value.AbstractObservableValue;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.databinding.swt.SWTObservables;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.AcceptAllFilter;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.grouplayout.GroupLayout;
import org.eclipse.swt.layout.grouplayout.LayoutStyle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IEditorPart;

import com.servoy.eclipse.core.ServoyModel;
import com.servoy.eclipse.core.ServoyModelManager;
import com.servoy.eclipse.core.util.DatabaseUtils;
import com.servoy.eclipse.model.builder.ServoyBuilder;
import com.servoy.eclipse.model.builder.ServoyBuilder.Problem;
import com.servoy.eclipse.model.util.ModelUtils;
import com.servoy.eclipse.model.util.ServoyLog;
import com.servoy.eclipse.model.util.TableWrapper;
import com.servoy.eclipse.ui.dialogs.FlatTreeContentProvider;
import com.servoy.eclipse.ui.dialogs.MethodDialog;
import com.servoy.eclipse.ui.dialogs.MethodDialog.MethodListOptions;
import com.servoy.eclipse.ui.dialogs.RelationContentProvider;
import com.servoy.eclipse.ui.dialogs.RelationContentProvider.RelationsWrapper;
import com.servoy.eclipse.ui.dialogs.SortDialog;
import com.servoy.eclipse.ui.dialogs.TableContentProvider;
import com.servoy.eclipse.ui.dialogs.TableContentProvider.TableListOptions;
import com.servoy.eclipse.ui.editors.valuelist.ValueListDPSelectionComposite;
import com.servoy.eclipse.ui.labelproviders.DatasourceLabelProvider;
import com.servoy.eclipse.ui.labelproviders.MethodLabelProvider;
import com.servoy.eclipse.ui.labelproviders.RelationLabelProvider;
import com.servoy.eclipse.ui.labelproviders.SolutionContextDelegateLabelProvider;
import com.servoy.eclipse.ui.labelproviders.ValuelistLabelProvider;
import com.servoy.eclipse.ui.property.MethodPropertyController.MethodValueEditor;
import com.servoy.eclipse.ui.property.MethodWithArguments;
import com.servoy.eclipse.ui.property.PersistContext;
import com.servoy.eclipse.ui.property.RelationPropertyController;
import com.servoy.eclipse.ui.property.TableValueEditor;
import com.servoy.eclipse.ui.property.ValuelistPropertyController;
import com.servoy.eclipse.ui.util.BindingHelper;
import com.servoy.eclipse.ui.util.DocumentValidatorVerifyListener;
import com.servoy.eclipse.ui.util.IControlFactory;
import com.servoy.eclipse.ui.util.IStatusChangedListener;
import com.servoy.eclipse.ui.views.TreeSelectObservableValue;
import com.servoy.eclipse.ui.views.TreeSelectViewer;
import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.persistence.IPersist;
import com.servoy.j2db.persistence.IServer;
import com.servoy.j2db.persistence.IValidateName;
import com.servoy.j2db.persistence.Relation;
import com.servoy.j2db.persistence.RepositoryException;
import com.servoy.j2db.persistence.ScriptMethod;
import com.servoy.j2db.persistence.ScriptVariable;
import com.servoy.j2db.persistence.Table;
import com.servoy.j2db.persistence.ValueList;
import com.servoy.j2db.util.DataSourceUtils;

/**
 * @author jcompagner
 * 
 */
public class ValueListEditor extends PersistEditor
{
	private DataBindingContext m_bindingContext;
	private Text separator_char;
	private ValueListDPSelectionComposite dp_select1;
	private ValueListDPSelectionComposite dp_select2;
	private ValueListDPSelectionComposite dp_select3;

	private TreeSelectViewer relationSelect;
	private TreeSelectViewer tableSelect;

	private TreeSelectViewer globalMethodSelect;
	private Button globalMethodValuesButton;
	private SelectionAdapter globalMethodValuesSelectionListener;
	private ISelectionChangedListener globalMethodSelectionListener;

	private Button customValuesButton;
	private Button tableValuesButton;

	private Text customValues;
	private Text nameField;

	private Button applyValuelistNameButton;
	private Button relatedValuesButton;

	private Group definitionGroup;
	private Label separatorCharacterLabel;

	private Button allowEmptyValueButton;
	private TreeSelectViewer sortingDefinitionSelect;

	private Table currentTable;
	private TreeSelectViewer fallbackValuelist;

	private int databaseValuesTypeOverride = -1; // used when the user clicks the related values button but realtionName has not been set yet

	private SelectionAdapter customvalueButtonSelectionListener;
	private SelectionAdapter tableValuesButtonSelectionListener;
	private SelectionAdapter relatedValuesButtoneSelectionListener;
	private ISelectionChangedListener tableSelectionListener;
	private ISelectionChangedListener relationSelectionListener;
	private IStatusChangedListener statusChangeListener;


	@Override
	protected boolean validatePersist(IPersist persist)
	{
		return persist instanceof ValueList;
	}

	public ValueList getValueList()
	{
		return (ValueList)getPersist();
	}

	/**
	 * @see org.eclipse.ui.part.WorkbenchPart#createPartControl(org.eclipse.swt.widgets.Composite)
	 */
	@Override
	public void createPartControl(Composite parent)
	{
		FlattenedSolution editingFlattenedSolution = ModelUtils.getEditingFlattenedSolution(getPersist());

		ScrolledComposite myScrolledComposite = new ScrolledComposite(parent, SWT.H_SCROLL | SWT.V_SCROLL);
		myScrolledComposite.setExpandHorizontal(true);
		myScrolledComposite.setExpandVertical(true);

		Composite comp = new Composite(myScrolledComposite, SWT.NONE);
		Label nameLabel = new Label(comp, SWT.NONE);
		nameLabel.setText("Valuelist Name"); //$NON-NLS-1$

		myScrolledComposite.setContent(comp);

		nameField = new Text(comp, SWT.BORDER);
		nameField.addVerifyListener(DocumentValidatorVerifyListener.IDENT_SERVOY_VERIFIER);

		customValues = new Text(comp, SWT.MULTI | SWT.BORDER | SWT.WRAP | SWT.V_SCROLL);
		customValues.setToolTipText("list with fixed displayValue[|realValue] ('^' for null real value, %%globals.NAME%% for global real value)"); //$NON-NLS-1$
		customValuesButton = new Button(comp, SWT.RADIO);
		customValuesButton.setText("Custom Values"); //$NON-NLS-1$
		customvalueButtonSelectionListener = new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				if (customValuesButton.getSelection())
				{
					handleCustomValuesButtonSelected();
					flagModified();
					refresh();
				}
			}
		};

		tableValuesButton = new Button(comp, SWT.RADIO);
		tableValuesButton.setText("Table Values"); //$NON-NLS-1$
		tableValuesButtonSelectionListener = new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				if (tableValuesButton.getSelection())
				{
					handleDatabaseValuesButtonSelected();
					flagModified();
					refresh();
				}
			}
		};

		tableSelect = new TreeSelectViewer(comp, SWT.NONE, TableValueEditor.INSTANCE);
		tableSelect.setContentProvider(new TableContentProvider());
		tableSelect.setLabelProvider(DatasourceLabelProvider.INSTANCE_IMAGE_NAMEONLY);
		tableSelect.setTextLabelProvider(new DatasourceLabelProvider("", false, true));
		tableSelect.setTitleText("Select table"); //$NON-NLS-1$
		tableSelectionListener = new ISelectionChangedListener()
		{
			public void selectionChanged(SelectionChangedEvent event)
			{
				IStructuredSelection selection = (IStructuredSelection)tableSelect.getSelection();
				handleTableSelected(selection.isEmpty() ? null : (TableWrapper)selection.getFirstElement());
				flagModified();
				refresh();
			}
		};
		tableSelect.setInput(new TableContentProvider.TableListOptions(TableListOptions.TableListType.ALL, false));
		tableSelect.setEditable(true);
		Control tableSelectControl = tableSelect.getControl();

		applyValuelistNameButton = new Button(comp, SWT.CHECK);
		applyValuelistNameButton.setText("Apply valuelist name as filter on column 'valuelist_name'"); //$NON-NLS-1$

		relatedValuesButton = new Button(comp, SWT.RADIO);
		relatedValuesButton.setText("Related Values"); //$NON-NLS-1$
		relatedValuesButtoneSelectionListener = new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				if (relatedValuesButton.getSelection())
				{
					handleRelatedValuesButtonSelected();
					flagModified();
					refresh();
				}
			}
		};

		relationSelect = new TreeSelectViewer(comp, SWT.NONE, RelationPropertyController.RelationValueEditor.INSTANCE);
		relationSelect.setContentProvider(new RelationContentProvider(editingFlattenedSolution));
		relationSelect.setLabelProvider(RelationLabelProvider.INSTANCE_LAST_NAME_ONLY);
		relationSelect.setTextLabelProvider(new RelationLabelProvider("", false, false));
		relationSelect.setSelectionFilter(AcceptAllFilter.getInstance()); // by default only leaf nodes can be selected
		relationSelect.setTitleText("Select relation"); //$NON-NLS-1$
		relationSelect.setEditable(true);
		relationSelectionListener = new ISelectionChangedListener()
		{
			public void selectionChanged(SelectionChangedEvent event)
			{
				IStructuredSelection selection = (IStructuredSelection)relationSelect.getSelection();
				handleRelationSelected(selection.isEmpty() ? null : (RelationsWrapper)selection.getFirstElement());
				flagModified();
				refresh();
			}
		};
		relationSelect.setInput(new RelationContentProvider.RelationListOptions(null, null, false, true));
		Control relationSelectControl = relationSelect.getControl();

		globalMethodValuesButton = new Button(comp, SWT.RADIO);
		globalMethodValuesButton.setText("Global method"); //$NON-NLS-1$
		globalMethodValuesSelectionListener = new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				if (globalMethodValuesButton.getSelection())
				{
					handleGlobalMethodButtonSelected();
					flagModified();
					refresh();
				}
			}
		};

		globalMethodSelect = new TreeSelectViewer(comp, SWT.NONE, new MethodValueEditor(PersistContext.create(getValueList(), null)))
		{
			@Override
			public IStructuredSelection openDialogBox(Control cellEditorWindow)
			{
				final MethodDialog dialog = new MethodDialog(cellEditorWindow.getShell(), (ILabelProvider)getLabelProvider(), getContentProvider(),
					getSelection(), getInput(), SWT.NONE, "Select Method", null); //$NON-NLS-1$
				dialog.setOptionsAreaFactory(new IControlFactory()
				{
					public Control createControl(Composite composite)
					{
						AddMethodButtonsComposite buttons = new AddMethodButtonsComposite(composite, SWT.NONE);
						buttons.setContext(getValueList(), "valueListGlobalMethod"); //$NON-NLS-1$
						buttons.setDialog(dialog);
						return buttons;
					}
				});
				dialog.open();

				if (dialog.getReturnCode() == Window.CANCEL)
				{
					return null;
				}
				return (IStructuredSelection)dialog.getSelection(); // single select
			}
		};
		globalMethodSelect.setContentProvider(new GlobalMethodListContentProvider(getValueList()));
		globalMethodSelect.setLabelProvider(new SolutionContextDelegateLabelProvider(new MethodLabelProvider(PersistContext.create(getValueList(), null),
			false, false), getValueList()));
		globalMethodSelect.setInput(new MethodListOptions(false, false, false, true));
		globalMethodSelect.setEditable(true);
		Control globalMethodSelectControl = globalMethodSelect.getControl();

		globalMethodSelectionListener = new ISelectionChangedListener()
		{
			public void selectionChanged(SelectionChangedEvent event)
			{
				IStructuredSelection selection = (IStructuredSelection)globalMethodSelect.getSelection();
				handleGlobalMethodSelected(selection.isEmpty() ? null : (MethodWithArguments)selection.getFirstElement());
				flagModified();
				refresh();
			}
		};

		definitionGroup = new Group(comp, SWT.NONE);
		definitionGroup.setText("Definition"); //$NON-NLS-1$
		allowEmptyValueButton = new Button(comp, SWT.CHECK);
		allowEmptyValueButton.setText("Allow empty value"); //$NON-NLS-1$

		dp_select1 = new ValueListDPSelectionComposite(definitionGroup, editingFlattenedSolution, SWT.NONE);
		dp_select2 = new ValueListDPSelectionComposite(definitionGroup, editingFlattenedSolution, SWT.NONE);
		dp_select3 = new ValueListDPSelectionComposite(definitionGroup, editingFlattenedSolution, SWT.NONE);

		separatorCharacterLabel = new Label(definitionGroup, SWT.NONE);
		separatorCharacterLabel.setText("Separator character"); //$NON-NLS-1$
		separator_char = new Text(definitionGroup, SWT.BORDER);

		sortingDefinitionSelect = new TreeSelectViewer(comp, SWT.NONE, 150)
		{
			@Override
			protected IStructuredSelection openDialogBox(Control control)
			{
				IStructuredSelection selection = (IStructuredSelection)getSelection();
				String sortOptions = (String)(selection.isEmpty() ? null : selection.getFirstElement());
				if (currentTable != null)
				{
					FlattenedSolution flattenedSolution = ModelUtils.getEditingFlattenedSolution(getPersist());

					SortDialog dialog = new SortDialog(control.getShell(), flattenedSolution, currentTable, sortOptions, "Sort options"); //$NON-NLS-1$
					dialog.open();

					if (dialog.getReturnCode() != Window.CANCEL)
					{
						sortOptions = dialog.getValue().toString();
					}
				}

				return sortOptions == null || sortOptions.length() == 0 ? StructuredSelection.EMPTY : new StructuredSelection(sortOptions);
			}
		};
		sortingDefinitionSelect.setButtonText("Sorting Definition..."); //$NON-NLS-1$
		Control sortingDefinitionControl = sortingDefinitionSelect.getControl();

		fallbackValuelist = new TreeSelectViewer(comp, SWT.NONE, new ValuelistPropertyController.ValueListValueEditor(editingFlattenedSolution));
		fallbackValuelist.setButtonText("Fallback Valuelist"); //$NON-NLS-1$
		fallbackValuelist.setContentProvider(new FallbackValuelistContentProvider(editingFlattenedSolution, getValueList()));
		fallbackValuelist.setLabelProvider(new ValuelistLabelProvider(editingFlattenedSolution));
		fallbackValuelist.setTitleText("Select fallback valuelist"); //$NON-NLS-1$
		fallbackValuelist.setInput(getValueList());
		fallbackValuelist.setEditable(true);

		Control fallbackValueListControl = fallbackValuelist.getControl();

		statusChangeListener = new IStatusChangedListener()
		{
			public void statusChanged(boolean valid)
			{
				flagModified();
			}
		};

		final GroupLayout groupLayout_1 = new GroupLayout(definitionGroup);
		groupLayout_1.setHorizontalGroup(groupLayout_1.createParallelGroup(GroupLayout.LEADING).add(
			groupLayout_1.createSequentialGroup().add(
				groupLayout_1.createParallelGroup(GroupLayout.LEADING).add(
					groupLayout_1.createSequentialGroup().addContainerGap().add(dp_select1, GroupLayout.PREFERRED_SIZE, 190, Short.MAX_VALUE).addPreferredGap(
						LayoutStyle.RELATED).add(dp_select2, GroupLayout.PREFERRED_SIZE, 191, Short.MAX_VALUE).addPreferredGap(LayoutStyle.RELATED).add(
						dp_select3, GroupLayout.PREFERRED_SIZE, 192, Short.MAX_VALUE)).add(
					groupLayout_1.createSequentialGroup().add(8, 8, 8).add(separatorCharacterLabel).addPreferredGap(LayoutStyle.RELATED).add(separator_char,
						GroupLayout.PREFERRED_SIZE, 50, GroupLayout.PREFERRED_SIZE))).addContainerGap()));
		groupLayout_1.setVerticalGroup(groupLayout_1.createParallelGroup(GroupLayout.LEADING).add(
			groupLayout_1.createSequentialGroup().addContainerGap().add(
				groupLayout_1.createParallelGroup(GroupLayout.LEADING).add(dp_select1, GroupLayout.PREFERRED_SIZE, 149, Short.MAX_VALUE).add(dp_select3,
					GroupLayout.PREFERRED_SIZE, 149, Short.MAX_VALUE).add(dp_select2, GroupLayout.PREFERRED_SIZE, 149, Short.MAX_VALUE)).addPreferredGap(
				LayoutStyle.RELATED).add(
				groupLayout_1.createParallelGroup(GroupLayout.BASELINE).add(separator_char, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
					GroupLayout.PREFERRED_SIZE).add(separatorCharacterLabel)).addContainerGap()));
		definitionGroup.setLayout(groupLayout_1);

		final GroupLayout groupLayout = new GroupLayout(comp);
		groupLayout.setHorizontalGroup(groupLayout.createParallelGroup(GroupLayout.LEADING).add(
			groupLayout.createSequentialGroup().add(
				groupLayout.createParallelGroup(GroupLayout.LEADING).add(
					groupLayout.createSequentialGroup().addContainerGap().add(
						groupLayout.createParallelGroup(GroupLayout.LEADING).add(
							groupLayout.createSequentialGroup().add(nameLabel).addPreferredGap(LayoutStyle.RELATED).add(nameField, 100, 200, Short.MAX_VALUE)).add(
							customValuesButton)).addPreferredGap(LayoutStyle.RELATED)).add(
					groupLayout.createSequentialGroup().add(19, 19, 19).add(customValues, 100, 200, Short.MAX_VALUE))).add(9, 9, 9)).add(
			groupLayout.createSequentialGroup().addContainerGap().add(fallbackValueListControl, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE,
				Short.MAX_VALUE).addPreferredGap(LayoutStyle.RELATED).add(40, 40, 40).add(allowEmptyValueButton, GroupLayout.PREFERRED_SIZE,
				GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE).add(40, 40, 40).addPreferredGap(LayoutStyle.RELATED).add(sortingDefinitionControl,
				GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE).addContainerGap()).add(
			groupLayout.createSequentialGroup().add(36, 36, 36).add(applyValuelistNameButton).addContainerGap()).add(
			groupLayout.createSequentialGroup().addContainerGap().add(
				groupLayout.createParallelGroup(GroupLayout.LEADING).add(
					groupLayout.createSequentialGroup().add(relatedValuesButton, GroupLayout.PREFERRED_SIZE, 134, GroupLayout.PREFERRED_SIZE).addPreferredGap(
						LayoutStyle.RELATED).add(relationSelectControl, GroupLayout.DEFAULT_SIZE, 200, Short.MAX_VALUE)).add(
					groupLayout.createSequentialGroup().add(globalMethodValuesButton, GroupLayout.PREFERRED_SIZE, 134, GroupLayout.PREFERRED_SIZE).addPreferredGap(
						LayoutStyle.RELATED).add(globalMethodSelectControl, GroupLayout.DEFAULT_SIZE, 200, Short.MAX_VALUE)).add(
					groupLayout.createSequentialGroup().add(tableValuesButton, GroupLayout.PREFERRED_SIZE, 134, GroupLayout.PREFERRED_SIZE).addPreferredGap(
						LayoutStyle.RELATED).add(tableSelectControl, GroupLayout.DEFAULT_SIZE, 200, Short.MAX_VALUE))).add(0, 200, 318)).add(
			groupLayout.createSequentialGroup().addContainerGap().add(definitionGroup, 100, 200, Short.MAX_VALUE).addContainerGap()));
		groupLayout.setVerticalGroup(groupLayout.createParallelGroup(GroupLayout.LEADING).add(
			groupLayout.createSequentialGroup().addContainerGap().add(
				groupLayout.createParallelGroup(GroupLayout.BASELINE).add(nameLabel).add(nameField, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
					GroupLayout.PREFERRED_SIZE)).addPreferredGap(LayoutStyle.RELATED).add(customValuesButton).addPreferredGap(LayoutStyle.RELATED).add(
				customValues, 50, 100, 400).addPreferredGap(LayoutStyle.RELATED).add(
				groupLayout.createParallelGroup(GroupLayout.LEADING).add(globalMethodValuesButton).add(globalMethodSelectControl)).addPreferredGap(
				LayoutStyle.RELATED).add(groupLayout.createParallelGroup(GroupLayout.TRAILING).add(tableValuesButton).add(tableSelectControl)).addPreferredGap(
				LayoutStyle.RELATED).add(applyValuelistNameButton).addPreferredGap(LayoutStyle.RELATED).add(
				groupLayout.createParallelGroup(GroupLayout.LEADING).add(relatedValuesButton).add(relationSelectControl)).addPreferredGap(LayoutStyle.RELATED).add(
				definitionGroup, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE).add(9, 9, 9).add(
				groupLayout.createParallelGroup(GroupLayout.LEADING).add(groupLayout.createParallelGroup(GroupLayout.BASELINE).add(fallbackValueListControl)).add(
					groupLayout.createParallelGroup(GroupLayout.BASELINE).add(sortingDefinitionControl).add(allowEmptyValueButton))).add(24, 24, 24)));
		comp.setLayout(groupLayout);

		myScrolledComposite.setMinSize(comp.computeSize(SWT.DEFAULT, SWT.DEFAULT));

		initDataBindings();

		// mark dirty if invalid - so that user is hinted to save it as a valid valuelist
		String message = checkValidState();
		if (message == null)
		{
			ServoyModel servoyModel = ServoyModelManager.getServoyModelManager().getServoyModel();
			List<Problem> problems = ServoyBuilder.checkValuelist(getValueList(), servoyModel.getFlattenedSolution(), ServoyModel.getServerManager(), false);
			for (Problem problem : problems)
			{
				if (problem.fix != null)
				{
					message = problem.message;
					break;
				}
			}
		}
		if (message != null)
		{
			MessageDialog.openWarning(getSite().getShell(), "Invalid valuelist", message); //$NON-NLS-1$
			getValueList().flagChanged();
			flagModified();
		}

		doRefresh();
	}

	@Override
	protected void doRefresh()
	{
		removeListeners();

		ValueList valueList = getValueList();
		try
		{
			int databaseValuesType;
			if (databaseValuesTypeOverride == -1)
			{
				databaseValuesType = valueList.getDatabaseValuesType();
			}
			else
			{
				databaseValuesType = databaseValuesTypeOverride;
				databaseValuesTypeOverride = -1;
			}
			customValuesButton.setSelection(valueList.getValueListType() == ValueList.CUSTOM_VALUES);
			customValues.setEnabled(valueList.getValueListType() == ValueList.CUSTOM_VALUES);

			globalMethodValuesButton.setSelection(valueList.getValueListType() == ValueList.GLOBAL_METHOD_VALUES);

			tableValuesButton.setSelection(valueList.getValueListType() == ValueList.DATABASE_VALUES && databaseValuesType == ValueList.TABLE_VALUES);
			applyValuelistNameButton.setEnabled(tableValuesButton.getSelection());
			relatedValuesButton.setSelection(valueList.getValueListType() == ValueList.DATABASE_VALUES && databaseValuesType == ValueList.RELATED_VALUES);

			FlattenedSolution flattenedSolution = ModelUtils.getEditingFlattenedSolution(getPersist());
			Table table = null;
			if (valueList.getValueListType() == ValueList.DATABASE_VALUES && databaseValuesType == ValueList.TABLE_VALUES)
			{
				String[] stn = DataSourceUtils.getDBServernameTablename(valueList.getDataSource());
				if (stn == null)
				{
					tableSelect.setSelection(StructuredSelection.EMPTY);
				}
				else
				{
					tableSelect.setSelection(new StructuredSelection(new TableWrapper(stn[0], stn[1])));
					IServer server = flattenedSolution.getSolution().getServer(stn[0]);
					if (server != null) table = (Table)server.getTable(stn[1]);
				}
			}
			else
			{
				tableSelect.setSelection(StructuredSelection.EMPTY);
			}

			if (valueList.getValueListType() == ValueList.DATABASE_VALUES && databaseValuesType == ValueList.RELATED_VALUES)
			{
				Relation[] relations = flattenedSolution.getRelationSequence(valueList.getRelationName());
				if (relations == null)
				{
					relationSelect.setSelection(StructuredSelection.EMPTY);
				}
				else
				{
					relationSelect.setSelection(new StructuredSelection(new RelationsWrapper(relations)));
					table = relations[relations.length - 1].getForeignTable();
				}
			}
			else
			{
				relationSelect.setSelection(StructuredSelection.EMPTY);
			}

			if (valueList.getValueListType() != ValueList.CUSTOM_VALUES && valueList.getValueListType() != ValueList.GLOBAL_METHOD_VALUES)
			{
				customValues.setText(""); //$NON-NLS-1$
			}

			currentTable = table;

			dp_select1.initDataBindings(table);
			dp_select2.initDataBindings(table);
			dp_select3.initDataBindings(table);

			boolean enabled = table != null;
			definitionGroup.setEnabled(enabled);
			dp_select1.setEnabled(enabled);
			dp_select2.setEnabled(enabled);
			dp_select3.setEnabled(enabled);
			sortingDefinitionSelect.setEnabled(enabled);
			separator_char.setEnabled(enabled);
			separatorCharacterLabel.setEnabled(enabled);

			if (enabled && valueList.getSortOptions() != null)
			{
				sortingDefinitionSelect.setSelection(new StructuredSelection(valueList.getSortOptions()));
			}
			else
			{
				sortingDefinitionSelect.setSelection(StructuredSelection.EMPTY);
			}

			m_bindingContext.updateTargets();
		}
		catch (RepositoryException e)
		{
			ServoyLog.logError(e);
		}
		catch (RemoteException e)
		{
			ServoyLog.logError(e);
		}
		finally
		{
			addListeners();
		}
	}

	private void addListeners()
	{
		customValuesButton.addSelectionListener(customvalueButtonSelectionListener);
		tableValuesButton.addSelectionListener(tableValuesButtonSelectionListener);
		relatedValuesButton.addSelectionListener(relatedValuesButtoneSelectionListener);
		tableSelect.addSelectionChangedListener(tableSelectionListener);
		tableSelect.addStatusChangedListener(statusChangeListener);
		relationSelect.addSelectionChangedListener(relationSelectionListener);
		relationSelect.addStatusChangedListener(statusChangeListener);
		globalMethodValuesButton.addSelectionListener(globalMethodValuesSelectionListener);
		globalMethodSelect.addSelectionChangedListener(globalMethodSelectionListener);
		globalMethodSelect.addStatusChangedListener(statusChangeListener);
		fallbackValuelist.addStatusChangedListener(statusChangeListener);
		sortingDefinitionSelect.addStatusChangedListener(statusChangeListener);
	}

	private void removeListeners()
	{
		customValuesButton.removeSelectionListener(customvalueButtonSelectionListener);
		tableValuesButton.removeSelectionListener(tableValuesButtonSelectionListener);
		relatedValuesButton.removeSelectionListener(relatedValuesButtoneSelectionListener);
		tableSelect.removeSelectionChangedListener(tableSelectionListener);
		tableSelect.removeStatusChangedListener(statusChangeListener);
		relationSelect.removeSelectionChangedListener(relationSelectionListener);
		relationSelect.removeStatusChangedListener(statusChangeListener);
		globalMethodValuesButton.removeSelectionListener(globalMethodValuesSelectionListener);
		globalMethodSelect.removeSelectionChangedListener(globalMethodSelectionListener);
		globalMethodSelect.removeStatusChangedListener(statusChangeListener);
		fallbackValuelist.removeStatusChangedListener(statusChangeListener);
		sortingDefinitionSelect.removeStatusChangedListener(statusChangeListener);
	}

	private DataBindingContext defineObservablesAndBindingContext()
	{
		m_bindingContext = BindingHelper.dispose(m_bindingContext);

		IObservableValue getValueListCustomValuesObserveValue = PojoObservables.observeValue(getValueList(), "customValues"); //$NON-NLS-1$
		IObservableValue fallbackValueListObserveValue = PojoObservables.observeValue(getValueList(), "fallbackValueListID"); //$NON-NLS-1$

		IObservableValue customValuesTextObserveWidget = SWTObservables.observeText(customValues, SWT.Modify);
		IObservableValue globalMethodtObserveWidget = new TreeSelectObservableValue(globalMethodSelect, String.class);
		IObservableValue fallbackValueListObserveWidget = new TreeSelectObservableValue(fallbackValuelist, int.class);

		IObservableValue getValueListSortOpotionsObserveValue = PojoObservables.observeValue(getValueList(), "sortOptions"); //$NON-NLS-1$
		IObservableValue sortingDefinitionSelectObserveWidget = new TreeSelectObservableValue(sortingDefinitionSelect, String.class);

		IObservableValue nameFieldTextObserveWidget = SWTObservables.observeText(nameField, SWT.Modify);
		IObservableValue getValueListNameObserveValue = new AbstractObservableValue()
		{
			public Object getValueType()
			{
				return null;
			}

			@Override
			protected Object doGetValue()
			{
				return getValueList().getName();
			}

			@Override
			protected void doSetValue(Object value)
			{
				//setName cannot be invoked, save does update name
				getValueList().flagChanged();
				flagModified();
			}
		};
		IObservableValue separatorFieldTextObserveWidget = SWTObservables.observeText(separator_char, SWT.Modify);
		IObservableValue getValueListSeparatorObserveValue = PojoObservables.observeValue(getValueList(), "separator"); //$NON-NLS-1$

		IObservableValue allowEmptyFieldTextObserveWidget = SWTObservables.observeSelection(allowEmptyValueButton);
		IObservableValue getValueListAllowEmptyValueObserveValue = PojoObservables.observeValue(getValueList(), "addEmptyValue"); //$NON-NLS-1$
		IObservableValue applyNameFilterSelectionObserveWidget = SWTObservables.observeSelection(applyValuelistNameButton);
		IObservableValue getApplyNameFilterSelectionObserveValue = PojoObservables.observeValue(getValueList(), "useTableFilter"); //$NON-NLS-1$

		m_bindingContext = new DataBindingContext();
		//
		m_bindingContext.bindValue(fallbackValueListObserveWidget, fallbackValueListObserveValue,
			new UpdateValueStrategy().setConverter(new Converter(Integer.class, Integer.class)
			{
				public Object convert(Object fromObject)
				{
					return fromObject == null ? new Integer(0) : fromObject;
				}
			}), new UpdateValueStrategy());

		m_bindingContext.bindValue(nameFieldTextObserveWidget, getValueListNameObserveValue, null, null);
		m_bindingContext.bindValue(customValuesTextObserveWidget, getValueListCustomValuesObserveValue,
			new UpdateValueStrategy().setConverter(new Converter(String.class, String.class)
			{
				public Object convert(Object fromObject)
				{
					if ("".equals(fromObject)) return null; //$NON-NLS-1$
					return fromObject;
				}
			}), new UpdateValueStrategy().setConverter(new Converter(String.class, String.class)
			{
				/**
				 * @see org.eclipse.core.databinding.conversion.IConverter#convert(java.lang.Object)
				 */
				public Object convert(Object fromObject)
				{
					if (fromObject == null) return ""; //$NON-NLS-1$
					if (getValueList().getValueListType() == ValueList.GLOBAL_METHOD_VALUES)
					{
						return ""; //$NON-NLS-1$
					}
					return fromObject;
				}
			}));
		m_bindingContext.bindValue(globalMethodtObserveWidget, getValueListCustomValuesObserveValue,
			new UpdateValueStrategy().setConverter(new Converter(String.class, String.class)
			{
				public Object convert(Object fromObject)
				{
					if ("".equals(fromObject)) return null; //$NON-NLS-1$
					if (fromObject instanceof String) return fromObject;
					if (fromObject instanceof MethodWithArguments)
					{
						FlattenedSolution fs = ModelUtils.getEditingFlattenedSolution(getPersist());
						ScriptMethod scriptMethod = fs.getScriptMethod(((MethodWithArguments)fromObject).methodId);
						if (scriptMethod != null)
						{
							return ScriptVariable.GLOBAL_DOT_PREFIX + scriptMethod.getName();
						}
					}
					return null;
				}
			}), new UpdateValueStrategy().setConverter(new Converter(String.class, String.class)
			{
				/**
				 * @see org.eclipse.core.databinding.conversion.IConverter#convert(java.lang.Object)
				 */
				public Object convert(Object fromObject)
				{
					if (fromObject == null) return null;
					if (getValueList().getValueListType() != ValueList.GLOBAL_METHOD_VALUES)
					{
						return fromObject;
					}
					FlattenedSolution fs = ModelUtils.getEditingFlattenedSolution(getPersist());
					ScriptMethod scriptMethod = fs.getScriptMethod(fromObject.toString().substring(ScriptVariable.GLOBAL_DOT_PREFIX.length()));
					if (scriptMethod != null)
					{
						return new MethodWithArguments(scriptMethod.getID());
					}
					return new MethodWithArguments.UnresolvedMethodWithArguments(fromObject.toString());
				}
			}));
		m_bindingContext.bindValue(sortingDefinitionSelectObserveWidget, getValueListSortOpotionsObserveValue, null, null);
		m_bindingContext.bindValue(allowEmptyFieldTextObserveWidget, getValueListAllowEmptyValueObserveValue,
			new UpdateValueStrategy().setConverter(new Converter(boolean.class, int.class)
			{
				public Object convert(Object fromObject)
				{
					return new Integer(Boolean.TRUE.equals(fromObject) ? ValueList.EMPTY_VALUE_ALWAYS : ValueList.EMPTY_VALUE_NEVER);
				}
			}), new UpdateValueStrategy().setConverter(new Converter(int.class, boolean.class)
			{
				public Object convert(Object fromObject)
				{
					return Boolean.valueOf(fromObject instanceof Integer && ((Integer)fromObject).intValue() == ValueList.EMPTY_VALUE_ALWAYS);
				}
			}));
		m_bindingContext.bindValue(applyNameFilterSelectionObserveWidget, getApplyNameFilterSelectionObserveValue, null, null);
		m_bindingContext.bindValue(separatorFieldTextObserveWidget, getValueListSeparatorObserveValue, null, null);

		BindingHelper.addGlobalChangeListener(m_bindingContext, new IChangeListener()
		{
			public void handleChange(ChangeEvent event)
			{
				flagModified();
			}
		});
		return m_bindingContext;
	}

	public void flagModified()
	{
		this.getSite().getShell().getDisplay().asyncExec(new Runnable()
		{
			public void run()
			{
				firePropertyChange(IEditorPart.PROP_DIRTY);
			}
		});
	}

	@Override
	public boolean isDirty()
	{
		return super.isDirty() || !tableSelect.isValid() || !relationSelect.isValid() || !globalMethodSelect.isValid() || !sortingDefinitionSelect.isValid() ||
			!fallbackValuelist.isValid();
	}

	private void handleGlobalMethodButtonSelected()
	{
		getValueList().setValueListType(ValueList.GLOBAL_METHOD_VALUES);
		getValueList().setDataSource(null);
		getValueList().setRelationName(null);
		getValueList().setCustomValues(null);
	}

	private void handleCustomValuesButtonSelected()
	{
		getValueList().setValueListType(ValueList.CUSTOM_VALUES);
		getValueList().setDataSource(null);
		getValueList().setRelationName(null);
		getValueList().setCustomValues(null);
	}

	private void handleDatabaseValuesButtonSelected()
	{
		getValueList().setValueListType(ValueList.DATABASE_VALUES);
		getValueList().setCustomValues(null);
		getValueList().setRelationName(null);
		databaseValuesTypeOverride = ValueList.TABLE_VALUES;
	}

	private void handleRelatedValuesButtonSelected()
	{
		getValueList().setValueListType(ValueList.DATABASE_VALUES);
		getValueList().setCustomValues(null);
		getValueList().setDataSource(null);
		databaseValuesTypeOverride = ValueList.RELATED_VALUES;
	}

	private void handleGlobalMethodSelected(MethodWithArguments methodWithArguments)
	{
		if (methodWithArguments == null)
		{
			getValueList().setCustomValues(null);
		}
		else
		{
			handleGlobalMethodButtonSelected();
			FlattenedSolution fs = ModelUtils.getEditingFlattenedSolution(getPersist());
			ScriptMethod scriptMethod = fs.getScriptMethod(methodWithArguments.methodId);
			if (scriptMethod != null)
			{
				getValueList().setCustomValues(ScriptVariable.GLOBAL_DOT_PREFIX + scriptMethod.getName());
			}
		}
	}

	private void handleTableSelected(TableWrapper tableWrapper)
	{
		if (tableWrapper == null)
		{
			getValueList().setDataSource(null);
		}
		else
		{
			handleDatabaseValuesButtonSelected();
			getValueList().setDataSource(DataSourceUtils.createDBTableDataSource(tableWrapper.getServerName(), tableWrapper.getTableName()));
		}
	}

	private void handleRelationSelected(RelationsWrapper relationsWrapper)
	{
		if (relationsWrapper == null)
		{
			getValueList().setRelationName(null);
		}
		else
		{
			handleRelatedValuesButtonSelected();
			getValueList().setRelationName(DatabaseUtils.getRelationsString(relationsWrapper.relations));
		}
	}

	@Override
	public void setFocus()
	{
		nameField.forceFocus();
	}

	@Override
	public void doSave(IProgressMonitor monitor)
	{
		if (isDirty())
		{
			ServoyModel servoyModel = ServoyModelManager.getServoyModelManager().getServoyModel();
			IValidateName validator = servoyModel.getNameValidator();
			try
			{
				getValueList().updateName(validator, nameField.getText());
				updateTitle();
			}
			catch (RepositoryException e)
			{
				MessageDialog.openError(getSite().getShell(), "Error while saving", e.getMessage()); //$NON-NLS-1$
				if (monitor != null) monitor.setCanceled(true);
				return;
			}

			String message = checkValidState();
			if (message != null)
			{
				MessageDialog.openWarning(getSite().getShell(), "Error while saving", message); //$NON-NLS-1$
				if (monitor != null) monitor.setCanceled(true);
				return;
			}
			else
			{
				// if valuelist still has invalid data in it, try to correct it automatically and tell the user what was changed
				List<Problem> problems = ServoyBuilder.checkValuelist(getValueList(), servoyModel.getFlattenedSolution(), ServoyModel.getServerManager(), true);
				StringBuffer paf = new StringBuffer("Some problems with the contents of this valuelist were noticed and corrected:\n"); //$NON-NLS-1$
				boolean autoFixes = false;
				for (Problem problem : problems)
				{
					if (problem.fix != null)
					{
						autoFixes = true;
						paf.append("\n- "); //$NON-NLS-1$
						paf.append(problem.message);
						paf.append(" Auto changed: "); //$NON-NLS-1$
						paf.append(problem.fix);
					}
				}
				if (autoFixes) MessageDialog.openWarning(getSite().getShell(), "Auto fixes when saving", paf.toString()); //$NON-NLS-1$
			}
		}
		super.doSave(monitor);
	}

	private String checkValidState()
	{
		if (nameField.getText() == null || nameField.getText().equals("")) //$NON-NLS-1$
		{
			return "You must specify the name of the valuelist."; //$NON-NLS-1$
		}

		if (tableValuesButton.getSelection())
		{
			if (!tableSelect.isValid())
			{
				return "The table name field is invalid"; //$NON-NLS-1$
			}

			boolean box1 = false;
			boolean box2 = false;
			boolean box3 = false;
			int boxesUsed = 0;
			int showUsed = 0;
			int returnUsed = 0;
			if (dp_select1.getShowInFieldFlag() || dp_select1.getReturnInDataproviderFlag())
			{
				if (dp_select1.getDataProvider() != null)
				{
					box1 = true;//filled
					boxesUsed++;
					if (dp_select1.getShowInFieldFlag())
					{
						showUsed++;
					}
					if (dp_select1.getReturnInDataproviderFlag())
					{
						returnUsed++;
					}
				}
				else
				{
					box1 = false;
				}
			}
			else
			{
				box1 = true;//still oke
			}

			if (dp_select2.getShowInFieldFlag() || dp_select2.getReturnInDataproviderFlag())
			{
				if (dp_select2.getDataProvider() != null)
				{
					box2 = true;//filled
					boxesUsed++;
					if (dp_select2.getShowInFieldFlag())
					{
						showUsed++;
					}
					if (dp_select2.getReturnInDataproviderFlag())
					{
						returnUsed++;
					}
				}
				else
				{
					box2 = false;
				}
			}
			else
			{
				box2 = true;//still oke
			}

			if (dp_select3.getShowInFieldFlag() || dp_select3.getReturnInDataproviderFlag())
			{
				if (dp_select3.getDataProvider() != null)
				{
					box3 = true;//filled
					boxesUsed++;
					if (dp_select3.getShowInFieldFlag())
					{
						showUsed++;
					}
					if (dp_select3.getReturnInDataproviderFlag())
					{
						returnUsed++;
					}
				}
				else
				{
					box3 = false;
				}
			}
			else
			{
				box3 = true;//still oke
			}

			if (boxesUsed == 0)
			{
				return "You must use at least one definition box."; //$NON-NLS-1$
			}
			if (showUsed == 0)
			{
				return "You must check at least one Show in field/list from a selected column."; //$NON-NLS-1$
			}
			if (returnUsed == 0)
			{
				return "You must check at least one Return in dataprovider from a selected column."; //$NON-NLS-1$
			}
			if (!box1)
			{
				return "There is an error in the first definition box"; //$NON-NLS-1$
			}
			if (!box2)
			{
				return "There is an error in the second definition box"; //$NON-NLS-1$
			}
			if (!box3)
			{
				return "There is an error in the third definition box"; //$NON-NLS-1$
			}
		}
		else if (relatedValuesButton.getSelection())
		{
			if (!relationSelect.isValid())
			{
				return "The relation name field is invalid"; //$NON-NLS-1$
			}
		}
		else if (globalMethodValuesButton.getSelection())
		{
			if (!globalMethodSelect.isValid())
			{
				return "The global method field is invalid"; //$NON-NLS-1$
			}
		}

		if (!sortingDefinitionSelect.isValid())
		{
			return "The sorting definition is invalid"; //$NON-NLS-1$
		}

		if (!fallbackValuelist.isValid())
		{
			return "The fallback value list is invalid"; //$NON-NLS-1$
		}

		return null;
	}

	private static class FallbackValuelistContentProvider extends FlatTreeContentProvider
	{

		private final FlattenedSolution flattenedSolution;
		private final ValueList vl;

		FallbackValuelistContentProvider(FlattenedSolution flattenedSolution, ValueList vl)
		{
			this.flattenedSolution = flattenedSolution;
			this.vl = vl;
		}

		@Override
		public Object[] getElements(Object inputElement)
		{
			List<Integer> vlIds = new ArrayList<Integer>();
			vlIds.add(new Integer(ValuelistLabelProvider.VALUELIST_NONE));

			Iterator<ValueList> it = flattenedSolution.getValueLists(true);
			while (it.hasNext())
			{
				Set<ValueList> processed = new HashSet<ValueList>(3);
				ValueList obj = it.next();
				if (isValid(obj, processed))
				{
					vlIds.add(new Integer(obj.getID()));
				}
			}

			return vlIds.toArray();
		}

		private boolean isValid(ValueList list, Set<ValueList> processed)
		{
			if (list == vl) return false;
			if (!processed.add(list)) return false;

			if (list.getValueListType() == ValueList.DATABASE_VALUES && vl.getValueListType() == ValueList.DATABASE_VALUES)
			{
				String listTable = getDataSource(list);
				String vlTable = getDataSource(vl);
				if (!listTable.equals(vlTable)) return false;
			}
			if (list.getFallbackValueListID() != 0)
			{
				ValueList valueList = flattenedSolution.getValueList(list.getFallbackValueListID());
				if (valueList != null)
				{
					return isValid(valueList, processed);
				}
			}
			return true;
		}

		/**
		 * @param list
		 */
		private String getDataSource(ValueList list)
		{
			if (list.getDatabaseValuesType() == ValueList.TABLE_VALUES)
			{
				return list.getDataSource();
			}
			else if (list.getDatabaseValuesType() == ValueList.RELATED_VALUES && list.getRelationName() != null)
			{
				Relation[] relations = flattenedSolution.getRelationSequence(list.getRelationName());
				if (relations != null)
				{
					return relations[relations.length - 1].getForeignDataSource();
				}
			}
			return ""; //$NON-NLS-1$
		}

	}

	protected DataBindingContext initDataBindings()
	{
		dp_select1.initValueListBindings(this, 1, 1);
		dp_select2.initValueListBindings(this, 2, 2);
		dp_select3.initValueListBindings(this, 3, 4);

		if (ServoyModelManager.getServoyModelManager().getServoyModel().getFlattenedSolution() != null &&
			ServoyModelManager.getServoyModelManager().getServoyModel().getFlattenedSolution().getValueList(getValueList().getName()) == null)
		{
			// is a new valuelist
			dp_select1.initDefaultValues();
		}

		return defineObservablesAndBindingContext();
	}

	private static class GlobalMethodListContentProvider extends FlatTreeContentProvider
	{
		private final IPersist persist;

		/**
		 * @param valueList
		 */
		public GlobalMethodListContentProvider(IPersist persist)
		{
			this.persist = persist;
		}

		/**
		 * @see org.eclipse.jface.viewers.ArrayContentProvider#getElements(java.lang.Object)
		 */
		@Override
		public Object[] getElements(Object inputElement)
		{
			FlattenedSolution editingFlattenedSolution = ModelUtils.getEditingFlattenedSolution(persist);
			Iterator<ScriptMethod> scriptMethods = editingFlattenedSolution.getScriptMethods(true);
			List<MethodWithArguments> lst = new ArrayList<MethodWithArguments>();
			while (scriptMethods.hasNext())
			{
				MethodWithArguments mwa = new MethodWithArguments(scriptMethods.next().getID());
				lst.add(mwa);
			}
			return lst.toArray();
		}
	}
}
