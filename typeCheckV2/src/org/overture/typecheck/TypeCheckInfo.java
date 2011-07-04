package org.overture.typecheck;

import org.overture.ast.typechecker.NameScope;
import org.overture.runtime.Environment;
import org.overture.runtime.TypeList;


public class TypeCheckInfo
{
	public Environment env;
	public NameScope scope;
	public TypeList qualifiers;
}
