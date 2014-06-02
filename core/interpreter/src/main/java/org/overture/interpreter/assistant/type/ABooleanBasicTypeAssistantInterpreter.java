package org.overture.interpreter.assistant.type;

import org.overture.ast.types.ABooleanBasicType;
import org.overture.interpreter.assistant.IInterpreterAssistantFactory;
import org.overture.interpreter.runtime.Context;
import org.overture.interpreter.values.BooleanValue;
import org.overture.interpreter.values.ValueList;

public class ABooleanBasicTypeAssistantInterpreter
{
	protected static IInterpreterAssistantFactory af;

	@SuppressWarnings("static-access")
	public ABooleanBasicTypeAssistantInterpreter(IInterpreterAssistantFactory af)
	{
		this.af = af;
	}

	public static ValueList getAllValues(ABooleanBasicType type, Context ctxt)
	{
		ValueList v = new ValueList();
		v.add(new BooleanValue(true));
		v.add(new BooleanValue(false));
		return v;
	}

}