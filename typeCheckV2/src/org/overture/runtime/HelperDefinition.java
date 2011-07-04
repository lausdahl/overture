package org.overture.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


import org.overture.ast.definitions.AClassDefDefinition;
import org.overture.ast.definitions.AExplicitFunctionDefinition;
import org.overture.ast.definitions.ALocalDefinition;
import org.overture.ast.definitions.AStateDefinition;
import org.overture.ast.definitions.PDefinition;
import org.overture.ast.node.NodeList;
import org.overture.ast.typechecker.NameScope;
import org.overture.ast.types.AClassType;
import org.overture.ast.types.PType;
import org.overture.typecheck.TypeCheckInfo;
import org.overture.typecheck.TypeCheckVisitor;
import org.overturetool.vdmj.lex.LexNameToken;


public class HelperDefinition {

	public static boolean hasSupertype(AClassDefDefinition aClassDefDefinition, AClassType other) {
		if (aClassDefDefinition.getType().equals(other))
		{
			return true;
		}
		else
		{
			for (PType type: aClassDefDefinition.getSupertypes())
			{
				AClassType sclass = (AClassType)type;

				if (HelperType.hasSupertype(sclass, other))
				{
					return true;
				}
			}
		}
		return false;
		
	}

	public static boolean isFunctionOrOperation(PDefinition possible) {
		switch(possible.kindPDefinition())
		{
			//TODO: missing operations
			case EXPLICITFUNCTION:
			case IMPLICITFUNCTION: 
				return true;
			default:
				return false;
		}
	}

	public static PDefinition findType(List<PDefinition> definitions,
			LexNameToken name, String fromModule) {
		
		for (PDefinition d: definitions)
		{
			PDefinition def = findType(d,name, fromModule);

			if (def != null)
			{
				return def;
			}
		}

		return null;
		
	}

	private static PDefinition findType(PDefinition d, LexNameToken name,
			String fromModule) {
		switch(d.kindPDefinition())
		{
			case STATE:
				if (HelperDefinition.findName(d,name, NameScope.STATE) != null)
				{
					return d;
				}

				return null;
			case TYPEDEF:
				return null;
				
			default:
				return null;
		}
	}

	private static PDefinition findName(PDefinition d, LexNameToken sought,
			NameScope scope) {
		if (d.getName().equals(sought))
		{
			if ((d.getNameScope()== NameScope.STATE && !scope.matches(NameScope.STATE)) ||
				(d.getNameScope() == NameScope.OLDSTATE && !scope.matches(NameScope.OLDSTATE)))
			{
				sought.report(3302,
					"State variable '" + sought.getName() + "' cannot be accessed from this context");
			}

			markUsed(d);
			return d;
		}

		return null;
	}

	public static void markUsed(PDefinition d)
	{
		d.setUsed(true);
	}

	public static PDefinition findName(List<PDefinition> definitions,
			LexNameToken name, NameScope scope) {
		for (PDefinition d: definitions)
		{
			PDefinition def = findName(d,name, scope);

			if (def != null)
			{
				return def;
			}
		}

		return null;
	}

	public static AStateDefinition findStateDefinition(
			List<PDefinition> definitions) {
		for (PDefinition d: definitions)
		{
			if (d instanceof AStateDefinition)
			{
				return (AStateDefinition)d;
			}
		}

   		return null;
	}

	public static void unusedCheck(List<PDefinition> definitions) {
		for (PDefinition d: definitions)
		{
			HelperDefinition.unusedCheck(d);
		}
		
	}

	private static void unusedCheck(PDefinition d) {
		if (!d.getUsed())
		{
			HelperDefinition.warning(d, 5000, "Definition '" + d.getName() + "' not used");
			HelperDefinition.markUsed(d);		// To avoid multiple warnings
		}
		
	}
	
	public static void warning(PDefinition d, int number, String msg)
	{
		TypeChecker.warning(number, msg, d.getLocation());
	}

	public static Set<PDefinition> findMatches(List<PDefinition> definitions,
			LexNameToken name) {
		
		Set<PDefinition> set = new HashSet<PDefinition>();

		for (PDefinition d: singleDefinitions(definitions))
		{
			if (HelperDefinition.isFunctionOrOperation(d) && d.getName().matches(name))
			{
				set.add(d);
			}
		}

		return set;
	}

	private static List<PDefinition> singleDefinitions(List<PDefinition> definitions) {
		List<PDefinition> all = new ArrayList<PDefinition>();

		for (PDefinition d: definitions)
		{
			all.addAll(HelperDefinition.getDefinitions(d));
		}

		return all;
	}

	private static Collection<? extends PDefinition> getDefinitions(
			PDefinition d) {
		System.out.println("HelperDefinition : getDefinitions(PDefinitions d) not implemented");
		return null;
	}

	public static void markUsed(List<PDefinition> definitions) {
		for (PDefinition d: definitions)
		{
			markUsed(d);
		}
		
	}

	public static void typeCheck(NodeList<PDefinition> defs,
			 TypeCheckInfo question, TypeCheckVisitor typeCheckVisitor) {
		for (PDefinition d: defs)
		{			
			d.apply(typeCheckVisitor, question );
		}
		
	}

	public static PDefinition getSelfDefinition(AExplicitFunctionDefinition node) {
		return HelperDefinition.getSelfDefinition(node.getClassDefinition());
	}

	private static PDefinition getSelfDefinition(
			AClassDefDefinition classDefinition) {
		
		PDefinition def = new ALocalDefinition(classDefinition.getLocation(),
				classDefinition.getName().getSelfName(), NameScope.LOCAL, classDefinition.getType());
			markUsed(def);
			return def;
	}
}
