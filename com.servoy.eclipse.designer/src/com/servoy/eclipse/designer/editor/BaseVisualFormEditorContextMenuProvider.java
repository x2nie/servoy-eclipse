/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2013 Servoy BV

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
package com.servoy.eclipse.designer.editor;

import org.eclipse.gef.ui.actions.ActionRegistry;
import org.eclipse.gef.ui.actions.GEFActionConstants;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.ui.IWorkbenchActionConstants;

import com.servoy.eclipse.designer.editor.commands.DesignerActionFactory;


/**
 * Provides context menu actions for the VisualFormEditor.
 * 
 * @author rgansevles
 */
public abstract class BaseVisualFormEditorContextMenuProvider extends MenuManager implements IMenuListener
{
	/** The editor's action registry. */
	protected final ActionRegistry actionRegistry;

	/**
	 * Instantiate a new menu context provider for the specified EditPartViewer and ActionRegistry.
	 * 
	 * @param viewer the editor's graphical viewer
	 * @param registry the editor's action registry
	 * @throws IllegalArgumentException if registry is <tt>null</tt>.
	 */
	public BaseVisualFormEditorContextMenuProvider(String id, ActionRegistry registry)
	{
		super(id, id);
		addMenuListener(this);
		setRemoveAllWhenShown(true);
		if (registry == null)
		{
			throw new IllegalArgumentException();
		}
		actionRegistry = registry;
	}

	/**
	 * @see IMenuListener#menuAboutToShow(IMenuManager)
	 */
	public void menuAboutToShow(IMenuManager menu)
	{
		buildContextMenu(menu);
	}

	/**
	 * Called when the context menu is about to show. Actions, whose state is enabled, will appear in the context menu.
	 * 
	 * @see org.eclipse.gef.ContextMenuProvider#buildContextMenu(org.eclipse.jface.action.IMenuManager)
	 */
	public void buildContextMenu(IMenuManager menu)
	{
		// Add standard action groups to the menu, but in the order we want to.
		menu.add(new Separator(GEFActionConstants.GROUP_UNDO));
		menu.add(new Separator(GEFActionConstants.GROUP_COPY));
		menu.add(new Separator(DesignerActionFactory.GROUP_ELEMENTS));
		menu.add(new Separator(DesignerActionFactory.GROUP_REFACTOR));
		menu.add(new Separator(DesignerActionFactory.GROUP_ACTIONS));
		menu.add(new Separator(GEFActionConstants.GROUP_REST));
		menu.add(new Separator(IWorkbenchActionConstants.SAVE_EXT));
		// Placeholder for contributions from other plugins
		menu.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
		menu.add(new Separator(IWorkbenchActionConstants.SHOW_EXT));
		menu.add(new Separator(IWorkbenchActionConstants.OPEN_EXT));

		// Add actions to the menu
		addContextmenuActions(menu);
	}

	/**
	 * @param menu  
	 */
	protected void addContextmenuActions(IMenuManager menu)
	{
	}
}
