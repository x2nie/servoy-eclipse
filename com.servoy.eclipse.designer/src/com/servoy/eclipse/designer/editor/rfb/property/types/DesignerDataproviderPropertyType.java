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

package com.servoy.eclipse.designer.editor.rfb.property.types;

import org.json.JSONException;
import org.json.JSONWriter;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.property.IBrowserConverterContext;
import org.sablo.websocket.utils.DataConversion;
import org.sablo.websocket.utils.JSONUtils;

import com.servoy.eclipse.designer.editor.rfb.DesignerNGUtils;
import com.servoy.j2db.server.ngclient.property.types.DataproviderPropertyType;
import com.servoy.j2db.server.ngclient.property.types.DataproviderTypeSabloValue;

/**
 * @author lvostinar
 *
 */
public class DesignerDataproviderPropertyType extends DataproviderPropertyType
{
	public static final DesignerDataproviderPropertyType DESIGNER_INSTANCE = new DesignerDataproviderPropertyType();

	@Override
	public JSONWriter toJSON(JSONWriter writer, String key, DataproviderTypeSabloValue sabloValue, PropertyDescription pd, DataConversion clientConversion,
		IBrowserConverterContext dataConverterContext) throws JSONException
	{
		if (!DesignerNGUtils.shouldShowData(dataConverterContext))
		{
			JSONUtils.addKeyIfPresent(writer, key);
			writer.value(null);
			return writer;
		}
		return super.toJSON(writer, key, sabloValue, pd, clientConversion, dataConverterContext);
	}
}
