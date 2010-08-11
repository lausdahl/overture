// this file is automatically generated by treegen. do not modify!

package org.overture.tools.treegen.ast.imp;

// import the abstract tree interfaces
import org.overture.tools.treegen.ast.itf.*;

// import the List and Vector types
import java.util.*;

public class TreeGenAstNode implements ITreeGenAstNode
{
	// keep track of all children nodes
	private Vector<ITreeGenAstNode> children = null;

	// retrieve the list of children of this node
	public List<ITreeGenAstNode> getChildren() { return children; }

	// link each node to a possible parent node
	private ITreeGenAstNode parent = null;

	// retrieve the parent node
	public ITreeGenAstNode getParent() { return parent; }

	// set the parent node
	public void setParent(ITreeGenAstNode pNode)
	{
		// integrity check
		assert(pNode != null);

		// set the parent
		parent = pNode;

		// and add ourselves to the list of children of that node
		pNode.getChildren().add(this);
	}

	// private member variable (column)
	private Long m_column;

	// public set operation for private member variable (column)
	public void setColumn(Long piv)
	{
		m_column = piv;
	}

	// public get operation for private member variable (column)
	public Long getColumn()
	{
		return m_column;
	}

	// private member variable (line)
	private Long m_line;

	// public set operation for private member variable (line)
	public void setLine(Long piv)
	{
		m_line = piv;
	}

	// public get operation for private member variable (line)
	public Long getLine()
	{
		return m_line;
	}

	// default constructor
	public TreeGenAstNode()
	{
		// initialize the list of children
		children = new Vector<ITreeGenAstNode>();

		// initialize the instance variables
		m_column = null;
		m_line = null;
	}

	// visitor support
	public void accept(ITreeGenAstVisitor pVisitor) { pVisitor.visitNode(this); }

	// the identity function
	public String identify() { return "TreeGenAstNode"; }

	// convert operation
	@SuppressWarnings({"unchecked","rawtypes"})
	protected static String convertToString(Object obj)
	{
		// consistency check
		assert (obj != null);
		
		// create the buffer
		StringBuffer buf = new StringBuffer();
		
		if (obj instanceof String) {
			buf.append("\""+obj.toString()+"\"");
		} else if (obj instanceof Vector) {
			buf.append("[");
			Vector col = (Vector) obj;
			Iterator iter = col.iterator();
			while (iter.hasNext()) {
				buf.append(convertToString(iter.next()));
				if (iter.hasNext()) buf.append(", ");
			}
			buf.append("]");
		} else if (obj instanceof HashSet) {
			buf.append("{");
			HashSet col = (HashSet) obj;
			Iterator iter = col.iterator();
			while (iter.hasNext()) {
				buf.append(convertToString(iter.next()));
				if (iter.hasNext()) buf.append(", ");
			}
			buf.append("}");
		} else if (obj instanceof HashMap) {
			buf.append("{");
			HashMap col = (HashMap) obj;
			if (col.isEmpty()) {
				buf.append(" |-> ");
			} else {
				Iterator iter = col.keySet().iterator();
				while (iter.hasNext()) {
					Object key = iter.next();
					Object val = col.get(key);
					buf.append(convertToString(key));
					buf.append(" |-> ");
					buf.append(convertToString(val));
					if (iter.hasNext()) buf.append(", ");
				}
			}
			buf.append("}");
		} else {
			buf.append(obj.toString());
		}
		
		// output the buffer
		return buf.toString();
	}
}
