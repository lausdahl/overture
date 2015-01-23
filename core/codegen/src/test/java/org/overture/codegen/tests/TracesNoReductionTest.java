package org.overture.codegen.tests;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.overture.ast.lex.LexLocation;
import org.overture.test.framework.BaseTestSuite;
import org.overture.test.framework.Properties;

public class TracesNoReductionTest extends BaseTestSuite
{
	public static final String ROOT = "src" + File.separatorChar + "test"
			+ File.separatorChar + "resources" + File.separatorChar
			+ "traces_no_reduction_specs";

	public static Test suite() throws IllegalArgumentException,
			SecurityException, InstantiationException, IllegalAccessException,
			InvocationTargetException, NoSuchMethodException, IOException
	{
		LexLocation.absoluteToStringLocation = false;
		Properties.recordTestResults = TestFlags.TRACES_NO_REDUCTION_TESTS_ON;

		String name = "Traces no reduction test case";
		TestSuite test = createTestCompleteFile(name, ROOT, TracesNoReductionTestCase.class, "");
		return test;
	}
}

