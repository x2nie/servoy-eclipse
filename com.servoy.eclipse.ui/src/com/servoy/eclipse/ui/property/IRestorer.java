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

/**
 * IRestorer interface, for objects that can save an objects state into an object and can restore state from that object.
 * 
 * @author rgansevles
 *
 */
public interface IRestorer
{
	/**
	 * Get state of an object to restore from
	 * @param object
	 * @return
	 */
	Object getState(Object object);

	/**
	 * Get state to indicate object was created and should be removed when restored.
	 * @param object
	 * @return
	 */
	Object getRemoveState(Object object);

	/**
	 * Restore state of object
	 * @param object
	 * @param state from either getState() or getRemoveState()
	 */
	void restoreState(Object object, Object state);
}
