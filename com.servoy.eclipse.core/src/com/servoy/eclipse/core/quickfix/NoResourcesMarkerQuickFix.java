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
package com.servoy.eclipse.core.quickfix;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;

/**
 * This class is able to quick-fix no resources problems markers by letting the user choose one resources project to remain referenced to the Servoy
 * solution project from the workspace.
 * 
 * @author acostescu
 */
public class NoResourcesMarkerQuickFix extends ChooseResourcesProjectQuickFix
{

	public NoResourcesMarkerQuickFix()
	{
		super(
			"Choose one of the workspace's resources projects.",
			"does not reference any Servoy resources projects. This in incorrect.\n\nPlease choose one of the resources projects from the workspace. A reference will be created to the selected project.");
	}

	@Override
	protected IProject[] getProjectListToFilter(IProject servoyProject) throws CoreException
	{
		return servoyProject.getWorkspace().getRoot().getProjects();
	}

}
