/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2015 Servoy BV

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

package com.servoy.eclipse.designer.editor.rfb.actions.handlers;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;

import org.eclipse.gef.commands.Command;

import com.servoy.eclipse.core.ServoyModelManager;
import com.servoy.j2db.persistence.IChildWebObject;
import com.servoy.j2db.persistence.IPersist;
import com.servoy.j2db.persistence.ISupportBounds;
import com.servoy.j2db.persistence.ISupportChilds;
import com.servoy.j2db.persistence.PositionComparator;

/**
 * @author jcompagner
 *
 */
public class ChangeParentCommand extends Command
{
	private final IPersist child, targetChild;
	private final ISupportChilds newParent;
	private ISupportChilds oldParent;
	private final boolean insertAfterTarget;

	public ChangeParentCommand(IPersist child, ISupportChilds newParent)
	{
		this(child, newParent, null, false);
	}

	public ChangeParentCommand(IPersist child, ISupportChilds newParent, IPersist targetChild, boolean insertAfterTarget)
	{
		super("Change Parent");
		this.child = child;
		this.targetChild = targetChild;
		this.newParent = newParent;
		this.insertAfterTarget = insertAfterTarget;
	}

	@Override
	public void execute()
	{
		oldParent = child.getParent();
		oldParent.removeChild(child);

		if (child instanceof ISupportBounds)
		{
			ArrayList<IPersist> children = new ArrayList<IPersist>();
			Iterator<IPersist> it = newParent.getAllObjects();
			while (it.hasNext())
			{
				IPersist persist = it.next();
				if (persist instanceof ISupportBounds)
				{
					children.add(persist);
				}
			}
			IPersist[] sortedChildArray = children.toArray(new IPersist[0]);
			Arrays.sort(sortedChildArray, PositionComparator.XY_PERSIST_COMPARATOR);
			children = new ArrayList<IPersist>(Arrays.asList(sortedChildArray));

			int insertIdx = targetChild instanceof ISupportBounds ? children.indexOf(targetChild) : -1;
			if (insertIdx == -1) children.add(child);
			else
			{
				if (insertAfterTarget) insertIdx++;
				if (insertIdx < children.size()) children.add(insertIdx, child);
				else children.add(child);
			}

			int counter = 1;
			for (IPersist p : children)
			{
				((ISupportBounds)p).setLocation(new Point(counter, counter));
				counter++;
			}
		}
		else if (child instanceof IChildWebObject)
		{
			ArrayList<IChildWebObject> children = new ArrayList<IChildWebObject>();
			Iterator<IPersist> it = newParent.getAllObjects();
			while (it.hasNext())
			{
				IPersist persist = it.next();
				if (persist instanceof IChildWebObject)
				{
					children.add((IChildWebObject)persist);
				}
			}
			IChildWebObject[] sortedChildArray = children.toArray(new IChildWebObject[0]);
			Arrays.sort(sortedChildArray, new Comparator<IChildWebObject>()
			{
				@Override
				public int compare(IChildWebObject o1, IChildWebObject o2)
				{
					return o1.getIndex() - o2.getIndex();
				}

			});
			children = new ArrayList<IChildWebObject>(Arrays.asList(sortedChildArray));

			int insertIdx = targetChild instanceof IChildWebObject ? children.indexOf(targetChild) : -1;
			if (insertIdx == -1) children.add((IChildWebObject)child);
			else
			{
				if (insertAfterTarget) insertIdx++;
				if (insertIdx < children.size()) children.add(insertIdx, (IChildWebObject)child);
				else children.add((IChildWebObject)child);
			}

			int counter = 0;
			for (IChildWebObject p : children)
			{
				p.setIndex(counter);
				counter++;
			}
		}

		newParent.addChild(child);
		ServoyModelManager.getServoyModelManager().getServoyModel().firePersistsChanged(false, Arrays.asList(new IPersist[] { child }));
	}

	@Override
	public void undo()
	{
		ArrayList<IPersist> changes = new ArrayList<IPersist>();
		changes.add(child.getParent());
		changes.add(oldParent);
		child.getParent().removeChild(child);
		oldParent.addChild(child);
		ServoyModelManager.getServoyModelManager().getServoyModel().firePersistsChanged(false, changes);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.gef.commands.Command#redo()
	 */
	@Override
	public void redo()
	{
		ArrayList<IPersist> changes = new ArrayList<IPersist>();
		changes.add(newParent);
		changes.add(oldParent);
		super.redo();
		ServoyModelManager.getServoyModelManager().getServoyModel().firePersistsChanged(false, changes);
	}
}
