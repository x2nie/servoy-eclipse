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
package com.servoy.eclipse.ui.util;

import java.util.Comparator;

import com.servoy.j2db.persistence.IColumn;

/**
 * Comparator for IColumn, sorts on name.
 * 
 * @author gerzse
 */


public class IColumnComparator implements Comparator<IColumn>
{
	public int compare(IColumn c1, IColumn c2)
	{
		if (c1 == null)
		{
			if (c2 == null) return 0;
			else return -1;
		}
		else
		{
			if (c2 == null) return 1;
			else
			{
				if (c1.getName() == null)
				{
					if (c2.getName() == null) return 0;
					else return -1;
				}
				else
				{
					if (c2.getName() == null) return 1;
					else return c1.getName().compareTo(c2.getName());
				}
			}
		}
	}
}
