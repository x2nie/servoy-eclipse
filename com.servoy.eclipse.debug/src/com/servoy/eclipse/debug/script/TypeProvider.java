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
package com.servoy.eclipse.debug.script;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.dltk.javascript.typeinfo.ITypeInfoContext;
import org.eclipse.dltk.javascript.typeinfo.ITypeProvider;
import org.eclipse.dltk.javascript.typeinfo.model.Member;
import org.eclipse.dltk.javascript.typeinfo.model.Property;
import org.eclipse.dltk.javascript.typeinfo.model.Type;
import org.eclipse.dltk.javascript.typeinfo.model.TypeInfoModelFactory;
import org.eclipse.dltk.javascript.typeinfo.model.TypeKind;
import org.eclipse.emf.common.util.EList;
import org.eclipse.jface.resource.ImageDescriptor;
import org.mozilla.javascript.JavaMembers;

import com.servoy.eclipse.core.Activator;
import com.servoy.eclipse.core.ServoyLog;
import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.FormController.JSForm;
import com.servoy.j2db.IApplication;
import com.servoy.j2db.dataprocessing.FoundSet;
import com.servoy.j2db.dataprocessing.JSDataSet;
import com.servoy.j2db.dataprocessing.Record;
import com.servoy.j2db.persistence.AggregateVariable;
import com.servoy.j2db.persistence.Column;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.FormElementGroup;
import com.servoy.j2db.persistence.IColumnTypes;
import com.servoy.j2db.persistence.IDataProvider;
import com.servoy.j2db.persistence.IFormElement;
import com.servoy.j2db.persistence.IPersist;
import com.servoy.j2db.persistence.IScriptProvider;
import com.servoy.j2db.persistence.IServer;
import com.servoy.j2db.persistence.Relation;
import com.servoy.j2db.persistence.RepositoryException;
import com.servoy.j2db.persistence.ScriptCalculation;
import com.servoy.j2db.persistence.ScriptMethod;
import com.servoy.j2db.persistence.ScriptVariable;
import com.servoy.j2db.persistence.Table;
import com.servoy.j2db.scripting.GroupScriptObject;
import com.servoy.j2db.scripting.IExecutingEnviroment;
import com.servoy.j2db.scripting.InstanceJavaMembers;
import com.servoy.j2db.scripting.ScriptObjectRegistry;
import com.servoy.j2db.smart.dataui.SwingItemFactory;
import com.servoy.j2db.util.ServoyException;
import com.servoy.j2db.util.Utils;

@SuppressWarnings("nls")
public class TypeProvider extends TypeCreator implements ITypeProvider
{
	private final ConcurrentMap<String, DynamicTypeFiller> dynamicTypeCreator = new ConcurrentHashMap<String, DynamicTypeFiller>();
	private final Set<String> constantOnly = new HashSet<String>();

	public TypeProvider()
	{
		addType(Record.JS_RECORD, Record.class);
		addType("JSDataSet", JSDataSet.class);
		addType(IExecutingEnviroment.TOPLEVEL_SERVOY_EXCEPTION, ServoyException.class);
		addType("controller", JSForm.class);

		addScopeType(FoundSet.JS_FOUNDSET, new FoundSetCreator());
		addScopeType("Form", new FormScopeCreator());
		addScopeType("Elements", new ElementsScopeCreator());

		dynamicTypeCreator.put(FoundSet.JS_FOUNDSET, new DataProviderFiller());
		dynamicTypeCreator.put(Record.JS_RECORD, new DataProviderFiller());
		dynamicTypeCreator.put("Form", new FormScopeFiller());
		dynamicTypeCreator.put("Elements", new ElementsScopeFiller());

	}

	@Override
	protected synchronized void initalize()
	{
		super.initalize();
		if (constantOnly.size() == 0)
		{
			Set<String> typeNames = getTypeNames(null);
			for (String name : typeNames)
			{
				Class< ? > cls = getTypeClass(name);
				if (cls != null)
				{
					ArrayList<String> al = new ArrayList<String>();
					JavaMembers javaMembers = ScriptObjectRegistry.getJavaMembers(cls, null);
					if (javaMembers != null)
					{
						Object[] members = javaMembers.getIds(false);
						for (Object element : members)
						{
							al.add((String)element);
						}
						if (javaMembers instanceof InstanceJavaMembers)
						{
							al.removeAll(((InstanceJavaMembers)javaMembers).getGettersAndSettersToHide());
						}
						else
						{
							al.removeAll(objectMethods);
						}
						if (al.size() == 0) constantOnly.add(name);
					}
				}
			}
		}
	}

	@Override
	protected boolean constantsOnly(String name)
	{
		return false;
	}

	public Set<String> listTypes(ITypeInfoContext context, String prefix)
	{
		Set<String> names = getTypeNames(prefix);
		names.remove("Elements");
		names.remove("controller");
		names.removeAll(constantOnly);
		return names;
	}

	/**
	 * @see org.eclipse.dltk.javascript.typeinfo.ITypeProvider#getType(org.eclipse.dltk.javascript.typeinfo.ITypeInfoContext, java.lang.String)
	 */
	@Override
	public Type getType(ITypeInfoContext context, String typeName)
	{
		return super.getType(context, getRealName(typeName));
	}

	/**
	 * @param typeName
	 * @return
	 */
	private String getRealName(String typeName)
	{
		if ("Record".equals(typeName)) return Record.JS_RECORD;
		if ("FoundSet".equals(typeName)) return FoundSet.JS_FOUNDSET;
		return typeName;
	}

	@Override
	protected Type createDynamicType(ITypeInfoContext context, String typeName)
	{
		// is it a 'generified' type
		int index = typeName.indexOf('<');
		if (index != -1)
		{
			String classType = typeName.substring(0, index);
			Type type = createType(context, classType, typeName);
			if (type == null) type = createDynamicType(context, classType);
			type.setName(typeName);
			DynamicTypeFiller filler = dynamicTypeCreator.get(classType);
			if (type != null && filler != null)
			{
				filler.fillType(type, context, typeName.substring(index + 1, typeName.length() - 1));
			}
			return type;
		}
		return super.createDynamicType(context, typeName);
	}

	/**
	 * @see com.servoy.eclipse.debug.script.TypeCreator#getTypeName(java.lang.String, java.lang.Class, java.lang.Class, java.lang.String)
	 */
	@Override
	protected String getMemberTypeName(ITypeInfoContext context, String memberName, Class< ? > memberReturnType, String objectTypeName)
	{
		int index = objectTypeName.indexOf('<');
		if (index != -1)
		{
			DynamicTypeFiller dynamicTypeFiller = dynamicTypeCreator.get(getRealName(memberReturnType.getSimpleName()));
			if (dynamicTypeFiller != null)
			{
				String memberType = dynamicTypeFiller.generateMemberType(context, memberName, memberReturnType,
					objectTypeName.substring(index + 1, objectTypeName.length() - 1));
				if (memberType != null) return memberType;
			}
		}
		return super.getMemberTypeName(context, memberName, memberReturnType, objectTypeName);
	}


	private interface DynamicTypeFiller
	{
		public void fillType(Type type, ITypeInfoContext context, String config);

		/**
		 * @param context TODO
		 * @param memberName
		 * @param memberReturnType
		 * @param substring
		 * @param i
		 * @return
		 */
		public String generateMemberType(ITypeInfoContext context, String memberName, Class< ? > memberReturnType, String config);
	}

	private class FormScopeFiller implements DynamicTypeFiller
	{

		public void fillType(Type type, ITypeInfoContext context, String config)
		{
			FlattenedSolution fs = getFlattenedSolution(context);
			if (fs != null)
			{
				Form form = fs.getForm(config);
				if (form != null)
				{
					try
					{
						EList<Member> members = type.getMembers();
						Form formToUse = form;
						if (form.getExtendsFormID() > 0)
						{
							formToUse = fs.getFlattenedForm(form);
							members.add(createProperty(context, "_super", true, "SuperForm", FORM_IMAGE)); // TODO super form scope
						}
						Table table = formToUse.getTable();
						if (table != null)
						{
							// first adjust the foundset member.
							for (Member member : members)
							{
								if (member.getName().equals("foundset"))
								{
									member.setType(context.getType(FoundSet.JS_FOUNDSET + '<' + ElementResolver.FOUNDSET_TABLE_CONFIG + table.getServerName() +
										'.' + table.getName() + '>'));
									break;
								}
							}
						}

						Iterator<ScriptMethod> scriptMethods = formToUse.getScriptMethods(false);
						while (scriptMethods.hasNext())
						{
							ScriptMethod sm = scriptMethods.next();
							members.add(createMethod(context, sm, FORM_METHOD_IMAGE, sm.getSerializableRuntimeProperty(IScriptProvider.FILENAME)));
						}

						// form variables
						addDataProviders(formToUse.getScriptVariables(false), members, context);

						if (!isLoginSolution(context))
						{
							// data providers
							Map<String, IDataProvider> allDataProvidersForTable = fs.getAllDataProvidersForTable(formToUse.getTable());

							if (allDataProvidersForTable != null)
							{
								addDataProviders(allDataProvidersForTable.values().iterator(), members, context);
							}

							// relations
							addRelations(context, fs, members, fs.getRelations(formToUse.getTable(), true, false));
						}

						// element scope
						members.add(createProperty(context, "elements", true, "Elements<" + formToUse.getName() + '>', PROPERTY));
					}
					catch (Exception e)
					{
						ServoyLog.logError(e);
					}
				}
			}
		}

		public String generateMemberType(ITypeInfoContext context, String memberName, Class< ? > memberReturnType, String config)
		{
			return null;
		}

	}


	private class ElementsScopeFiller implements DynamicTypeFiller
	{
		private final ConcurrentHashMap<String, Type> elementTypes = new ConcurrentHashMap<String, Type>();

		public void fillType(Type type, ITypeInfoContext context, String config)
		{
			FlattenedSolution fs = getFlattenedSolution(context);
			if (fs != null)
			{
				Form form = fs.getForm(config);
				if (form != null)
				{
					try
					{
						EList<Member> members = type.getMembers();
						Form formToUse = form;
						if (form.getExtendsFormID() > 0)
						{
							formToUse = fs.getFlattenedForm(form);
						}
						IApplication application = Activator.getDefault().getDesignClient();
						Iterator<IPersist> formObjects = formToUse.getAllObjects();
						while (formObjects.hasNext())
						{
							IPersist persist = formObjects.next();
							if (persist instanceof IFormElement)
							{
								IFormElement formElement = (IFormElement)persist;
								if (!Utils.stringIsEmpty(formElement.getName()))
								{
									Class< ? > persistClass = SwingItemFactory.getPersistClass(application, persist);
									members.add(createProperty(formElement.getName(), true, getElementType(context, persistClass), null, PROPERTY));
								}
								else if (formElement.getGroupID() != null)
								{
									String groupName = FormElementGroup.getName(formElement.getGroupID());
									if (groupName != null)
									{
										members.add(createProperty(groupName, true, getElementType(context, GroupScriptObject.class), null, PROPERTY));
									}
								}
							}
						}
					}
					catch (Exception e)
					{
						ServoyLog.logError(e);
					}
				}
			}
		}

		public String generateMemberType(ITypeInfoContext context, String memberName, Class< ? > memberReturnType, String config)
		{
			// TODO Auto-generated method stub
			return null;
		}

		private Type getElementType(ITypeInfoContext context, Class< ? > cls)
		{
			Type type = elementTypes.get(cls.getSimpleName());
			if (type == null)
			{
				type = createType(context, cls.getSimpleName(), cls);
				Type t = elementTypes.putIfAbsent(cls.getSimpleName(), type);
				if (t != null) return t;
			}
			return type;
		}

	}

	private class DataProviderFiller implements DynamicTypeFiller
	{
		/**
		 * @see com.servoy.eclipse.debug.script.TypeProvider.DynamicTypeFiller#fillType(org.eclipse.dltk.javascript.typeinfo.model.Type, org.eclipse.dltk.javascript.typeinfo.ITypeInfoContext, java.lang.String)
		 */
		public void fillType(Type type, ITypeInfoContext context, String config)
		{
			Table table = null;
			FlattenedSolution fs = getFlattenedSolution(context);

			if (config.startsWith(ElementResolver.FOUNDSET_TABLE_CONFIG))
			{
				// table foundset
				int index = config.indexOf('.');
				if (index > 0)
				{
					String serverName = config.substring(ElementResolver.FOUNDSET_TABLE_CONFIG.length(), index);
					String tableName = config.substring(index + 1);

					if (fs != null)
					{
						try
						{
							IServer server = fs.getSolution().getRepository().getServer(serverName);
							if (server != null)
							{
								table = (Table)server.getTable(tableName);
							}
						}
						catch (Exception e)
						{
							ServoyLog.logError(e);
						}

					}
				}
			}
			else
			{
				// relation
				try
				{
					table = fs.getRelation(config).getForeignTable();
				}
				catch (RepositoryException e)
				{
					ServoyLog.logError(e);
				}
			}

			if (table != null)
			{
				try
				{
					EList<Member> members = type.getMembers();
					Map<String, IDataProvider> allDataProvidersForTable = fs.getAllDataProvidersForTable(table);
					if (allDataProvidersForTable != null)
					{
						addDataProviders(allDataProvidersForTable.values().iterator(), members, context);
					}

					// relations
					addRelations(context, fs, members, fs.getRelations(table, true, false));
				}
				catch (RepositoryException e)
				{
					ServoyLog.logError(e);
				}
			}

		}

		/**
		 * @see com.servoy.eclipse.debug.script.TypeProvider.DynamicTypeFiller#generateMemberType(java.lang.String, java.lang.Class, java.lang.String, int)
		 */
		public String generateMemberType(ITypeInfoContext context, String memberName, Class< ? > memberReturnType, String config)
		{
			if (memberReturnType == Record.class)
			{
				return Record.JS_RECORD + '<' + config + '>';
			}
			if (memberReturnType == FoundSet.class)
			{
				if (memberName.equals("unrelated"))
				{
					if (!config.startsWith(ElementResolver.FOUNDSET_TABLE_CONFIG))
					{
						// its really a relation, unrelate it.
						FlattenedSolution fs = TypeCreator.getFlattenedSolution(context);
						if (fs != null)
						{
							Relation relation = fs.getRelation(config);
							if (relation != null)
							{
								return FoundSet.JS_FOUNDSET + '<' + ElementResolver.FOUNDSET_TABLE_CONFIG + relation.getForeignServerName() + '.' +
									relation.getForeignTableName() + '>';
							}
						}
						return FoundSet.JS_FOUNDSET;
					}
				}
				return FoundSet.JS_FOUNDSET + '<' + config + '>';
			}
			return null;
		}
	}

	private class FoundSetCreator implements IScopeTypeCreator
	{

		public Type createType(ITypeInfoContext context, String fullTypeName)
		{
			Type type = TypeProvider.this.createType(context, FoundSet.JS_FOUNDSET, FoundSet.class);
			type.setAttribute(IMAGE_DESCRIPTOR, FOUNDSET_IMAGE);

			Property alldataproviders = TypeInfoModelFactory.eINSTANCE.createProperty();
			alldataproviders.setName("alldataproviders");
			alldataproviders.setDescription("the dataproviders array of this foundset");
			alldataproviders.setAttribute(IMAGE_DESCRIPTOR, SPECIAL_PROPERTY);
			type.getMembers().add(alldataproviders);

			return type;
		}

	}


	private static class FormScopeCreator implements IScopeTypeCreator
	{

		public Type createType(ITypeInfoContext context, String typeName)
		{
			Type type = TypeInfoModelFactory.eINSTANCE.createType();
			type.setName(typeName);
			type.setKind(TypeKind.JAVA);

			EList<Member> members = type.getMembers();

			boolean isLoginSolution = isLoginSolution(context);

			members.add(createProperty(context, "allnames", true, "Array", SPECIAL_PROPERTY));
			if (!isLoginSolution) members.add(createProperty(context, "alldataproviders", true, "Array", SPECIAL_PROPERTY));
			members.add(createProperty(context, "allmethods", true, "Array", SPECIAL_PROPERTY));
			if (!isLoginSolution) members.add(createProperty(context, "allrelations", true, "Array", SPECIAL_PROPERTY));
			members.add(createProperty(context, "allvariables", true, "Array", SPECIAL_PROPERTY));

			// controller and foundset
			members.add(createProperty(context, "controller", true, "controller", PROPERTY));
			if (!isLoginSolution) members.add(createProperty(context, "foundset", true, FoundSet.JS_FOUNDSET, FOUNDSET_IMAGE));
			type.setAttribute(IMAGE_DESCRIPTOR, FORM_IMAGE);
			return type;
		}
	}

	public static class ElementsScopeCreator implements IScopeTypeCreator
	{

		public Type createType(ITypeInfoContext context, String typeName)
		{
			Type type = TypeInfoModelFactory.eINSTANCE.createType();
			type.setName(typeName);
			type.setKind(TypeKind.JAVA);

			EList<Member> members = type.getMembers();

			members.add(createProperty(context, "allnames", true, "Array", SPECIAL_PROPERTY));
			members.add(createProperty(context, "length", true, "Number", PROPERTY));

			type.setAttribute(IMAGE_DESCRIPTOR, PROPERTY);
			return type;
		}
	}

	private static void addDataProviders(Iterator< ? extends IDataProvider> dataproviders, EList<Member> members, ITypeInfoContext context)
	{
		while (dataproviders.hasNext())
		{
			IDataProvider provider = dataproviders.next();
			Property property = TypeInfoModelFactory.eINSTANCE.createProperty();
			property.setName(provider.getDataProviderID());
			property.setAttribute(RESOURCE, provider);
			switch (provider.getDataProviderType())
			{
				case IColumnTypes.DATETIME :
					property.setType(context.getType("Date"));
					break;
				case IColumnTypes.INTEGER :
				case IColumnTypes.NUMBER :
					property.setType(context.getType("Number"));
					break;
				case IColumnTypes.TEXT :
					property.setType(context.getType("String"));
					break;
			}
			ImageDescriptor image = COLUMN_IMAGE;
			String variableType = "Column";
			if (provider instanceof AggregateVariable)
			{
				image = COLUMN_AGGR_IMAGE;
				variableType = "Aggregate (" + ((AggregateVariable)provider).getRootObject().getName() + ")";
			}
			else if (provider instanceof ScriptCalculation)
			{
				image = COLUMN_CALC_IMAGE;
				variableType = "Calculation (" + ((ScriptCalculation)provider).getRootObject().getName() + ")";
			}
			else if (provider instanceof ScriptVariable)
			{
				image = FORM_VARIABLE_IMAGE;
				variableType = "ScriptVariable";
				property.setAttribute(RESOURCE, ((ScriptVariable)provider).getSerializableRuntimeProperty(IScriptProvider.FILENAME));
			}
			property.setAttribute(IMAGE_DESCRIPTOR, image);
			property.setDescription(variableType);
			members.add(property);
		}
	}

	/**
	 * @param context
	 * @param fs
	 * @param members
	 * @param relations
	 * @throws RepositoryException
	 */
	private static void addRelations(ITypeInfoContext context, FlattenedSolution fs, EList<Member> members, Iterator<Relation> relations)
		throws RepositoryException
	{
		while (relations.hasNext())
		{
			Relation relation = relations.next();
			Property property = createProperty(relation.getName(), true, context.getType(FoundSet.JS_FOUNDSET + "<" + relation.getName() + ">"),
				getRelationDescription(relation, relation.getPrimaryDataProviders(fs), relation.getForeignColumns()), RELATION_IMAGE, relation);
			members.add(property);
		}
	}

	private static String getRelationDescription(Relation relation, IDataProvider[] primaryDataProviders, Column[] foreignColumns)
	{
		StringBuilder sb = new StringBuilder(150);
		if (relation.isGlobal())
		{
			sb.append("Global relation defined in solution: "); //$NON-NLS-1$
		}
		else if (primaryDataProviders.length == 0)
		{
			sb.append("Self referencing relation defined in solution:"); //$NON-NLS-1$
		}
		else
		{
			sb.append("Relation defined in solution: "); //$NON-NLS-1$
		}
		sb.append(relation.getRootObject().getName());
		if (relation.isGlobal() || primaryDataProviders.length == 0)
		{
			sb.append("<br/>On table: "); //$NON-NLS-1$
			sb.append(relation.getForeignServerName() + "->" + relation.getForeignTableName()); //$NON-NLS-1$
		}
		else
		{
			sb.append("<br/>From: "); //$NON-NLS-1$
//			sb.append(relation.getPrimaryDataSource());
			sb.append(relation.getPrimaryServerName() + "->" + relation.getPrimaryTableName()); //$NON-NLS-1$
			sb.append("<br/>To: "); //$NON-NLS-1$
			sb.append(relation.getForeignServerName() + "->" + relation.getForeignTableName()); //$NON-NLS-1$
		}
		sb.append("<br/>"); //$NON-NLS-1$
		if (primaryDataProviders.length != 0)
		{
			for (int i = 0; i < foreignColumns.length; i++)
			{
				sb.append("&nbsp;&nbsp;"); //$NON-NLS-1$
				sb.append((primaryDataProviders[i] != null) ? primaryDataProviders[i].getDataProviderID() : "unresolved");
				sb.append("->"); //$NON-NLS-1$
				sb.append((foreignColumns[i] != null) ? foreignColumns[i].getDataProviderID() : "unresolved");
				sb.append("<br/>"); //$NON-NLS-1$
			}
		}
		return sb.toString();
	}


}
