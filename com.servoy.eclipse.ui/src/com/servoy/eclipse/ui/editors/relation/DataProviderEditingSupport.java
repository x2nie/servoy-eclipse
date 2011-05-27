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
package com.servoy.eclipse.ui.editors.relation;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ComboBoxCellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.TableItem;

import com.servoy.eclipse.model.util.ModelUtils;
import com.servoy.eclipse.model.util.ServoyLog;
import com.servoy.eclipse.ui.dialogs.DataProviderDialog;
import com.servoy.eclipse.ui.dialogs.DataProviderTreeViewer;
import com.servoy.eclipse.ui.dialogs.DataProviderTreeViewer.DataProviderOptions.INCLUDE_RELATIONS;
import com.servoy.eclipse.ui.editors.RelationEditor;
import com.servoy.eclipse.ui.labelproviders.DataProviderLabelProvider;
import com.servoy.eclipse.ui.labelproviders.SolutionContextDelegateLabelProvider;
import com.servoy.eclipse.ui.property.DataProviderConverter;
import com.servoy.eclipse.ui.property.PersistContext;
import com.servoy.eclipse.ui.util.FixedComboBoxCellEditor;
import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.persistence.IDataProvider;
import com.servoy.j2db.persistence.Relation;
import com.servoy.j2db.util.Utils;

public class DataProviderEditingSupport extends EditingSupport
{
	private final ComboBoxCellEditor editor;
	private final RelationEditor relationEditor;
	private final int index;
	private final Integer[] editingRow;

	public DataProviderEditingSupport(final RelationEditor re, final TableViewer tv, int i)
	{
		super(tv);
		relationEditor = re;
		index = i;
		editor = new FixedComboBoxCellEditor(tv.getTable(), new String[0], SWT.READ_ONLY);

		MouseAdapter listener = new MouseAdapter()
		{
			@Override
			public void mouseDown(MouseEvent e)
			{
				if (e.button == 1)
				{
					if ((e.stateMask & SWT.MOD1) > 0)
					{
						Point point = new Point(e.x, e.y);
						TableItem item = tv.getTable().getItem(point);
						if (item != null && item.getBounds(index).contains(point))
						{
							setDataProvider(tv, item, re);
						}
					}
				}
			}
		};
		tv.getTable().addMouseListener(listener);
		editingRow = new Integer[1];
		if (editor.getControl() instanceof CCombo)
		{
			((CCombo)editor.getControl()).setVisibleItemCount(RelationEditor.NUMBER_VISIBLE_ITEMS);
			((CCombo)editor.getControl()).addKeyListener(new KeyAdapter()
			{
				@Override
				public void keyPressed(KeyEvent e)
				{
					if (e.keyCode == SWT.CR && (e.stateMask & SWT.MOD1) > 0)
					{
						setDataProvider(tv, tv.getTable().getItem(editingRow[0].intValue()), re);
					}
				}
			});
			((CCombo)editor.getControl()).addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseDown(MouseEvent e)
				{
					if (e.button == 1)
					{
						if ((e.stateMask & SWT.MOD1) > 0)
						{
							setDataProvider(tv, tv.getTable().getItem(editingRow[0].intValue()), re);
						}
					}
				}
			});
		}

	}

	private void setDataProvider(final TableViewer tv, final TableItem item, final RelationEditor re)
	{
		try
		{
			List<TableItem> items = Arrays.asList(tv.getTable().getItems());
			int idx = items.indexOf(item);
			if (re.canEditIndex(idx))
			{
				com.servoy.j2db.persistence.Table table = null;
				if (index == RelationEditor.CI_FROM)
				{
					table = ((Relation)re.getPersist()).getPrimaryTable();
				}
				else
				{
					table = ((Relation)re.getPersist()).getForeignTable();
				}
				if (table != null || index == RelationEditor.CI_FROM)
				{
					boolean includeGlobalsAndCalcs = (index == RelationEditor.CI_FROM);
					FlattenedSolution flattenedEditingSolution = ModelUtils.getEditingFlattenedSolution(re.getPersist());

					String[] dataProviders = relationEditor.getDataProviders(index);
					Object rowInput = re.getRowInput(idx);
					Integer valueIndex = 0;
					if (rowInput instanceof Integer[])
					{
						valueIndex = ((Integer[])rowInput)[index];
					}
					IDataProvider provider = null;
					if (valueIndex != null)
					{
						provider = DataProviderConverter.getDataProvider(flattenedEditingSolution, null, table, dataProviders[valueIndex.intValue()]);
					}
					DataProviderDialog dialog = new DataProviderDialog(re.getSite().getShell(), new SolutionContextDelegateLabelProvider(
						DataProviderLabelProvider.INSTANCE_HIDEPREFIX, re.getPersist()), PersistContext.create(re.getPersist(), null),
						flattenedEditingSolution, table, new DataProviderTreeViewer.DataProviderOptions(false, true, includeGlobalsAndCalcs, false, false,
							includeGlobalsAndCalcs, false, false, INCLUDE_RELATIONS.NO, false, true, null), provider != null
							? new StructuredSelection(provider) : null, SWT.NONE, "Select Data Provider");
					dialog.open();

					if (dialog.getReturnCode() != Window.CANCEL)
					{
						Object sel = ((IStructuredSelection)dialog.getSelection()).getFirstElement();
						if (sel instanceof IDataProvider)
						{
							String id = ((IDataProvider)sel).getDataProviderID();
							int i = 0;
							for (i = 0; i < dataProviders.length; i++)
							{
								if (id.equals(dataProviders[i]))
								{
									setValue(rowInput, new Integer(i));
									break;
								}
							}
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

	@Override
	protected void setValue(Object element, Object value)
	{
		if (element instanceof Integer[])
		{
			Integer[] pi = (Integer[])element;
			Integer previousValue = pi[index];
			pi[index] = new Integer(Utils.getAsInteger(value));
			relationEditor.autoFill(pi, index);
			if (!Utils.equalObjects(previousValue, pi[index]) && !(previousValue == null && pi[index].intValue() == 0)) relationEditor.flagModified(true);
			getViewer().update(element, null);
		}
	}

	@Override
	protected Object getValue(Object element)
	{
		if (element instanceof Integer[])
		{
			Integer[] pi = (Integer[])element;
			return (pi[index] != null ? pi[index] : new Integer(0));
		}
		return null;
	}

	@Override
	protected CellEditor getCellEditor(Object element)
	{
		editor.setItems(relationEditor.getDataProviders(index));
		editingRow[0] = new Integer(relationEditor.getIndex(element));
		return editor;
	}

	@Override
	protected boolean canEdit(Object element)
	{
		if (element instanceof Integer[] && editor != null)
		{
			return relationEditor.canEdit(element);
		}
		return false;
	}
}