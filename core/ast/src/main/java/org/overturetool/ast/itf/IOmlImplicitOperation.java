package org.overturetool.ast.itf;

import java.util.*;
import jp.co.csk.vdm.toolbox.VDM.*;

public abstract interface IOmlImplicitOperation extends IOmlOperationShape
{
	abstract String getIdentifier() throws CGException;
	@SuppressWarnings("unchecked")
	abstract Vector getPatternTypePairList() throws CGException;
	@SuppressWarnings("unchecked")
	abstract Vector getIdentifierTypePairList() throws CGException;
	abstract IOmlOperationTrailer getTrailer() throws CGException;
}

