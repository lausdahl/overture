package org.overture.interpreter.assistant.type;

import org.overture.ast.types.AOptionalType;
import org.overture.interpreter.assistant.IInterpreterAssistantFactory;
import org.overture.interpreter.runtime.Context;
import org.overture.interpreter.runtime.ValueException;
import org.overture.interpreter.values.NilValue;
import org.overture.interpreter.values.ValueList;
import org.overture.typechecker.assistant.type.AOptionalTypeAssistantTC;

public class AOptionalTypeAssistantInterpreter extends AOptionalTypeAssistantTC
{
	protected static IInterpreterAssistantFactory af;

	@SuppressWarnings("static-access")
	public AOptionalTypeAssistantInterpreter(IInterpreterAssistantFactory af)
	{
		super(af);
		this.af = af;
	}

	public static ValueList getAllValues(AOptionalType type, Context ctxt)
			throws ValueException
	{
		ValueList list = PTypeAssistantInterpreter.getAllValues(type.getType(), ctxt);
		list.add(new NilValue());
		return list;
	}

}
