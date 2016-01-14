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

package com.servoy.eclipse.ui.property.types;

import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.json.JSONException;
import org.json.JSONObject;
import org.sablo.specification.PropertyDescription;
import org.sablo.specification.WebComponentSpecification;

import com.servoy.eclipse.model.util.ServoyLog;
import com.servoy.eclipse.ui.property.ComplexProperty;
import com.servoy.eclipse.ui.property.ConvertorObjectCellEditor.IObjectTextConverter;
import com.servoy.eclipse.ui.property.ISetterAwarePropertySource;
import com.servoy.eclipse.ui.property.JSONObjectTypePropertyController.JSONObjectTextConverter;
import com.servoy.eclipse.ui.property.ObjectTypePropertyController;
import com.servoy.eclipse.ui.property.PDPropertySource;
import com.servoy.eclipse.ui.property.PersistContext;
import com.servoy.j2db.persistence.IBasicWebObject;
import com.servoy.j2db.persistence.IPersist;
import com.servoy.j2db.util.ServoyJSONObject;

/**
 * Property controller to be used in properties view for custom objects.
 *
 * @author acostescu
 */
public class CustomObjectTypePropertyController extends ObjectTypePropertyController
{

	private static IObjectTextConverter OBJECT_TEXT_CONVERTER;

	private final PersistContext persistContext;
	private final PropertyDescription propertyDescription;

	public CustomObjectTypePropertyController(Object id, String displayName, PersistContext persistContext, PropertyDescription propertyDescription)
	{
		super(id, displayName);
		this.persistContext = persistContext;
		this.propertyDescription = propertyDescription;
		OBJECT_TEXT_CONVERTER = new CustomObjectTextConverter();
	}

	@Override
	protected CustomObjectPropertySource getObjectChildPropertySource(ComplexProperty<Object> complexProperty)
	{
		return new CustomObjectPropertySource(complexProperty);
	}

	private class CustomObjectTextConverter extends JSONObjectTextConverter
	{
		/*
		 * (non-Javadoc)
		 *
		 * @see com.servoy.eclipse.ui.property.ConvertorObjectCellEditor.IObjectTextConverter#convertToObject(java.lang.String)
		 */
		@Override
		public Object convertToObject(String value)
		{
			IPersist persist = persistContext.getPersist();
			if (persist instanceof IBasicWebObject)
			{
				IBasicWebObject wo = (IBasicWebObject)persist;
				JSONObject obj = (JSONObject)super.convertToObject(value);
				for (String key : obj.keySet())
				{
					wo.setJsonSubproperty(key, obj.opt(key));
				}
			}
			return null;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see com.servoy.eclipse.ui.property.ConvertorObjectCellEditor.IObjectTextConverter#convertToString(java.lang.Object)
		 */
		@Override
		public String convertToString(Object value)
		{
			if (value instanceof IBasicWebObject)
			{
				return super.convertToString(((IBasicWebObject)value).getFlattenedJson());
			}
			return null;
		}

		/*
		 * (non-Javadoc)
		 *
		 * @see com.servoy.eclipse.ui.property.ConvertorObjectCellEditor.IObjectTextConverter#isCorrectObject(java.lang.Object)
		 */
		@Override
		public String isCorrectObject(Object value)
		{
			if (value instanceof IBasicWebObject)
			{
				return super.isCorrectObject(((IBasicWebObject)value).getFlattenedJson());
			}
			return null;
		}

	}

	protected class CustomObjectPropertySource extends ObjectPropertySource
	{

		protected PDPropertySource underlyingPropertySource;

		public CustomObjectPropertySource(ComplexProperty<Object> complexProperty)
		{
			super(complexProperty);
		}

		protected PDPropertySource getUnderlyingPropertySource()
		{
			IPersist persist = (IPersist)getEditableValue(); // parent persist holding property with propertyDescription
			PersistContext pContext = PersistContext.create(persist, persistContext.getContext());
			;

			String pdName = propertyDescription.getName();
			if (persist != null && !WebComponentSpecification.ARRAY_ELEMENT_PD_NAME.equals(pdName))
			{
				persist = (IPersist)((IBasicWebObject)persist).getProperty(pdName);
				// property of a custom object or property of a web component; persistContext points to parent in this case
				pContext = PersistContext.create(persist, persistContext.getContext());
			} // else persistContext already has correct persist (array element persist)


			if (underlyingPropertySource == null || ((persist instanceof IBasicWebObject) && (((IBasicWebObject)persist).getFlattenedJson() == null))) // so if we have no propertySource or if we have one but we shouldn't (json became null meanwhile)
			{
				if (persist instanceof IBasicWebObject)
				{
					if (((IBasicWebObject)persist).getFlattenedJson() != null)
					{
						underlyingPropertySource = new PDPropertySource(pContext, readOnly, propertyDescription);
					}
					else
					{
						underlyingPropertySource = null;
					}
				}
				else if (persist == null)
				{
					// value of this property is null probably - so we don't show nested contents
					underlyingPropertySource = null;
				}
				else
				{
					underlyingPropertySource = null;
					ServoyLog.logError("Unexpected. Persist of custom json object handler is not instance of IBasicWebObject: (" + propertyDescription + ", " +
						persistContext + ")", new RuntimeException());
				}
			}
			return underlyingPropertySource;
		}

		@Override
		public IPropertyDescriptor[] createPropertyDescriptors()
		{
			PDPropertySource underlying = getUnderlyingPropertySource();
			return underlying != null ? underlying.getPropertyDescriptors() : new IPropertyDescriptor[0];
		}

		@Override
		public Object getPropertyValue(Object id)
		{
			PDPropertySource underlying = getUnderlyingPropertySource();
			return underlying != null ? underlying.getPropertyValue(id) : null;
		}

		@Override
		public Object setComplexPropertyValue(Object id, Object v)
		{
			Object val = ServoyJSONObject.adjustJavascriptNULLForOrgJSON(v);
			PDPropertySource underlying = getUnderlyingPropertySource();
			if (underlying != null) underlying.setPropertyValue(id, val);
			return getEditableValue();
		}

		@Override
		public void defaultSetProperty(Object id, Object value)
		{
			PDPropertySource underlying = getUnderlyingPropertySource();
			underlying.defaultSetProperty(id, value);
		}

		@Override
		public Object defaultGetProperty(Object id)
		{
			PDPropertySource underlying = getUnderlyingPropertySource();
			return underlying.defaultGetProperty(id);
		}

		@Override
		public boolean defaultIsPropertySet(Object id)
		{
			PDPropertySource underlying = getUnderlyingPropertySource();
			return underlying.defaultIsPropertySet(id);
		}

		@Override
		public void defaultResetProperty(Object id)
		{
			underlyingPropertySource.defaultResetProperty(id);
		}

	}

	@Override
	public void resetPropertyValue(ISetterAwarePropertySource propertySource)
	{
		if (propertyDescription.hasDefault())
		{
			Object defValue = propertyDescription.getDefaultValue();
			JSONObject toSet = null;
			if (defValue instanceof String)
			{
				try
				{
					toSet = new ServoyJSONObject((String)defValue, false);
				}
				catch (JSONException e)
				{
					ServoyLog.logError(e);
				}
			}
			propertySource.setPropertyValue(getId(), toSet);
		}
		else propertySource.defaultResetProperty(getId());
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.eclipse.ui.property.ObjectTypePropertyController#getLabelText(java.lang.Object)
	 */
	@Override
	protected String getLabelText(Object element)
	{
		return (element == null ? "null" : "{...}");
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.servoy.eclipse.ui.property.ObjectTypePropertyController#getMainObjectTextConverter()
	 */
	@Override
	protected IObjectTextConverter getMainObjectTextConverter()
	{
		return OBJECT_TEXT_CONVERTER;
	}

}
