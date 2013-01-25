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
package com.servoy.eclipse.ui.property;

import org.eclipse.jface.viewers.ILabelProvider;

import com.servoy.eclipse.ui.Messages;
import com.servoy.j2db.util.Utils;

/**
 * Simple combobox model with static real and display values.
 * 
 * @author rgansevles
 */

public class ComboboxPropertyModel<T> implements IComboboxPropertyModel<T>
{
	private T[] real;
	private String[] display;

	/**
	 * Display and real values are the same.
	 * 
	 * @param display
	 */
	public ComboboxPropertyModel(T[] real)
	{
		this(real, (ILabelProvider)null);
	}

	public ComboboxPropertyModel<T> addDefaultValue()
	{
		return addDefaultValue(null, Messages.LabelDefault);
	}

	public ComboboxPropertyModel<T> addDefaultValue(T defaultReal)
	{
		return addDefaultValue(defaultReal, Messages.LabelDefault);
	}

	public ComboboxPropertyModel<T> addDefaultValue(T defaultReal, String defaultDisplay)
	{
		real = Utils.arrayAdd(real, defaultReal, false);
		display = Utils.arrayAdd(display, defaultDisplay, false);
		return this;
	}

	/**
	 * Display and real values are the same.
	 * 
	 * @param display
	 */
	public ComboboxPropertyModel(T[] real, ILabelProvider labelProvider)
	{
		this.real = real;
		this.display = new String[real.length];
		for (int i = 0; i < real.length; i++)
		{
			if (labelProvider == null)
			{
				display[i] = real[i] == null ? "" : real[i].toString();
			}
			else
			{
				display[i] = labelProvider.getText(real[i]);
			}
		}
	}


	/**
	 * Display and real values are different.
	 * 
	 * @param display
	 */
	public ComboboxPropertyModel(T[] real, String[] display)
	{
		this.real = real;
		this.display = display;
	}

	public String[] getDisplayValues()
	{
		return display;
	}

	public T[] getRealValues()
	{
		return real;
	}
}
