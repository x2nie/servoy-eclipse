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
package com.servoy.eclipse.ui.editors.table;

import org.eclipse.core.databinding.observable.AbstractObservable;
import org.eclipse.core.databinding.observable.ChangeEvent;
import org.eclipse.core.databinding.observable.ChangeSupport;
import org.eclipse.core.databinding.observable.IChangeListener;
import org.eclipse.core.databinding.observable.IObservable;
import org.eclipse.core.databinding.observable.Realm;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;

import com.servoy.eclipse.ui.util.FixedComboBoxCellEditor;
import com.servoy.j2db.persistence.AggregateVariable;
import com.servoy.j2db.query.QueryAggregate;


public class AggregationTypeEditingSupport extends EditingSupport
{
	private final CellEditor editor;
	private final IObservable observable;

	public AggregationTypeEditingSupport(TreeViewer tv)
	{
		super(tv);
		String[] types = new String[QueryAggregate.ALL_DEFINED_AGGREGATES.length];
		for (int i = 0; i < types.length; i++)
		{
			types[i] = AggregateVariable.getTypeAsString(QueryAggregate.ALL_DEFINED_AGGREGATES[i]);
		}
		editor = new FixedComboBoxCellEditor(tv.getTree(), types, SWT.READ_ONLY);
		changeSupport = new ChangeSupport(Realm.getDefault())
		{
			@Override
			protected void lastListenerRemoved()
			{
			}

			@Override
			protected void firstListenerAdded()
			{
			}
		};
		observable = new AbstractObservable(Realm.getDefault())
		{
			@Override
			public void addChangeListener(IChangeListener listener)
			{
				changeSupport.addChangeListener(listener);
			}

			@Override
			public void removeChangeListener(IChangeListener listener)
			{
				changeSupport.removeChangeListener(listener);
			}

			public boolean isStale()
			{
				return false;
			}
		};
	}

	private final ChangeSupport changeSupport;

	public void addChangeListener(IChangeListener listener)
	{
		observable.addChangeListener(listener);
	}

	public void removeChangeListener(IChangeListener listener)
	{
		observable.removeChangeListener(listener);
	}

	@Override
	protected void setValue(Object element, Object value)
	{
		if (element instanceof AggregateVariable)
		{
			AggregateVariable aggregateVariable = (AggregateVariable)element;
			int index = Integer.parseInt(value.toString());
			int type = QueryAggregate.ALL_DEFINED_AGGREGATES[index];
			if (type != aggregateVariable.getType())
			{
				aggregateVariable.setType(type);
				getViewer().update(element, null);
				changeSupport.fireEvent(new ChangeEvent(observable));
			}
		}
	}

	@Override
	protected Object getValue(Object element)
	{
		if (element instanceof AggregateVariable)
		{
			AggregateVariable aggregateVariable = (AggregateVariable)element;
			int type = aggregateVariable.getType();
			int index = 0;
			for (int i = 0; i < QueryAggregate.ALL_DEFINED_AGGREGATES.length; i++)
			{
				if (QueryAggregate.ALL_DEFINED_AGGREGATES[i] == type)
				{
					index = i;
					break;
				}
			}
			return new Integer(index);
		}
		return null;
	}

	@Override
	protected CellEditor getCellEditor(Object element)
	{
		return editor;
	}

	@Override
	protected boolean canEdit(Object element)
	{
		if (element instanceof AggregateVariable) return true;
		else return false;
	}
}
