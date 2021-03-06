/*
 This file belongs to the Servoy development and deployment environment, Copyright (C) 1997-2011 Servoy BV

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

package com.servoy.eclipse.ui.search;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.dltk.codeassist.ISelectionEngine;
import org.eclipse.dltk.codeassist.ISelectionRequestor;
import org.eclipse.dltk.compiler.env.IModuleSource;
import org.eclipse.dltk.core.DLTKCore;
import org.eclipse.dltk.core.DLTKLanguageManager;
import org.eclipse.dltk.core.IModelElement;
import org.eclipse.dltk.core.ISourceModule;
import org.eclipse.dltk.core.ISourceRange;
import org.eclipse.dltk.javascript.core.JavaScriptNature;
import org.eclipse.dltk.javascript.typeinfo.model.Element;
import org.eclipse.search.core.text.TextSearchEngine;
import org.eclipse.search.core.text.TextSearchMatchAccess;
import org.eclipse.search.core.text.TextSearchRequestor;
import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.search.ui.text.FileTextSearchScope;

import com.servoy.eclipse.core.ServoyModelManager;
import com.servoy.eclipse.model.nature.ServoyProject;
import com.servoy.j2db.FlattenedSolution;
import com.servoy.j2db.persistence.Column;
import com.servoy.j2db.persistence.Form;
import com.servoy.j2db.persistence.IColumn;
import com.servoy.j2db.persistence.IDataProvider;
import com.servoy.j2db.persistence.Relation;
import com.servoy.j2db.persistence.RepositoryException;
import com.servoy.j2db.persistence.ValueList;
import com.servoy.j2db.util.Debug;

/**
 * An {@link ISearchQuery} implementation for finding dataproviders (columns) in dbi, frm, rel, val, js files.
 * 
 * @author acostache
 */
public class DataProviderSearch extends DLTKSearchEngineSearch
{
	private final IColumn dataprovider;
	private FlattenedSolution flattenedSolution = null;
	private String datasource = null;

	public DataProviderSearch(IColumn dataprovider)
	{
		this.dataprovider = dataprovider;
		flattenedSolution = ServoyModelManager.getServoyModelManager().getServoyModel().getFlattenedSolution();
		try
		{
			datasource = dataprovider.getTable().getDataSource();
		}
		catch (RepositoryException e)
		{
			datasource = "";
			Debug.log("Error initializing DataProviderSearch", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.search.ui.ISearchQuery#run(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public IStatus run(IProgressMonitor monitor) throws OperationCanceledException
	{
		ServoyProject activeProject = ServoyModelManager.getServoyModelManager().getServoyModel().getActiveProject();
		if (activeProject == null) return Status.OK_STATUS;
		IResource[] scopes = getAllScopes();
		TextSearchRequestor collector = getResultCollector();

		//search servoy  resources
		FileTextSearchScope servoyResourceScope = FileTextSearchScope.newSearchScope(scopes, new String[] { "*.val", "*.frm", "*.rel" }, true);
		TextSearchEngine.create().search(servoyResourceScope, collector, Pattern.compile("(\\b" + dataprovider.getDataProviderID() + "\\b)"), monitor);

		//search js files
		((DataProviderSearchCollector)collector).setEngine(DLTKLanguageManager.getSelectionEngine(JavaScriptNature.NATURE_ID));
		FileTextSearchScope scriptScope = FileTextSearchScope.newSearchScope(scopes, new String[] { "*.js" }, true);
		TextSearchEngine.create().search(scriptScope, collector, Pattern.compile("\\b" + dataprovider.getName() + "\\b"), monitor);

		return Status.OK_STATUS;
	}

	@Override
	protected TextSearchResultCollector createTextSearchCollector(AbstractTextSearchResult searchResult)
	{
		return new DataProviderSearchCollector(searchResult, dataprovider);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.search.ui.ISearchQuery#getLabel()
	 */
	public String getLabel()
	{
		return "Searching references to dataprovider '" + dataprovider.getName() + "'" + " (" + datasource + ")";
	}

	private class DataProviderSearchCollector extends TextSearchResultCollector
	{
		private ISelectionEngine engine;
		private final IColumn dataprovider;
		private boolean found;
		Pattern dataproviderIDPattern = null;
		Pattern relatedDataproviderIDPattern; // matches related pattern 
		private ISourceModule cachedJSModule = null;
		private String currentJSFile = "";

		public DataProviderSearchCollector(AbstractTextSearchResult result, IColumn dataprovider)
		{
			super(result);
			this.dataprovider = dataprovider;
			/*
			 * an explenation of this regex can be fond here:
			 * http://rick.measham.id.au/paste/explain.pl?regex=.*dataProviderID%5Cs*%5C%3A%5Cs*%5B%22%27%5DmyDataprovider%24
			 * 
			 * Matches the last occurrence of dataProviderID:"my_dataprovider in a multiline string. !Importat!: at the begining of the regex there is this
			 * pattern [.\\s]* , we cannot use only dot "." because it matches everything except new lines . We needed to add \s because \s matches also
			 * newlines
			 */
			dataproviderIDPattern = Pattern.compile("[.\\s]*dataProviderID\\s*\\:\\s*[\"']" + dataprovider.getDataProviderID() + "$", Pattern.MULTILINE);

			/*
			 * This regex is mostly the same as the previews one except in with the relation name, capturing the relation name in a group . Ex
			 * dataProviderID:"my_relation.my_dataprovider
			 */
			relatedDataproviderIDPattern = Pattern.compile("[.\\s]*dataProviderID\\s*\\:\\s*[\"']" + "(\\w+)\\." + dataprovider.getDataProviderID() + "$",
				Pattern.MULTILINE);

		}

		/**
		 * @param engine the engine to set
		 */
		public void setEngine(ISelectionEngine engine)
		{
			this.engine = engine;
			if (engine != null) engine.setRequestor(new ISelectionRequestor()
			{
				public void acceptModelElement(IModelElement element)
				{
				}

				public void acceptElement(Object element, ISourceRange range)
				{
				}

				public void acceptForeignElement(Object object)
				{
					if (object instanceof Element)
					{
						// TODO refactor this is the constant of TypeProvider.RESOURCE
						if (DataProviderSearchCollector.this.dataprovider.equals(((Element)object).getAttribute("servoy.RESOURCE")))
						{
							found = true;
						}
					}
				}
			});
		}

		@Override
		protected FileMatch createFileMatch(TextSearchMatchAccess matchRequestor, int matchOffset, LineElement lineElement)
		{
			FileMatch match = super.createFileMatch(matchRequestor, matchOffset, lineElement);
			if (engine != null)
			{
				found = false;
				// only recreate ISourceModule if the current match different js file
				if (!currentJSFile.equals(matchRequestor.getFile().getName()))
				{
					cachedJSModule = DLTKCore.createSourceModuleFrom(matchRequestor.getFile());
					currentJSFile = matchRequestor.getFile().getName();
				}
				engine.select((IModuleSource)cachedJSModule, matchOffset + 1, matchOffset + 1);
				if (!found)
				{
					match.setPossibleMatch(true);
				}
				return match;
			}
			else
			{// search in servoy resources
				String fileName = matchRequestor.getFile().getName();
				String[] tokens = fileName.split("\\.(?=[^\\.]+$)");
				if (tokens.length > 1)
				{
					if (tokens[1].equals("rel"))
					{
						if (isMatchRefferencedInRelation(tokens[0], false))
						{
							return match;
						}
					}
					else if (tokens[1].equals("val"))
					{
						if (isValueListMatch(tokens[0]))
						{
							return match;
						}
					}
					else if (tokens[1].equals("frm"))
					{
						if (isFormMatch(tokens[0], matchRequestor, matchOffset))
						{
							return match;
						}
					}
				}
				//no valid match found in servoy resources
				return null;
			}
		}

		private boolean isValueListMatch(String valueListName)
		{
			ValueList vl = flattenedSolution.getValueList(valueListName);
			if (vl != null)
			{
				if (datasource.equals(vl.getDataSource()))
				{ // table value list
					return true;
				}
				else
				{ //test if it is a related value list
					if (isMatchRefferencedInRelation(vl.getRelationName(), true))
					{
						return true;
					}

				}
			}
			return false;
		}

		private boolean isFormMatch(String formName, TextSearchMatchAccess matchRequestor, int matchOffset)
		{
			Form form = flattenedSolution.getForm(formName);
			if (form != null)
			{
				// get the whole string before the match , that is matchOffset - delta
				//needed because ex: dataProviderID:"my_relation.my_dataprovider ,  to get the relation name and verify if it is in that relation
				int delta = (matchOffset - 100) < 0 ? matchOffset : 100;
				String previewsMatchContent = matchRequestor.getFileContent(matchOffset - delta, delta + matchRequestor.getMatchLength());
				if (datasource.equals(form.getDataSource()))
				{ // form has the datasource
					Matcher matcher = dataproviderIDPattern.matcher(previewsMatchContent);
					if (matcher.find())
					{
						return true;
					}
					else
					{ // form may reference the dataprovider via self relation
						Matcher matcherRelated = relatedDataproviderIDPattern.matcher(previewsMatchContent);
						if (matcherRelated.find())
						{
							if (isMatchRefferencedInRelation(matcherRelated.group(1), true))
							{
								return true;
							}
						}
					}
				}
				else
				{ //form doesn't have the datasource but may reference dataproviderid via relation
					Matcher matcher = relatedDataproviderIDPattern.matcher(previewsMatchContent);
					if (matcher.find())
					{
						if (isMatchRefferencedInRelation(matcher.group(1), true))
						{
							return true;
						}
					}
				}
			}
			return false;
		}

		private boolean isMatchRefferencedInRelation(String relName, boolean onlyInForeignDatasource)
		{
			if (relName != null)
			{
				Relation rel = flattenedSolution.getRelation(relName);
				if (rel != null)
				{
					if (onlyInForeignDatasource)
					{
						if (datasource.equals(rel.getForeignDataSource()))
						{
							try
							{
								for (Column col : rel.getForeignColumns())
								{
									if (dataprovider.getDataProviderID().equals(col.getDataProviderID())) return true;
								}
							}
							catch (RepositoryException e)
							{
								Debug.log("Exception while getting foreign columns for relation: " + relName, e);
							}
						}
					}
					else
					{
						try
						{
							if (datasource.equals(rel.getForeignDataSource()))
							{
								for (Column col : rel.getForeignColumns())
								{
									if (dataprovider.getDataProviderID().equals(col.getDataProviderID())) return true;
								}
							}
							if (datasource.equals(rel.getPrimaryDataSource()))
							{
								for (IDataProvider col : rel.getPrimaryDataProviders(flattenedSolution))
								{
									if (dataprovider.getDataProviderID().equals(col.getDataProviderID())) return true;
								}
							}
						}
						catch (RepositoryException e)
						{
							Debug.log("Exception while getting foreign columns for relation: " + relName, e);
						}
					}
				}
			}
			return false;
		}
	}

}
