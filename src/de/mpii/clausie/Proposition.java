
package de.mpii.clausie;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Stores a proposition.
 * 
 * @date $LastChangedDate: 2013-04-24 11:54:36 +0200 (Wed, 24 Apr 2013) $
 * @version $LastChangedRevision: 741 $ */
public class Proposition {
	
	/** Constituents of the proposition */
	List<String> constituents = new ArrayList<String>();
	/** Position of optional constituents */
	Set<Integer> optional = new HashSet<Integer>();
	
	// TODO: types of constituents (e.g., optionality)
	// sentence ID etc.
	
	public Proposition() {
	}

	/** Returns the subject of the proposition */
	public String subject() {
		return constituents.get(0);
	}
	
	/** Returns the relation of the proposition */
	public String relation() {
		return constituents.get(1);
	}
	
	/** Returns a constituent in a given position*/
	public String argument(int i) {
		return constituents.get(i+2);
	}
	
	/** Returns the number of arguments*/
	public int noArguments() {
		return constituents.size() -2;
	}
	
	/** Checks if an argument is optional*/
	public boolean isOptionalArgument(int i) {
		return optional.contains(i+2);
	}
	
	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		String sep = "(";
		for (int i=0; i<constituents.size(); i++) {
			String constituent = constituents.get(i);
			sb.append(sep);
			sep = ", ";
			sb.append("\"");
			sb.append(constituent);
			sb.append("\"");
			if (optional.contains(i)) {
				sb.append("?");
			}
		}
		sb.append(")");
		return sb.toString();
	}
	
	@Override
	public Proposition clone() {
		Proposition clone = new Proposition();
		clone.constituents = new ArrayList<String>(this.constituents);
		clone.optional = new HashSet<Integer>(this.optional);
		return clone;
	}
}
