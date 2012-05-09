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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.views.properties.IPropertyDescriptor;

import com.servoy.eclipse.model.util.ModelUtils;
import com.servoy.eclipse.ui.dialogs.MethodDialog;
import com.servoy.eclipse.ui.dialogs.MethodDialog.MethodListOptions;
import com.servoy.eclipse.ui.editors.IValueEditor;
import com.servoy.eclipse.ui.editors.MethodCellEditor;
import com.servoy.eclipse.ui.labelproviders.AccesCheckingContextDelegateLabelProvider;
import com.servoy.eclipse.ui.labelproviders.FormContextDelegateLabelProvider;
import com.servoy.eclipse.ui.labelproviders.MethodLabelProvider;
import com.servoy.eclipse.ui.labelproviders.SolutionContextDelegateLabelProvider;
import com.servoy.eclipse.ui.util.EditorUtil;
import com.servoy.eclipse.ui.views.solutionexplorer.actions.NewMethodAction;
import com.servoy.j2db.persistence.AbstractBase;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.IPersist;
import com.servoy.j2db.persistence.IRepository;
import com.servoy.j2db.persistence.IScriptProvider;
import com.servoy.j2db.persistence.MethodArgument;
import com.servoy.j2db.persistence.MethodTemplate;
import com.servoy.j2db.persistence.Table;
import com.servoy.j2db.util.SafeArrayList;
import com.servoy.j2db.util.Utils;

/**
 * Property controller for method properties, subproperties are instance arguments
 * 
 * @author rgansevles
 * 
 */
public class MethodPropertyController<P> extends PropertyController<P, Object>
{
	private final PersistContext persistContext;
	private final MethodListOptions options;


	public MethodPropertyController(String id, String displayName, PersistContext persistContext, MethodListOptions options)
	{
		super(id, displayName);
		this.options = options;
		this.persistContext = persistContext;
		setLabelProvider(new AccesCheckingContextDelegateLabelProvider(new SolutionContextDelegateLabelProvider(new FormContextDelegateLabelProvider(
			new MethodLabelProvider(persistContext, true, !options.includeDefault), persistContext.getContext()))));
		setSupportsReadonly(true);
	}

	@Override
	public CellEditor createPropertyEditor(Composite parent)
	{
		ILabelProvider methodLabelProvider = new AccesCheckingContextDelegateLabelProvider(new FormContextDelegateLabelProvider(new MethodLabelProvider(
			persistContext, false, !options.includeDefault), persistContext.getContext()));
		return new MethodCellEditor(parent, methodLabelProvider, new MethodValueEditor(persistContext), persistContext, getId(), false, // readonly is handled in openDialogBox below
			options)
		{
			@Override
			public MethodWithArguments openDialogBox(Control cellEditorWindow)
			{
				if (MethodPropertyController.this.isReadOnly())
				{
					MethodWithArguments val = (MethodWithArguments)getValue();
					if (persistContext.getContext() instanceof Form)
					{
						// val.methodId is the id of the actual called method according to form inheritance (generated by FormInheritenceMethodConverter)
						IScriptProvider scriptMethod = ModelUtils.getScriptMethod(persistContext.getPersist(), persistContext.getContext(), val.table,
							val.methodId);
						if (scriptMethod != null && !scriptMethod.getParent().getUUID().equals(persistContext.getContext().getUUID()) &&
							scriptMethod.getParent() instanceof Form &&
							MessageDialog.openQuestion(getControl().getShell(), "Method property of a super form", "Overwrite it with a method in this form?"))
						{
							// the context is the currently viewed form
							NewMethodAction.createNewMethod(getControl().getShell(), persistContext.getContext(), getId().toString(), true,
								scriptMethod.getName(), null);
							// Note: the original value is returned, but FormInheritenceMethodConverter.getScriptMethod() will find 
							// the new method via the form hierarchy
						}
					}
					// must always return original value (readonly)
					return val;
				}
				return super.openDialogBox(cellEditorWindow);
			}
		};
	}

	public static IPropertyDescriptor[] createMethodPropertyDescriptors(IScriptProvider scriptMethod, final IPersist context, final String methodKey)
	{
		if (!(scriptMethod instanceof AbstractBase))
		{
			return new IPropertyDescriptor[0];
		}
		List<IPropertyDescriptor> descs = new ArrayList<IPropertyDescriptor>();

		MethodArgument[] formalArguments = ((AbstractBase)scriptMethod).getRuntimeProperty(IScriptProvider.METHOD_ARGUMENTS);
		final MethodTemplate template = MethodTemplate.getTemplate(scriptMethod.getClass(), methodKey);
		int nargs = formalArguments != null && (template.getArguments() == null || formalArguments.length > template.getArguments().length)
			? formalArguments.length : ((template.getArguments() == null) ? 0 : template.getArguments().length);
		for (int i = 0; i < nargs; i++)
		{
			PropertyController<String, String> propertyController;
			if (template.getArguments() != null && template.getArguments().length > i)
			{
				// arguments defined in template, will be overridden at runtime
				final MethodArgument templateArgument = template.getArguments()[i];
				String argName = (formalArguments != null && i < formalArguments.length) ? formalArguments[i].getName() : "arguments[" + i + ']'; //$NON-NLS-1$
				propertyController = new PropertyController<String, String>(Integer.valueOf(i), argName, null, new LabelProvider()
				{
					@Override
					public String getText(Object element)
					{
						return templateArgument.getName() + " {" + templateArgument.getType() + '}'; //$NON-NLS-1$
					}
				}, null);
				propertyController.setDescription(templateArgument.getDescription());
			}
			else
			{
				final int index = i;
				propertyController = new PropertyController<String, String>(Integer.valueOf(i), formalArguments[i].getName(), null, new LabelProvider()
				{
					@Override
					public String getText(Object element)
					{
						if (element == null || "".equals(element)) //$NON-NLS-1$
						{
							// argument is not set in method call in subform, show inherited value
							List<Form> formHierarchy = ModelUtils.getEditingFlattenedSolution(context).getFormHierarchy(
								(Form)context.getAncestor(IRepository.FORMS));
							for (Form form : formHierarchy)
							{
								List<Object> instanceMethodArguments = form.getInstanceMethodArguments(methodKey);
								if (instanceMethodArguments != null && instanceMethodArguments.size() > index)
								{
									Object inherited = instanceMethodArguments.get(index);
									if (inherited != null)
									{
										return inherited.toString() + " [" + form.getName() + ']'; //$NON-NLS-1$
									}
								}
							}
						}
						return super.getText(element);
					}
				}, new ICellEditorFactory()
				{
					public CellEditor createPropertyEditor(Composite parent)
					{
						return new TextCellEditor(parent);
					}
				});
			}
			descs.add(propertyController);
		}
		return descs.toArray(new IPropertyDescriptor[descs.size()]);
	}

	public static void setInstancMethodArguments(IPersist persist, Object id, List<Object> arguments)
	{
		if (persist instanceof AbstractBase)
		{
			int len = 0;
			for (int i = 0; arguments != null && i < arguments.size(); i++)
			{
				// find the index of the last non-null entry
				if (arguments.get(i) != null) len = i + 1;
			}
			// save a copy of the mwa.arguments list so that changes in mwa.arguments are not affecting customProperties
			((AbstractBase)persist).putInstanceMethodArguments(id.toString(), len == 0 ? null : new ArrayList<Object>(arguments.subList(0, len)));
		}
	}


	public static class MethodValueEditor implements IValueEditor<MethodWithArguments>
	{
		private final PersistContext persistContext;

		public MethodValueEditor(PersistContext persistContext)
		{
			this.persistContext = persistContext;
		}

		public boolean canEdit(MethodWithArguments value)
		{
			return ModelUtils.getScriptMethod(persistContext.getPersist(), persistContext.getContext(), value.table, value.methodId) != null;
		}

		public void openEditor(MethodWithArguments value)
		{
			EditorUtil.openScriptEditor(ModelUtils.getScriptMethod(persistContext.getPersist(), persistContext.getContext(), value.table, value.methodId),
				null, true);
		}
	}


	public static class MethodPropertySource extends ComplexPropertySource<MethodWithArguments>
	{
		private IPropertyDescriptor[] propertyDescriptors = null;
		private final PersistContext persistContext;
		private final String methodKey;
		private final Table table;

		public MethodPropertySource(ComplexProperty<MethodWithArguments> complexProperty, PersistContext persistContext, Table table, String methodKey,
			boolean readOnly)
		{
			super(complexProperty);
			this.persistContext = persistContext;
			this.table = table;
			this.methodKey = methodKey;
			setReadonly(readOnly);
		}

		@Override
		public IPropertyDescriptor[] createPropertyDescriptors()
		{
			if (propertyDescriptors == null)
			{
				MethodWithArguments mwa = getEditableValue();
				if (mwa == null)
				{
					return new IPropertyDescriptor[0];
				}

				int methodId;
				IPersist persist = persistContext.getPersist();
				if (MethodDialog.METHOD_DEFAULT.equals(mwa) && persist instanceof Form && ((Form)persist).getExtendsID() > 0)
				{
					// look for method in superform
					Form flattenedForm = ModelUtils.getEditingFlattenedSolution(persist).getFlattenedForm(persist);
					Object propertyValue = new PersistPropertySource(flattenedForm, null, false).getPropertyValue(methodKey);
					methodId = ((ComplexProperty<MethodWithArguments>)propertyValue).getValue().methodId;
				}
				else
				{
					methodId = mwa.methodId;
				}
				IScriptProvider scriptMethod = ModelUtils.getScriptMethod(persistContext.getPersist(), persistContext.getContext(), table, methodId);
				// make sure sub-properties are sorted in defined order
				propertyDescriptors = PropertyController.applySequencePropertyComparator(createMethodPropertyDescriptors(scriptMethod,
					persistContext.getContext(), methodKey));
			}
			return propertyDescriptors;
		}


		@Override
		public Object getPropertyValue(Object id)
		{
			int idx = ((Integer)id).intValue();
			MethodWithArguments mwa = getEditableValue();
			if (mwa.arguments == null || idx < 0 || idx >= mwa.arguments.size() || mwa.arguments.get(idx) == null)
			{
				return "";
			}
			return mwa.arguments.get(idx);
		}

		@Override
		protected MethodWithArguments setComplexPropertyValue(Object id, Object v)
		{
			int idx = ((Integer)id).intValue();
			MethodWithArguments mwa = getEditableValue();
			if (mwa.arguments == null)
			{
				mwa = new MethodWithArguments(mwa.methodId, new SafeArrayList<Object>(), mwa.table);
			}
			String value;
			if (v instanceof String && ((String)v).length() > 0)
			{
				Object parsed = Utils.parseJSExpression(v);
				if (parsed == null)
				{
					// not a bool, number or string, convert to quoted string
					value = Utils.makeJSExpression(v);
				}
				else
				{
					value = (String)v;
				}
			}
			else
			{
				value = null;
			}

			mwa.arguments.set(idx, value);
			return mwa;
		}
	}

}
