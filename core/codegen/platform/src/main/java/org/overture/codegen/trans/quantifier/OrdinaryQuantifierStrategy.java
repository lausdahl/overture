/*
 * #%~
 * VDM Code Generator
 * %%
 * Copyright (C) 2008 - 2014 Overture
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #~%
 */
package org.overture.codegen.trans.quantifier;

import java.util.List;

import org.overture.codegen.ir.SExpIR;
import org.overture.codegen.ir.SPatternIR;
import org.overture.codegen.ir.SStmIR;
import org.overture.codegen.ir.analysis.AnalysisException;
import org.overture.codegen.ir.declarations.AVarDeclIR;
import org.overture.codegen.ir.expressions.AIdentifierVarExpIR;
import org.overture.codegen.ir.ITempVarGen;
import org.overture.codegen.trans.IterationVarPrefixes;
import org.overture.codegen.trans.assistants.TransAssistantIR;
import org.overture.codegen.trans.iterator.ILanguageIterator;

public class OrdinaryQuantifierStrategy extends QuantifierBaseStrategy
{
	protected OrdinaryQuantifier quantifier;

	public OrdinaryQuantifierStrategy(
			TransAssistantIR transformationAssistant,
			SExpIR predicate, String resultVarName,
			OrdinaryQuantifier quantifier, ILanguageIterator langIterator,
			ITempVarGen tempGen, IterationVarPrefixes iteVarPrefixes)
	{
		super(transformationAssistant, predicate, resultVarName, langIterator, tempGen, iteVarPrefixes);
		this.quantifier = quantifier;
	}

	@Override
	public List<AVarDeclIR> getOuterBlockDecls(
			AIdentifierVarExpIR setVar, List<SPatternIR> patterns)
			throws AnalysisException
	{
		return firstBind ? packDecl(transAssist.consBoolVarDecl(resultVarName, quantifier == OrdinaryQuantifier.FORALL))
				: null;
	}

	@Override
	public SExpIR getForLoopCond(AIdentifierVarExpIR setVar,
			List<SPatternIR> patterns, SPatternIR pattern)
			throws AnalysisException
	{
		SExpIR left = langIterator.getForLoopCond(setVar, patterns, pattern);
		SExpIR right = transAssist.consBoolCheck(resultVarName, quantifier == OrdinaryQuantifier.EXISTS);

		return transAssist.consAndExp(left, right);
	}

	@Override
	public List<SStmIR> getForLoopStms(AIdentifierVarExpIR setVar,
			List<SPatternIR> patterns, SPatternIR pattern)
	{
		return lastBind ? packStm(transAssist.consBoolVarAssignment(predicate, resultVarName))
				: null;
	}
}
