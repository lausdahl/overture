package org.overture.codegen.rt2rmi;

import java.util.LinkedList;
import java.util.List;

import org.overture.cgrmi.extast.declarations.ARemoteContractImplDeclIR;
import org.overture.codegen.ir.declarations.ADefaultClassDeclIR;
import org.overture.codegen.ir.declarations.AMethodDeclIR;
import org.overture.codegen.ir.IRInfo;
import org.overture.codegen.vdm2java.IJavaConstants;

/*
 * This sets up the remote contract implementation
 * with the relevant parameters and methods described in
 * the main report.
 * 
 * Sets up the ARemoteContractImplDeclCG node
 */

public class RemoteImplGenerator
{

	private List<ADefaultClassDeclIR> irClasses;
	private IRInfo info;
	
	public RemoteImplGenerator(List<ADefaultClassDeclIR> irClasses, IRInfo info)
	{
		super();
		this.irClasses = irClasses;
		this.info = info;
	}

	public List<ARemoteContractImplDeclIR> run()
	{
		for(ADefaultClassDeclIR classCg : irClasses)
		{
			if (classCg.getSuperNames().size() > 1)
			{
				info.addTransformationWarning(classCg, RemoteContractGenerator.MULTIPLE_INHERITANCE_WARNING);
				return new LinkedList<>();
			}
			
		}
		
		List<ARemoteContractImplDeclIR> contractImpls = new LinkedList<ARemoteContractImplDeclIR>();

		for (ADefaultClassDeclIR classCg : irClasses)
		{
			List<AMethodDeclIR> publicMethods = new LinkedList<AMethodDeclIR>();
			ARemoteContractImplDeclIR contractImpl = new ARemoteContractImplDeclIR();
			contractImpl.setName(classCg.getName());
			
			contractImpl.setFields(classCg.getFields());
			if(!classCg.getSuperNames().isEmpty())
			{
				contractImpl.setSuperName(classCg.getSuperNames().get(0).getName());
			}
			contractImpl.setAbstract(classCg.getAbstract());
			// Add type declarations
	
			contractImpl.setTypeDecls(classCg.getTypeDecls());

			if (classCg.getSuperNames().isEmpty())
				contractImpl.setIsUniCast(true);
			else
				contractImpl.setIsUniCast(false);
			for (AMethodDeclIR method : classCg.getMethods())
			{

				// The autogenerated method "toString" is skipped
				if (method.getName().equals(IJavaConstants.TO_STRING))
				{
				} else if (method.getAccess().equals(IJavaConstants.PUBLIC))
				{
					publicMethods.add(method);
				} else
				{
					publicMethods.add(method);
				}
			}

			//contractImpl.setMethods(publicMethods);

			contractImpls.add(contractImpl);
		}

		return contractImpls;
	}

}
