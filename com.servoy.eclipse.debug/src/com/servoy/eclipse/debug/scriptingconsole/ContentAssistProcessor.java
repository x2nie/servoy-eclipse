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

package com.servoy.eclipse.debug.scriptingconsole;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.dltk.codeassist.ICompletionEngine;
import org.eclipse.dltk.compiler.env.IModuleSource;
import org.eclipse.dltk.core.DLTKCore;
import org.eclipse.dltk.core.DLTKLanguageManager;
import org.eclipse.dltk.core.IModelElement;
import org.eclipse.dltk.core.ISourceModule;
import org.eclipse.dltk.javascript.core.JavaScriptLanguageToolkit;
import org.eclipse.dltk.ui.DLTKPluginImages;
import org.eclipse.dltk.ui.DLTKUILanguageManager;
import org.eclipse.dltk.ui.IDLTKUILanguageToolkit;
import org.eclipse.dltk.ui.templates.ScriptTemplateContext;
import org.eclipse.dltk.ui.templates.ScriptTemplateContextType;
import org.eclipse.dltk.ui.templates.ScriptTemplateProposal;
import org.eclipse.dltk.ui.text.completion.AbstractScriptCompletionProposal;
import org.eclipse.dltk.ui.text.completion.CompletionProposalComparator;
import org.eclipse.dltk.ui.text.completion.IScriptCompletionProposal;
import org.eclipse.dltk.ui.text.completion.ScriptCompletionProposalCollector;
import org.eclipse.dltk.utils.TextUtils;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateBuffer;
import org.eclipse.jface.text.templates.TemplateContext;
import org.eclipse.jface.text.templates.TemplateException;

import com.servoy.eclipse.core.ServoyModelManager;
import com.servoy.eclipse.debug.script.ValueCollectionProvider;
import com.servoy.eclipse.model.nature.ServoyProject;
import com.servoy.eclipse.model.repository.SolutionSerializer;
import com.servoy.j2db.IDebugClient;
import com.servoy.j2db.persistence.IRootObject;
import com.servoy.j2db.util.Pair;

/**
 * @author jcompagner
 *
 */
final class ContentAssistProcessor implements IContentAssistProcessor
{
	private final IActiveClientProvider provider;

	public ContentAssistProcessor(IActiveClientProvider provider)
	{
		this.provider = provider;
	}

	public String getErrorMessage()
	{
		return null;
	}

	public IContextInformationValidator getContextInformationValidator()
	{
		return null;
	}

	public char[] getContextInformationAutoActivationCharacters()
	{
		return null;
	}

	public char[] getCompletionProposalAutoActivationCharacters()
	{
		return new char[] { '.' };
	}

	public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset)
	{
		return null;
	}

	public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset)
	{
		IDebugClient selectedClient = provider.getSelectedClient();
		if (selectedClient != null)
		{
			final ICompletionEngine engine = DLTKLanguageManager.getCompletionEngine(JavaScriptLanguageToolkit.getDefault().getNatureId());
			if (engine == null)
			{
				return new ICompletionProposal[0];
			}
			StringBuilder sb = provider.getSelectedClientScript();
			String commandLine = ((ScriptConsoleViewer)viewer).getCommandLine();
			final String input = sb == null ? commandLine : sb.toString() + commandLine;

			String solutionName = selectedClient.getSolutionName();
			IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(solutionName);
			Pair<String, IRootObject> scopePair = ScriptConsole.getGlobalScope();
			if (scopePair != null)
			{
				ServoyProject servoyProject = ServoyModelManager.getServoyModelManager().getServoyModel().getServoyProject(scopePair.getRight().getName());
				if (servoyProject != null)
				{
					final IFile file = servoyProject.getProject().getFile(scopePair.getLeft() + SolutionSerializer.JS_FILE_EXTENSION);
					final IModelElement modelElement = DLTKCore.create(file);

					IModuleSource source = new IModuleSource()
					{

						public String getFileName()
						{
							return file.getName();
						}

						public String getSourceContents()
						{
							return input;
						}

						public IModelElement getModelElement()
						{
							return modelElement;
						}

						public char[] getContentsAsCharArray()
						{
							return input.toCharArray();
						}
					};

					ScriptCompletionProposalCollector collector = new ScriptCompletionProposalCollector(DLTKCore.create(project))
					{
						@Override
						protected String getNatureId()
						{
							return JavaScriptLanguageToolkit.getDefault().getNatureId();
						}
					};
					engine.setRequestor(collector);
					int caretPosition = ((ScriptConsoleViewer)viewer).getCaretPosition();
					// a bit of a hack to get full globals completion.
					ValueCollectionProvider.setGenerateFullGlobalCollection(Boolean.TRUE);
					try
					{
						caretPosition = caretPosition - ((ScriptConsoleViewer)viewer).getCommandLineOffset();
						if (sb != null) caretPosition += sb.length();
						engine.complete(source, caretPosition, 0);
					}
					finally
					{
						ValueCollectionProvider.setGenerateFullGlobalCollection(Boolean.FALSE);
					}
					ArrayList<ICompletionProposal> list = new ArrayList<ICompletionProposal>();
					String prefix = extractPrefix(viewer, offset);
					IRegion region = new Region(offset - prefix.length(), prefix.length());
					if (isValidLocation(viewer, region))
					{
						ScriptTemplateContextType contextType = new ScriptTemplateContextType()
						{
							/*
							 * (non-Javadoc)
							 * 
							 * @see org.eclipse.dltk.ui.templates.ScriptTemplateContextType#createContext(org.eclipse.jface.text.IDocument, int, int,
							 * org.eclipse.dltk.core.ISourceModule)
							 */
							@Override
							public ScriptTemplateContext createContext(IDocument document, int completionPosition, int length, ISourceModule sourceModule)
							{
								return new ScriptTemplateContext(this, document, completionPosition, length, sourceModule)
								{
									/*
									 * (non-Javadoc)
									 * 
									 * @see org.eclipse.dltk.ui.templates.ScriptTemplateContext#evaluate(org.eclipse.jface.text.templates.Template)
									 */
									@Override
									public TemplateBuffer evaluate(Template template) throws BadLocationException, TemplateException
									{
										if (!canEvaluate(template))
										{
											return null;
										}
										Template copy = template;
										final String[] lines = TextUtils.splitLines(copy.getPattern());
										if (lines.length > 1)
										{
											StringBuilder sb1 = new StringBuilder();
											for (String line : lines)
											{
												sb1.append(line);
											}
											copy = new Template(copy.getName(), copy.getDescription(), copy.getContextTypeId(), sb1.toString(),
												copy.isAutoInsertable());
										}
										return super.evaluate(copy);
									}
								};
							}
						};
						TemplateContext context = contextType.createContext(viewer.getDocument(), offset - prefix.length(), prefix.length(),
							(ISourceModule)modelElement);
						IDLTKUILanguageToolkit languageToolkit = DLTKUILanguageManager.getLanguageToolkit(JavaScriptLanguageToolkit.getDefault().getNatureId());
						Template[] templates = languageToolkit.getEditorTemplates().getTemplateStore().getTemplates();
						for (Template template : templates)
						{
							if (template.getName().startsWith(prefix))
							{
								list.add(new ScriptTemplateProposal(template, context, region, DLTKPluginImages.get(DLTKPluginImages.IMG_OBJS_TEMPLATE), 1));
							}
						}
					}

					IScriptCompletionProposal[] scriptCompletionProposals = collector.getScriptCompletionProposals();

					int commandLineStart = ((ScriptConsoleViewer)viewer).getCommandLineOffset() - (sb != null ? sb.length() : 0);
					for (IScriptCompletionProposal scriptCompletionProposal : scriptCompletionProposals)
					{
						int replacementOffset = ((AbstractScriptCompletionProposal)scriptCompletionProposal).getReplacementOffset();
						((AbstractScriptCompletionProposal)scriptCompletionProposal).setReplacementOffset(commandLineStart + replacementOffset);
					}
					list.addAll(Arrays.asList(scriptCompletionProposals));
					Collections.sort(list, new CompletionProposalComparator());
					return list.toArray(new ICompletionProposal[list.size()]);
				}
			}
		}
		return new ICompletionProposal[0];
	}

	protected boolean isValidLocation(ITextViewer viewer, IRegion region)
	{
		try
		{
			final String trigger = getTrigger(viewer, region);
			final char[] ignore = new char[] { '.', ':', '@', '$' };
			for (char element : ignore)
			{
				if (trigger.indexOf(element) != -1)
				{
					return false;
				}
			}
		}
		catch (BadLocationException e)
		{
			if (DLTKCore.DEBUG)
			{
				e.printStackTrace();
			}
			return false;
		}
		return true;
	}

	protected String getTrigger(ITextViewer viewer, IRegion region) throws BadLocationException
	{
		final IDocument doc = viewer.getDocument();
		final int regionEnd = region.getOffset() + region.getLength();
		final IRegion line = doc.getLineInformationOfOffset(regionEnd);
		final String s = doc.get(line.getOffset(), regionEnd - line.getOffset());
		final int spaceIndex = s.lastIndexOf(' ');
		if (spaceIndex != -1)
		{
			return s.substring(spaceIndex);
		}
		else
		{
			return s;
		}
	}

	protected String extractPrefix(ITextViewer viewer, int offset)
	{
		int i = offset;
		IDocument document = viewer.getDocument();
		if (i > document.getLength()) return "";

		try
		{
			while (i > 0)
			{
				char ch = document.getChar(i - 1);
				if (!Character.isJavaIdentifierPart(ch)) break;
				i--;
			}

			return document.get(i, offset - i);
		}
		catch (BadLocationException e)
		{
			return "";
		}
	}
}