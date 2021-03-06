/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2014 Servoy BV

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

package com.servoy.j2db.server.ngclient.design;

import java.util.List;
import java.util.Map;

import org.sablo.specification.WebObjectSpecification;
import org.sablo.websocket.IClientService;
import org.sablo.websocket.impl.ClientService;

import com.servoy.j2db.IFormController;
import com.servoy.j2db.server.ngclient.INGClientWindow;
import com.servoy.j2db.server.ngclient.NGClientWebsocketSession;
import com.servoy.j2db.server.ngclient.eventthread.NGClientWebsocketSessionWindows;

/**
 * @author jcompagner
 */
public class DesignNGClientWebsocketSession extends NGClientWebsocketSession
{
	public static final String EDITOR_CONTENT_SERVICE = "$editorContentService";

	private static final WebObjectSpecification EDITOR_CONTENT_SERVICE_SPECIFICATION = new WebObjectSpecification(EDITOR_CONTENT_SERVICE, "",
		EDITOR_CONTENT_SERVICE, null, null, null, "", null);

	/**
	 * @param uuid
	 */
	public DesignNGClientWebsocketSession(String uuid)
	{
		super(uuid);
	}

	@Override
	protected IClientService createClientService(String name)
	{
		if (EDITOR_CONTENT_SERVICE.equals(name))
		{
			return new ClientService(EDITOR_CONTENT_SERVICE, EDITOR_CONTENT_SERVICE_SPECIFICATION);
		}
		return super.createClientService(name);
	}


	@Override
	public INGClientWindow createWindow(String windowUuid, String windowName)
	{
		return new DesignNGClientWindow(this, windowUuid, windowName);
	}

	@Override
	public void onOpen(Map<String, List<String>> requestParams)
	{
		super.onOpen(requestParams);
		String form = requestParams.get("f").get(0);
		IFormController controller = getClient().getFormManager().leaseFormPanel(form);
		getClient().getRuntimeWindowManager().getCurrentWindow().setController(controller);
		if (getClient().getSolution() != null)
		{
			sendSolutionCSSURL(getClient().getSolution());
		}
	}

	@Override
	public INGClientWindow getWindowWithForm(String formName)
	{
		// as in developer when we want to debug we will open the form editor in a browser we might end up with the same form showing in two places (developer and separate editor)
		// both need to be used so we return here the all-windows-proxy
		NGClientWebsocketSessionWindows proxyToAllWindows = new NGClientWebsocketSessionWindows(this);
		return proxyToAllWindows.hasForm(formName) ? proxyToAllWindows : null;
	}

}
