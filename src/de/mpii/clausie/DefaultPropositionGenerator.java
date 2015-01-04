package de.mpii.clausie;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import de.mpii.clausie.Constituent.Flag;

/** Currently the default proposition generator generates 3-ary propositions out of a clause.
 * 
 * @date $LastChangedDate: 2013-04-23 12:50:00 +0200 (Tue, 23 Apr 2013) $
 * @version $LastChangedRevision: 736 $ */
public class DefaultPropositionGenerator extends PropositionGenerator {
	public DefaultPropositionGenerator(ClausIE clausIE) {
		super(clausIE);
	}

	@Override
	public void generate(List<Proposition> result, Clause clause,
			List<Boolean> include) {
		Proposition proposition = new Proposition();
		List<Proposition> propositions = new ArrayList<Proposition>();
		
		// process subject
		if (clause.subject > -1 && include.get(clause.subject)) { // subject is -1 when there is an xcomp
			proposition.constituents.add( generate(clause, clause.subject) );
		} else {
			//throw new IllegalArgumentException();
		}
		
		// process verb
		if (include.get(clause.verb)) {
			proposition.constituents.add( generate(clause, clause.verb) );
		} else {
			throw new IllegalArgumentException();
		}
		
		propositions.add(proposition);
		
		// process arguments
		SortedSet<Integer> sortedIndexes = new TreeSet<Integer>();
		sortedIndexes.addAll(clause.iobjects);
		sortedIndexes.addAll(clause.dobjects);
		sortedIndexes.addAll(clause.xcomps);
		sortedIndexes.addAll(clause.ccomps);
		sortedIndexes.addAll(clause.acomps);
		sortedIndexes.addAll(clause.adverbials);
		if (clause.complement >= 0)
			sortedIndexes.add(clause.complement);
		for(Integer index: sortedIndexes) {
			if (clause.constituents.get(clause.verb) instanceof IndexedConstituent && clause.adverbials.contains(index) && ((IndexedConstituent)clause.constituents.get(index)).getRoot().index() < ((IndexedConstituent)clause.constituents.get(clause.verb)).getRoot().index()) continue;
				for(Proposition p: propositions) {
						if (include.get(index)) {
							p.constituents.add( generate(clause, index) );
						}
				}
		}
		
		// process adverbials  before verb
		sortedIndexes.clear();
		sortedIndexes.addAll(clause.adverbials);
		for (Integer index : sortedIndexes) {
			if (clause.constituents.get(clause.verb) instanceof TextConstituent || ((IndexedConstituent)clause.constituents.get(index)).getRoot().index() > ((IndexedConstituent)clause.constituents.get(clause.verb)).getRoot().index()) break;
			if (include.get(index)) {
				for(Proposition p: propositions) {
					p.constituents.add( generate(clause, index) );
					if (clause.getFlag(index, clausIE.options).equals(Flag.OPTIONAL)) {
						p.optional.add(p.constituents.size());
					}	
				}
			}
		}
		
		// make 3-ary if needed
		if (!clausIE.options.nary ) {
			for(Proposition p: propositions) {
				p.optional.clear();
				if (p.constituents.size() > 3) {
					StringBuilder arg = new StringBuilder();
					for (int i=2; i<p.constituents.size(); i++) {
						if (i>2) arg.append(" ");
						arg.append(p.constituents.get(i));
					}
					p.constituents.set(2,  arg.toString());
					for (int i=p.constituents.size()-1; i>2; i--) {
						p.constituents.remove(i);
					}
				}
			}
		}
		
		// we are done
		result.addAll(propositions);
	}
}
