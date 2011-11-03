package org.overture.pog.visitors;

import java.util.LinkedList;
import java.util.List;

import org.overture.ast.analysis.QuestionAnswerAdaptor;
import org.overture.ast.definitions.AExplicitFunctionDefinition;
import org.overture.ast.definitions.AImplicitFunctionDefinition;
import org.overture.ast.definitions.PDefinition;
import org.overture.ast.expressions.*;
import org.overture.ast.patterns.AIgnorePattern;
import org.overture.ast.patterns.PMultipleBind;
import org.overture.ast.types.AFunctionType;
import org.overture.ast.types.AUnknownType;
import org.overture.ast.types.PType;
import org.overture.ast.types.SMapType;
import org.overture.ast.types.SSeqType;
import org.overture.pog.assistants.PDefinitionAssistantPOG;
import org.overture.pog.obligations.CasesExhaustiveObligation;
import org.overture.pog.obligations.FiniteMapObligation;
import org.overture.pog.obligations.FunctionApplyObligation;
import org.overture.pog.obligations.MapApplyObligation;
import org.overture.pog.obligations.MapSetOfCompatibleObligation;
import org.overture.pog.obligations.NonEmptySeqObligation;
import org.overture.pog.obligations.POContextStack;
import org.overture.pog.obligations.POForAllContext;
import org.overture.pog.obligations.POForAllPredicateContext;
import org.overture.pog.obligations.ProofObligation;
import org.overture.pog.obligations.ProofObligationList;
import org.overture.pog.obligations.RecursiveObligation;
import org.overture.pog.obligations.SeqApplyObligation;
import org.overture.pog.obligations.SubTypeObligation;
import org.overture.typecheck.TypeCheckInfo;
import org.overture.typecheck.TypeComparator;
import org.overture.typecheck.visitors.TypeCheckVisitor;

public class PogExpVisitor extends QuestionAnswerAdaptor<POContextStack, ProofObligationList> {

	final private QuestionAnswerAdaptor<POContextStack, ProofObligationList> rootVisitor;
		
	public PogExpVisitor(PogVisitor pogVisitor) {
		this.rootVisitor = pogVisitor;
		
	}
		
	@Override
	// RWL see [1] pg. 57: 6.12 Apply Expressions
	public ProofObligationList caseAApplyExp(AApplyExp node,
			POContextStack question) {

		ProofObligationList obligations = new ProofObligationList();
		
		PType type = node.getType();
		if (type instanceof SMapType )
		{
			SMapType mapType = (SMapType)type;
			obligations.add(new MapApplyObligation(node.getRoot(), node.getArgs().get(0), question));
			PType aType = question.checkType(node.getArgs().get(0), node.getArgtypes().get(0));
			
			if (!TypeComparator.isSubType(aType, mapType.getFrom()))
			{
				obligations.add(new SubTypeObligation(node.getArgs().get(0), mapType.getFrom(), aType, question));
			}
		}
		
		if (! (type instanceof AUnknownType) && (type instanceof AFunctionType) )
		{
			AFunctionType funcType = (AFunctionType)type;
			// TODO Fix this get the name of the precondition
			String prename = "Precond";
			if (prename == null || !prename.equals(""))
			{
				obligations.add(new FunctionApplyObligation(node.getRoot(), node.getArgs(), prename, question));
			}
			
			int i = 0;
			List<PType> argTypes = node.getArgtypes();
			List<PExp> argList = node.getArgs();
			for(PType argType : argTypes)
			{
				argType = question.checkType(argList.get(i), argType);
				PType pt = funcType.getParameters().get(i);
				
				if (!TypeComparator.isSubType(argType, pt))
					obligations.add(new SubTypeObligation(argList.get(i), pt, argType, question));
				i++;
			}
			
			PDefinition recursive = node.getRecursive();
			if (recursive != null)
			{
				if (recursive instanceof AExplicitFunctionDefinition)
				{
					AExplicitFunctionDefinition def = (AExplicitFunctionDefinition)recursive;
					if (def.getMeasure() != null)
					{
						obligations.add(new RecursiveObligation(def, node, question));
					}
				}
				else if (recursive instanceof AImplicitFunctionDefinition)
				{
					AImplicitFunctionDefinition def = (AImplicitFunctionDefinition)recursive;
					if (def.getMeasure() != null)
					{
						obligations.add(new RecursiveObligation(def,node, question));
					}
					
				}
			}
		}
		
		if (type instanceof SSeqType)
		{
			obligations.add(new SeqApplyObligation(node.getRoot(), node.getArgs().get(0), question));
		}
		
		obligations.addAll(node.getRoot().apply(this, question));
		
		for(PExp arg: node.getArgs())
		{
			obligations.addAll(arg.apply(this, question));
		}
		
		return obligations;
	}

	@Override
	// see [1] pg. 179 unary expressions
	public ProofObligationList caseAHeadUnaryExp(AHeadUnaryExp node,
			POContextStack question) {

		ProofObligation po =new NonEmptySeqObligation(node.getExp(), question); 
		
		LinkedList<PDefinition> defs = new LinkedList<PDefinition>();
		ProofObligationList obligations = PDefinitionAssistantPOG.getProofObligations(defs, rootVisitor, question);
		obligations.add(po);
		
		return obligations;
	}

	@Override
	// [1] pg. 46 
	public ProofObligationList caseACasesExp(ACasesExp node,
			POContextStack question) {

		ProofObligationList obligations = new ProofObligationList();
		
		int count = 0;
		boolean hasIgnore = false;
		
		
		// handle each case
		for(ACaseAlternative alt : node.getCases())
		{
			
			if (alt.getPattern() instanceof AIgnorePattern)
				hasIgnore = true;
			
			obligations.addAll(alt.apply(this, question));
			count++;
		}
		
		if (node.getOthers() != null)
		{
			obligations.addAll(node.getOthers().apply(this, question));
		}
		
		for(int i = 0;i<count;i++) question.pop();
		
		if (node.getOthers() == null && !hasIgnore)
			obligations.add(new CasesExhaustiveObligation(node, question));
		
		return obligations;
	}

	@Override
	public ProofObligationList caseAMapCompMapExp(AMapCompMapExp node,
			POContextStack question) {
		ProofObligationList obligations = new ProofObligationList();
		
		obligations.add(new MapSetOfCompatibleObligation(node, question));
		
		question.push(new POForAllPredicateContext(node));
		obligations.addAll(node.getFirst().apply(this, question));
		question.pop();
		
		boolean finiteTest = false;
		
		for (PMultipleBind mb : node.getBindings())
		{
			obligations.addAll(mb.apply(this, question));
			if (mb instanceof PMultipleBind)
				finiteTest = true;
		}
		
		if (finiteTest)
			obligations.add(new FiniteMapObligation(node, node.getType(), question));
		
		PExp predicate = node.getPredicate();
		if (predicate != null)
		{
			question.push(new POForAllContext(node));
		}
		
		return obligations;
	}
	
	
	@Override
	// RWL see [1] pg. 179 A.5.4 Unary Expressions
	public ProofObligationList caseSUnaryExp(SUnaryExp node,
			POContextStack question) {
		
		ProofObligationList obligations = new ProofObligationList();
		
		
		
		return obligations;
	}

	@Override
	public ProofObligationList defaultSUnaryExp(SUnaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.defaultSUnaryExp(node, question);
	}

	@Override
	public ProofObligationList caseSBinaryExp(SBinaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseSBinaryExp(node, question);
	}

	@Override
	public ProofObligationList defaultSBinaryExp(SBinaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.defaultSBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseABooleanConstExp(ABooleanConstExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseABooleanConstExp(node, question);
	}

	@Override
	public ProofObligationList caseACharLiteralExp(ACharLiteralExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseACharLiteralExp(node, question);
	}

	@Override
	public ProofObligationList caseAElseIfExp(AElseIfExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAElseIfExp(node, question);
	}

	@Override
	public ProofObligationList caseAExists1Exp(AExists1Exp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAExists1Exp(node, question);
	}

	@Override
	public ProofObligationList caseAExistsExp(AExistsExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAExistsExp(node, question);
	}

	@Override
	public ProofObligationList caseAFieldExp(AFieldExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAFieldExp(node, question);
	}

	@Override
	public ProofObligationList caseAFieldNumberExp(AFieldNumberExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAFieldNumberExp(node, question);
	}

	@Override
	public ProofObligationList caseAForAllExp(AForAllExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAForAllExp(node, question);
	}

	@Override
	public ProofObligationList caseAFuncInstatiationExp(
			AFuncInstatiationExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAFuncInstatiationExp(node, question);
	}

	@Override
	public ProofObligationList caseAHistoryExp(AHistoryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAHistoryExp(node, question);
	}

	@Override
	public ProofObligationList caseAIfExp(AIfExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAIfExp(node, question);
	}

	@Override
	public ProofObligationList caseAIntLiteralExp(AIntLiteralExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAIntLiteralExp(node, question);
	}

	@Override
	public ProofObligationList caseAIotaExp(AIotaExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAIotaExp(node, question);
	}

	@Override
	public ProofObligationList caseAIsExp(AIsExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAIsExp(node, question);
	}

	@Override
	public ProofObligationList caseAIsOfBaseClassExp(AIsOfBaseClassExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAIsOfBaseClassExp(node, question);
	}

	@Override
	public ProofObligationList caseAIsOfClassExp(AIsOfClassExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAIsOfClassExp(node, question);
	}

	@Override
	public ProofObligationList caseALambdaExp(ALambdaExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseALambdaExp(node, question);
	}

	@Override
	public ProofObligationList caseALetBeStExp(ALetBeStExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseALetBeStExp(node, question);
	}

	@Override
	public ProofObligationList caseALetDefExp(ALetDefExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseALetDefExp(node, question);
	}

	@Override
	public ProofObligationList caseADefExp(ADefExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseADefExp(node, question);
	}

	@Override
	public ProofObligationList caseSMapExp(SMapExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseSMapExp(node, question);
	}

	@Override
	public ProofObligationList defaultSMapExp(SMapExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.defaultSMapExp(node, question);
	}

	@Override
	public ProofObligationList caseAMapletExp(AMapletExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAMapletExp(node, question);
	}

	@Override
	public ProofObligationList caseAMkBasicExp(AMkBasicExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAMkBasicExp(node, question);
	}

	@Override
	public ProofObligationList caseAMkTypeExp(AMkTypeExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAMkTypeExp(node, question);
	}

	@Override
	public ProofObligationList caseAMuExp(AMuExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAMuExp(node, question);
	}

	@Override
	public ProofObligationList caseANewExp(ANewExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseANewExp(node, question);
	}

	@Override
	public ProofObligationList caseANilExp(ANilExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseANilExp(node, question);
	}

	@Override
	public ProofObligationList caseANotYetSpecifiedExp(
			ANotYetSpecifiedExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseANotYetSpecifiedExp(node, question);
	}

	@Override
	public ProofObligationList caseAPostOpExp(APostOpExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAPostOpExp(node, question);
	}

	@Override
	public ProofObligationList caseAPreExp(APreExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAPreExp(node, question);
	}

	@Override
	public ProofObligationList caseAPreOpExp(APreOpExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAPreOpExp(node, question);
	}

	@Override
	public ProofObligationList caseAQuoteLiteralExp(AQuoteLiteralExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAQuoteLiteralExp(node, question);
	}

	@Override
	public ProofObligationList caseARealLiteralExp(ARealLiteralExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseARealLiteralExp(node, question);
	}

	@Override
	public ProofObligationList caseASameBaseClassExp(ASameBaseClassExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseASameBaseClassExp(node, question);
	}

	@Override
	public ProofObligationList caseASameClassExp(ASameClassExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseASameClassExp(node, question);
	}

	@Override
	public ProofObligationList caseASelfExp(ASelfExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseASelfExp(node, question);
	}

	@Override
	public ProofObligationList caseSSeqExp(SSeqExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseSSeqExp(node, question);
	}

	@Override
	public ProofObligationList defaultSSeqExp(SSeqExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.defaultSSeqExp(node, question);
	}

	@Override
	public ProofObligationList caseSSetExp(SSetExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseSSetExp(node, question);
	}

	@Override
	public ProofObligationList defaultSSetExp(SSetExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.defaultSSetExp(node, question);
	}

	@Override
	public ProofObligationList caseAStateInitExp(AStateInitExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAStateInitExp(node, question);
	}

	@Override
	public ProofObligationList caseAStringLiteralExp(AStringLiteralExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAStringLiteralExp(node, question);
	}

	@Override
	public ProofObligationList caseASubclassResponsibilityExp(
			ASubclassResponsibilityExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseASubclassResponsibilityExp(node, question);
	}

	@Override
	public ProofObligationList caseASubseqExp(ASubseqExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseASubseqExp(node, question);
	}

	@Override
	public ProofObligationList caseAThreadIdExp(AThreadIdExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAThreadIdExp(node, question);
	}

	@Override
	public ProofObligationList caseATimeExp(ATimeExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseATimeExp(node, question);
	}

	@Override
	public ProofObligationList caseATupleExp(ATupleExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseATupleExp(node, question);
	}

	@Override
	public ProofObligationList caseAUndefinedExp(AUndefinedExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAUndefinedExp(node, question);
	}

	@Override
	public ProofObligationList caseAVariableExp(AVariableExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAVariableExp(node, question);
	}

	@Override
	public ProofObligationList caseAAbsoluteUnaryExp(AAbsoluteUnaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAAbsoluteUnaryExp(node, question);
	}

	@Override
	public ProofObligationList caseACardinalityUnaryExp(
			ACardinalityUnaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseACardinalityUnaryExp(node, question);
	}

	@Override
	public ProofObligationList caseADistConcatUnaryExp(
			ADistConcatUnaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseADistConcatUnaryExp(node, question);
	}

	@Override
	public ProofObligationList caseADistIntersectUnaryExp(
			ADistIntersectUnaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseADistIntersectUnaryExp(node, question);
	}

	@Override
	public ProofObligationList caseADistMergeUnaryExp(ADistMergeUnaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseADistMergeUnaryExp(node, question);
	}

	@Override
	public ProofObligationList caseADistUnionUnaryExp(ADistUnionUnaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseADistUnionUnaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAElementsUnaryExp(AElementsUnaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAElementsUnaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAFloorUnaryExp(AFloorUnaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAFloorUnaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAIndicesUnaryExp(AIndicesUnaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAIndicesUnaryExp(node, question);
	}

	@Override
	public ProofObligationList caseALenUnaryExp(ALenUnaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseALenUnaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAMapDomainUnaryExp(AMapDomainUnaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAMapDomainUnaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAMapInverseUnaryExp(
			AMapInverseUnaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAMapInverseUnaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAMapRangeUnaryExp(AMapRangeUnaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAMapRangeUnaryExp(node, question);
	}

	@Override
	public ProofObligationList caseANotUnaryExp(ANotUnaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseANotUnaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAPowerSetUnaryExp(APowerSetUnaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAPowerSetUnaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAReverseUnaryExp(AReverseUnaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAReverseUnaryExp(node, question);
	}

	@Override
	public ProofObligationList caseATailUnaryExp(ATailUnaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseATailUnaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAUnaryMinusUnaryExp(
			AUnaryMinusUnaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAUnaryMinusUnaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAUnaryPlusUnaryExp(AUnaryPlusUnaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAUnaryPlusUnaryExp(node, question);
	}

	@Override
	public ProofObligationList caseSBooleanBinaryExp(SBooleanBinaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseSBooleanBinaryExp(node, question);
	}

	@Override
	public ProofObligationList defaultSBooleanBinaryExp(SBooleanBinaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.defaultSBooleanBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseACompBinaryExp(ACompBinaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseACompBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseADomainResByBinaryExp(
			ADomainResByBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseADomainResByBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseADomainResToBinaryExp(
			ADomainResToBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseADomainResToBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAEqualsBinaryExp(AEqualsBinaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAEqualsBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAInSetBinaryExp(AInSetBinaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAInSetBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAMapUnionBinaryExp(AMapUnionBinaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAMapUnionBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseANotEqualBinaryExp(ANotEqualBinaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseANotEqualBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseANotInSetBinaryExp(ANotInSetBinaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseANotInSetBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseSNumericBinaryExp(SNumericBinaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseSNumericBinaryExp(node, question);
	}

	@Override
	public ProofObligationList defaultSNumericBinaryExp(SNumericBinaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.defaultSNumericBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAPlusPlusBinaryExp(APlusPlusBinaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAPlusPlusBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAProperSubsetBinaryExp(
			AProperSubsetBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAProperSubsetBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseARangeResByBinaryExp(
			ARangeResByBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseARangeResByBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseARangeResToBinaryExp(
			ARangeResToBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseARangeResToBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseASeqConcatBinaryExp(
			ASeqConcatBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseASeqConcatBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseASetDifferenceBinaryExp(
			ASetDifferenceBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseASetDifferenceBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseASetIntersectBinaryExp(
			ASetIntersectBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseASetIntersectBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseASetUnionBinaryExp(ASetUnionBinaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseASetUnionBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAStarStarBinaryExp(AStarStarBinaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAStarStarBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseASubsetBinaryExp(ASubsetBinaryExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseASubsetBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAAndBooleanBinaryExp(
			AAndBooleanBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAAndBooleanBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAEquivalentBooleanBinaryExp(
			AEquivalentBooleanBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAEquivalentBooleanBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAImpliesBooleanBinaryExp(
			AImpliesBooleanBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAImpliesBooleanBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAOrBooleanBinaryExp(
			AOrBooleanBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAOrBooleanBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseADivNumericBinaryExp(
			ADivNumericBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseADivNumericBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseADivideNumericBinaryExp(
			ADivideNumericBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseADivideNumericBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAGreaterEqualNumericBinaryExp(
			AGreaterEqualNumericBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAGreaterEqualNumericBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAGreaterNumericBinaryExp(
			AGreaterNumericBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAGreaterNumericBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseALessEqualNumericBinaryExp(
			ALessEqualNumericBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseALessEqualNumericBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseALessNumericBinaryExp(
			ALessNumericBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseALessNumericBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAModNumericBinaryExp(
			AModNumericBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAModNumericBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAPlusNumericBinaryExp(
			APlusNumericBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAPlusNumericBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseARemNumericBinaryExp(
			ARemNumericBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseARemNumericBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseASubstractNumericBinaryExp(
			ASubstractNumericBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseASubstractNumericBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseATimesNumericBinaryExp(
			ATimesNumericBinaryExp node, POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseATimesNumericBinaryExp(node, question);
	}

	@Override
	public ProofObligationList caseAMapEnumMapExp(AMapEnumMapExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseAMapEnumMapExp(node, question);
	}

	@Override
	public ProofObligationList caseASeqCompSeqExp(ASeqCompSeqExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseASeqCompSeqExp(node, question);
	}

	@Override
	public ProofObligationList caseASeqEnumSeqExp(ASeqEnumSeqExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseASeqEnumSeqExp(node, question);
	}

	@Override
	public ProofObligationList caseASetCompSetExp(ASetCompSetExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseASetCompSetExp(node, question);
	}

	@Override
	public ProofObligationList caseASetEnumSetExp(ASetEnumSetExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseASetEnumSetExp(node, question);
	}

	@Override
	public ProofObligationList caseASetRangeSetExp(ASetRangeSetExp node,
			POContextStack question) {
		// TODO Auto-generated method stub
		return super.caseASetRangeSetExp(node, question);
	}
	
	
}
