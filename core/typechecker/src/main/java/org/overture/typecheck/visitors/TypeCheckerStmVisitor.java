package org.overture.typecheck.visitors;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.overture.ast.analysis.QuestionAnswerAdaptor;
import org.overture.ast.definitions.AExplicitFunctionDefinition;
import org.overture.ast.definitions.AExplicitOperationDefinition;
import org.overture.ast.definitions.AImplicitOperationDefinition;
import org.overture.ast.definitions.AInstanceVariableDefinition;
import org.overture.ast.definitions.ALocalDefinition;
import org.overture.ast.definitions.PDefinition;
import org.overture.ast.definitions.SClassDefinition;
import org.overture.ast.definitions.assistants.PAccessSpecifierAssistant;
import org.overture.ast.definitions.assistants.PDefinitionAssistant;
import org.overture.ast.definitions.assistants.PDefinitionListAssistant;
import org.overture.ast.expressions.AIntConstExp;
import org.overture.ast.expressions.ARealConstExp;
import org.overture.ast.expressions.AStringConstExp;
import org.overture.ast.expressions.AVariableExp;
import org.overture.ast.patterns.AExpressionPattern;
import org.overture.ast.patterns.assistants.PPatternAssistant;
import org.overture.ast.statements.AAlwaysStm;
import org.overture.ast.statements.AAssignmentStm;
import org.overture.ast.statements.AAtomicStm;
import org.overture.ast.statements.ABlockSimpleBlockStm;
import org.overture.ast.statements.ACallObjectStm;
import org.overture.ast.statements.ACallStm;
import org.overture.ast.statements.ACaseAlternativeStm;
import org.overture.ast.statements.ACasesStm;
import org.overture.ast.statements.AClassInvariantStm;
import org.overture.ast.statements.ACyclesStm;
import org.overture.ast.statements.ADefLetDefStm;
import org.overture.ast.statements.ADurationStm;
import org.overture.ast.statements.AElseIfStm;
import org.overture.ast.statements.AErrorStm;
import org.overture.ast.statements.AExitStm;
import org.overture.ast.statements.AForAllStm;
import org.overture.ast.statements.AForIndexStm;
import org.overture.ast.statements.PStm;
import org.overture.ast.statements.assistants.ABlockSimpleBlockStmAssistant;
import org.overture.ast.statements.assistants.ACallObjectStatementAssistant;
import org.overture.ast.statements.assistants.ACallStmAssistant;
import org.overture.ast.statements.assistants.PStateDesignatorAssistant;
import org.overture.ast.types.AAccessSpecifierAccessSpecifier;
import org.overture.ast.types.ABooleanBasicType;
import org.overture.ast.types.AClassType;
import org.overture.ast.types.AFunctionType;
import org.overture.ast.types.AOperationType;
import org.overture.ast.types.ASetType;
import org.overture.ast.types.AUnionType;
import org.overture.ast.types.AUnknownType;
import org.overture.ast.types.AVoidType;
import org.overture.ast.types.PType;
import org.overture.ast.types.assistants.PTypeAssistant;
import org.overture.runtime.Environment;
import org.overture.runtime.FlatCheckedEnvironment;
import org.overture.runtime.TypeComparator;
import org.overture.typecheck.PrivateClassEnvironment;
import org.overture.typecheck.PublicClassEnvironment;
import org.overture.typecheck.TypeCheckInfo;
import org.overture.typecheck.TypeCheckerErrors;
import org.overturetool.vdmj.Settings;
import org.overturetool.vdmj.lex.Dialect;
import org.overturetool.vdmj.lex.LexNameToken;
import org.overturetool.vdmj.lex.LexStringToken;

import org.overturetool.vdmj.typechecker.NameScope;



public class TypeCheckerStmVisitor extends QuestionAnswerAdaptor<TypeCheckInfo, PType> 
{

	final private QuestionAnswerAdaptor<TypeCheckInfo, PType> rootVisitor;
	
	
	public TypeCheckerStmVisitor(TypeCheckVisitor typeCheckVisitor) 
	{
		this.rootVisitor = typeCheckVisitor;
		
	}
	
	@Override
	public PType caseAAlwaysStm(AAlwaysStm node, TypeCheckInfo question) 
	{
		node.getAlways().apply(rootVisitor, question);
		node.setType(node.getBody().apply(rootVisitor, question));
		return node.getType();
	}
	
	@Override
	public PType caseAAssignmentStm(AAssignmentStm node, TypeCheckInfo question) 
	{
		
		node.setTargetType(node.getTarget().apply(rootVisitor, question));
		node.setExpType(node.getExp().apply(rootVisitor, question));
		
		if (!TypeComparator.compatible(node.getType(), node.getExp().getType()))
		{
			TypeCheckerErrors.report(3239, "Incompatible types in assignment", node.getLocation(), node);
			TypeCheckerErrors.detail2("Target", node.getTarget(), "Expression", node.getExp());
		}

		node.setClassDefinition(question.env.findClassDefinition());
		node.setStateDefinition(question.env.findStateDefinition());

		PDefinition encl = question.env.getEnclosingDefinition();

		if (encl != null)
		{
			if (encl instanceof AExplicitOperationDefinition)
			{
				AExplicitOperationDefinition op = (AExplicitOperationDefinition)encl;
				node.setInConstructor(op.getIsConstructor()); 
			}
			else if (encl instanceof AImplicitOperationDefinition)
			{
				AImplicitOperationDefinition op = (AImplicitOperationDefinition)encl;
				node.setInConstructor(op.getIsConstructor()); 
			}
		}

		if (node.getInConstructor())
		{			
			// Mark assignment target as initialized (so no warnings)
			PDefinition state;
			state = PStateDesignatorAssistant.targetDefinition(node.getTarget(), question);
			
			if (state instanceof AInstanceVariableDefinition)
			{
				AInstanceVariableDefinition iv = (AInstanceVariableDefinition)state;
				iv.setInitialized(true);
			}
		}

		return new AVoidType(node.getLocation(),false);
	}
	
	@Override
	public PType caseAAtomicStm(AAtomicStm node, TypeCheckInfo question) 
	{
		
		node.setStatedef(question.env.findStateDefinition());
		
		for (AAssignmentStm stmt : node.getAssignments()) {
			stmt.apply(rootVisitor, question);
		}
		
		return new AVoidType(node.getLocation(),false);
	}
	
	@Override
	public PType caseABlockSimpleBlockStm(ABlockSimpleBlockStm node, TypeCheckInfo question) 
	{
		boolean notreached = false;
		Set<PType> rtypes = new HashSet<PType>();
		PType last = null;

		for (PStm stmt: node.getStatements())
		{
			PType stype = stmt.apply(rootVisitor,question);

			if (notreached)
			{
				TypeCheckerErrors.warning(5006, "Statement will not be reached", node.getLocation(), node);
			}
			else
			{
				last = stype;
				notreached = true;

    			if (stype instanceof AUnionType)
    			{
    				AUnionType ust = (AUnionType)stype;

    				for (PType t: ust.getTypes())
    				{
    					ABlockSimpleBlockStmAssistant.addOne(rtypes, t);

    					if (t instanceof AVoidType ||
    						t instanceof AUnknownType)
    					{
    						notreached = false;
    					}
    				}
    			}
    			else
    			{
    				ABlockSimpleBlockStmAssistant.addOne(rtypes, stype);

					if (stype instanceof AVoidType ||
						stype instanceof AUnknownType)
					{
						notreached = false;
					}
    			}
			}
		}

		// If the last statement reached has a void component, add this to the overall
		// return type, as the block may return nothing.

		
		if (last != null &&
			(PTypeAssistant.isType(last, AVoidType.class) || PTypeAssistant.isUnknown(last)))
		{
			rtypes.add(new AVoidType(node.getLocation(), false));
		}

		return rtypes.isEmpty() ?
			new AVoidType(node.getLocation(), false) : PTypeAssistant.getType(rtypes, node.getLocation());
	}
	
	@Override
	public PType caseACallObjectStm(ACallObjectStm node, TypeCheckInfo question) 
	{
		PType dtype = node.getDesignator().apply(rootVisitor,question);
		
		if (!PTypeAssistant.isClass(dtype))
		{
			TypeCheckerErrors.report(3207, "Object designator is not an object type", node.getLocation(), node);
			return new AUnknownType(node.getLocation(), false);
		}

		AClassType ctype = PTypeAssistant.getClassType(dtype);
		
		SClassDefinition classdef = ctype.getClassdef();
		SClassDefinition self = question.env.findClassDefinition();
		Environment classenv = null;
		

		if (self == classdef || PDefinitionAssistant.hasSupertype(self, classdef.getType()))
		{
			// All fields visible. Note that protected fields are inherited
			// into "locals" so they are effectively private
			classenv = new PrivateClassEnvironment(self);
		}
		else
		{
			// Only public fields externally visible
			classenv = new PublicClassEnvironment(classdef);
		}

		if (node.getClassname() == null)
		{
			node.setField(new LexNameToken(
				ctype.getName().name, node.getFieldname().getName(), node.getFieldname().getLocation()));
		}
		else
		{
			node.setField(node.getClassname());
		}

		node.getField().location.executable(true);
		List<PType> atypes = ACallObjectStatementAssistant.getArgTypes(node.getArgs(), rootVisitor, question);
		node.getField().setTypeQualifier(atypes);
		PDefinition fdef = classenv.findName(node.getField(), question.scope);

		// Special code for the deploy method of CPU

		if (Settings.dialect == Dialect.VDM_RT &&
			node.getField().module.equals("CPU") && node.getField().name.equals("deploy"))
		{ 
			
			if (!PTypeAssistant.isType(atypes.get(0), AClassType.class))
			{
				TypeCheckerErrors.report(3280, "Argument to deploy must be an object", 
						node.getArgs().get(0).getLocation(), node.getArgs().get(0));
			}

			return new AVoidType(node.getLocation(), false);
		}
		else if (Settings.dialect == Dialect.VDM_RT &&
				node.getField().module.equals("CPU") && node.getField().name.equals("setPriority"))
		{
			if (!(atypes.get(0) instanceof AOperationType))
			{
				TypeCheckerErrors.report(3290, "Argument to setPriority must be an operation", 
						node.getArgs().get(0).getLocation(), node.getArgs().get(0));
			}
			else
			{
				// Convert the variable expression to a string...
    			AVariableExp a1 = (AVariableExp)node.getArgs().get(0);
    			node.getArgs().remove(0);
    			node.getArgs().add(0, new AStringConstExp(null, a1.getLocation(),
    				new LexStringToken(
    					a1.getName().getExplicit(true).getName(),a1.getLocation())));

    			if (a1.getName().module.equals(a1.getName().name))	// it's a constructor
    			{
    				TypeCheckerErrors.report(3291, "Argument to setPriority cannot be a constructor", 
    						node.getArgs().get(0).getLocation(), node.getArgs().get(0));

    			}
			}

			return new AVoidType(node.getLocation(), false);
		}
		else if (fdef == null)
		{
			TypeCheckerErrors.report(3209, "Member " + node.getField() + " is not in scope", node.getLocation(), node);
			return new AUnknownType(node.getLocation(), false);
		}
		else if (PDefinitionAssistant.isStatic(fdef) && !question.env.isStatic())
		{
			// warning(5005, "Should invoke member " + field + " from a static context");
		}

		PType type = fdef.getType();

		if (PTypeAssistant.isOperation(type))
		{
			AOperationType optype = PTypeAssistant.getOperation(type);
			optype.apply(rootVisitor, question);
			node.getField().setTypeQualifier(optype.getParameters());
			ACallObjectStatementAssistant.checkArgTypes(type, optype.getParameters(), atypes);	// Not necessary?
			return optype.getResult();
		}
		else if (PTypeAssistant.isFunction(type))
		{
			// This is the case where a function is called as an operation without
			// a "return" statement.

			AFunctionType ftype = PTypeAssistant.getFunction(type);
			ftype.apply(rootVisitor, question);
			node.getField().setTypeQualifier(ftype.getParameters());
			ACallObjectStatementAssistant.checkArgTypes(type, ftype.getParameters(), atypes);	// Not necessary?
			return ftype.getResult();
		}
		else
		{
			TypeCheckerErrors.report(3210, "Object member is neither a function nor an operation", node.getLocation(), node);
			return new AUnknownType(node.getLocation(), false);
		}
	}
	
	@Override
	public PType caseACallStm(ACallStm node, TypeCheckInfo question) 
	{
		List<PType> atypes = ACallObjectStatementAssistant.getArgTypes(node.getArgs(), rootVisitor, question);

		if (question.env.isVDMPP())
		{
			node.getName().setTypeQualifier(atypes);
		}

		PDefinition opdef = question.env.findName(node.getName(), question.scope);

		if (opdef == null)
		{
			TypeCheckerErrors.report(3213, "Operation " + node.getName() + " is not in scope", node.getLocation(), node);
			question.env.listAlternatives(node.getName());
			return new AUnknownType(node.getLocation(), false);
		}

		if (!PDefinitionAssistant.isStatic(opdef) && question.env.isStatic())
		{
			TypeCheckerErrors.report(3214, "Cannot call " + node.getName() + " from static context", node.getLocation(), node);
			return new AUnknownType(node.getLocation(),false);
		}

		PType type = opdef.getType();

		if (PTypeAssistant.isOperation(type))
		{
    		AOperationType optype = PTypeAssistant.getOperation(type);

    		
    		PTypeAssistant.typeResolve(optype, null, rootVisitor, question);
    		// Reset the name's qualifier with the actual operation type so
    		// that runtime search has a simple TypeComparator call.

    		if (question.env.isVDMPP())
    		{
    			node.getName().setTypeQualifier(optype.getParameters());
    		}

    		ACallStmAssistant.checkArgTypes(optype, optype.getParameters(), atypes);
    		return optype.getResult();
		}
		else if (PTypeAssistant.isFunction(type))
		{
			// This is the case where a function is called as an operation without
			// a "return" statement.

    		AFunctionType ftype = PTypeAssistant.getFunction(type);
    		PTypeAssistant.typeResolve(ftype, null, rootVisitor, question);

    		// Reset the name's qualifier with the actual function type so
    		// that runtime search has a simple TypeComparator call.

    		if (question.env.isVDMPP())
    		{
    			node.getName().setTypeQualifier(ftype.getParameters());
    		}

    		ACallStmAssistant.checkArgTypes(ftype, ftype.getParameters(), atypes);
    		return ftype.getResult();
		}
		else
		{
			TypeCheckerErrors.report(3210, "Name is neither a function nor an operation", node.getLocation(), node);
			return new AUnknownType(node.getLocation(), false);
		}
	}
	
	@Override
	public PType caseACaseAlternativeStm(ACaseAlternativeStm node,TypeCheckInfo question) 
	{

		if (node.getDefs() == null)
		{
			node.setDefs(new LinkedList<PDefinition>());
			PPatternAssistant.typeResolve(node.getPattern(), rootVisitor, question);

			if (node.getPattern() instanceof AExpressionPattern)
			{
				// Only expression patterns need type checking...
				AExpressionPattern ep = (AExpressionPattern)node.getPattern();
				ep.getExp().apply(rootVisitor, question);
			}
			
			PPatternAssistant.typeResolve(node.getPattern(), rootVisitor, question);
			
			ACasesStm stm = (ACasesStm) node.parent();		
			node.getDefs().addAll(PPatternAssistant.getDefinitions(node.getPattern(), stm.getExp().getType(), NameScope.LOCAL));
		}
		
		PDefinitionListAssistant.typeCheck(node.getDefs(), rootVisitor, question);
		Environment local = new FlatCheckedEnvironment(node.getDefs(), question.env, question.scope);
		PType r = node.getResult().apply(rootVisitor, question);
		local.unusedCheck();
		return r;
	}
	
	@Override
	public PType caseACasesStm(ACasesStm node, TypeCheckInfo question) 
	{
		
		node.setType(node.getExp().apply(rootVisitor, question));

		Set<PType> rtypes = new HashSet<PType>();

		for (ACaseAlternativeStm c: node.getCases())
		{
			rtypes.add(c.apply(rootVisitor, question));
		}

		if (node.getOthers()!= null)
		{
			rtypes.add(node.getOthers().apply(rootVisitor, question));
		}

		return PTypeAssistant.getType(rtypes, node.getLocation());
	}
	
	@Override
	public PType caseAClassInvariantStm(AClassInvariantStm node,TypeCheckInfo question) 
	{

		// Definitions already checked.
		return new ABooleanBasicType(node.getLocation(),false);
	}
	
	@Override
	public PType caseACyclesStm(ACyclesStm node, TypeCheckInfo question) 
	{
		
		if (node.getCycles() instanceof AIntConstExp)
		{
			AIntConstExp i = (AIntConstExp)node.getCycles();

			if (i.getValue().value < 0)
			{
				TypeCheckerErrors.report(3282, "Argument to cycles must be integer >= 0", node.getLocation(), node);
			}

			node.setValue(i.getValue().value);
		}
		else if (node.getCycles() instanceof ARealConstExp)
		{
			ARealConstExp i = (ARealConstExp)node.getCycles();

			if (i.getValue().value < 0 ||
				Math.floor(i.getValue().value) != i.getValue().value)
			{
				TypeCheckerErrors.report(3282, "Argument to cycles must be integer >= 0", node.getLocation(), node);
			}

			node.setValue((long) i.getValue().value);
		}
		else
		{
			TypeCheckerErrors.report(3282, "Argument to cycles must be integer >= 0", node.getLocation(), node);
		}

		return node.getStatement().apply(rootVisitor, question);
	}
	
	@Override
	public PType caseADefLetDefStm(ADefLetDefStm node, TypeCheckInfo question) 
	{
		
		// Each local definition is in scope for later local definitions...

		Environment local = question.env;

		for (PDefinition d: node.getLocalDefs())
		{
			if (d instanceof AExplicitFunctionDefinition)
			{
				// Functions' names are in scope in their bodies, whereas
				// simple variable declarations aren't

				local = new FlatCheckedEnvironment(d, local, question.scope);	// cumulative
				PDefinitionAssistant.implicitDefinitions(d, local);
				PDefinitionAssistant.typeResolve(d, rootVisitor, question);
				
				if (question.env.isVDMPP())
				{
					SClassDefinition cdef = question.env.findClassDefinition();
					d.setClassDefinition(cdef);
					d.setAccess(PAccessSpecifierAssistant.getStatic(d, true));
				}
				
				d.apply(rootVisitor, question);
			}
			else
			{
				PDefinitionAssistant.implicitDefinitions(d, local);
				PDefinitionAssistant.typeResolve(d, rootVisitor, question);
				d.apply(rootVisitor, question);
				local = new FlatCheckedEnvironment(d, local, question.scope);	// cumulative
			}
		}

		PType r = node.getStatement().apply(rootVisitor, question);
		local.unusedCheck(question.env);
		return r;
	}
	
	@Override
	public PType caseADurationStm(ADurationStm node, TypeCheckInfo question) 
	{
		long durationValue = 0;
		
		if (node.getDuration() instanceof AIntConstExp)
		{
			AIntConstExp i = (AIntConstExp)node.getDuration();

			if (i.getValue().value < 0)
			{
				TypeCheckerErrors.report(3281, "Argument to duration must be integer >= 0", node.getLocation(), node);
			}

			durationValue = i.getValue().value;
		}
		else if (node.getDuration() instanceof ARealConstExp)
		{
			ARealConstExp i = (ARealConstExp)node.getDuration();

			if (i.getValue().value < 0 ||
				Math.floor(i.getValue().value) != i.getValue().value)
			{
				TypeCheckerErrors.report(3282, "Argument to duration must be integer >= 0", node.getLocation(), node);
			}

			durationValue = (long)i.getValue().value;
		}
		else
		{
			TypeCheckerErrors.report(3281, "Argument to duration must be integer >= 0", node.getLocation(), node);;
		}

		node.setStep(durationValue);//sets the input value [ns] to internal
		
		return node.getStatement().apply(rootVisitor, question);
	}
	
	@Override
	public PType caseAElseIfStm(AElseIfStm node, TypeCheckInfo question) 
	{
		if (!PTypeAssistant.isType(node.getElseIf().apply(rootVisitor, question), ABooleanBasicType.class))
		{
			TypeCheckerErrors.report(3218, "Expression is not boolean", node.getLocation(), node);
		}

		return node.getThenStm().apply(rootVisitor, question);
	}
	
	@Override
	public PType caseAErrorStm(AErrorStm node, TypeCheckInfo question) 
	{
		return new AUnknownType(node.getLocation(), false);	// Because we terminate anyway
	}
	
	@Override
	public PType caseAExitStm(AExitStm node, TypeCheckInfo question) 
	{
		if (node.getExpression() != null)
		{
			node.setType(node.getExpression().apply(rootVisitor, question));
		}

		// This is unknown because the statement doesn't actually return a
		// value - so if this is the only statement in a body, it is not a
		// type error (should return the same type as the definition return
		// type).

		return new AUnknownType(node.getLocation(), false);
	}
	
	@Override
	public PType caseAForAllStm(AForAllStm node, TypeCheckInfo question) {
		node.setType(node.getSet().apply(rootVisitor, question));
		PPatternAssistant.typeResolve(node.getPattern(), rootVisitor, question);

		if (PTypeAssistant.isSet(node.getType()))
		{
			ASetType st = PTypeAssistant.getSet(node.getType());
			List<PDefinition> defs = PPatternAssistant.getDefinitions(node.getPattern(), st.getSetof(), NameScope.LOCAL);

			Environment local = new FlatCheckedEnvironment(defs, question.env, question.scope);
			PType rt = node.getStatement().apply(rootVisitor, question);
			local.unusedCheck();
			return rt;
		}
		else
		{
			TypeCheckerErrors.report(3219, "For all statement does not contain a set type", node.getLocation(), node);
			return new AUnknownType(node.getLocation(), false);
		}
	}
	
	@Override
	public PType caseAForIndexStm(AForIndexStm node, TypeCheckInfo question) {
		PType ft = node.getFrom().apply(rootVisitor,question);
		PType tt = node.getTo().apply(rootVisitor,question);

		if (!PTypeAssistant.isNumeric(ft))
		{
			TypeCheckerErrors.report(3220, "From type is not numeric", node.getLocation(), node);
		}

		if (!PTypeAssistant.isNumeric(tt))
		{
			TypeCheckerErrors.report(3221, "To type is not numeric", node.getLocation(), node);
		}

		if (node.getBy() != null)
		{
			PType bt = node.getBy().apply(rootVisitor,question);

			if (!PTypeAssistant.isNumeric(bt))
			{
				TypeCheckerErrors.report(3222, "By type is not numeric", node.getLocation(), node);
			}
		}
		AAccessSpecifierAccessSpecifier a = new AAccessSpecifierAccessSpecifier();
		
		PDefinition vardef = new ALocalDefinition(node.getVar().getLocation(), node.getVar().getClassName(), 
				NameScope.LOCAL, false, null, PAccessSpecifierAssistant.getDefault(), ft, false);
		Environment local = new FlatCheckedEnvironment(vardef, question.env, question.scope);
		PType rt = node.getStatement().apply(rootVisitor, question);
		local.unusedCheck();
		return rt;
	}
}
