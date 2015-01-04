package de.mpii.clausie;

import java.util.ArrayList;
import java.util.List;

import de.mpii.clausie.Constituent.Flag;

import edu.stanford.nlp.ling.IndexedWord;

/**
 * A clause is a basic unit of a sentence. In ClausIE, a clause consists of a
 * set of constituents (at least a subject and a verb) and a type.
 */
public class Clause {
	// -- Type definition
	// -------------------------------------------------------------------------

	/** Clause types */
	public enum Type {
		SV, SVC, SVA, SVO, SVOO, SVOC, SVOA, EXISTENTIAL, UNKNOWN;
	};

	// -- member variables
	// -----------------------------------------------------------------------

	/** Constituents of this clause */
	List<Constituent> constituents = new ArrayList<Constituent>();

	/** Type of this clause */
	Type type = Type.UNKNOWN;

	/** Position of subject in {@link #constituents} */
	int subject = -1;

	/** Position of verb in {@link #constituents} */
	int verb = -1;

	// They are lists because some times the parsers (probably an error)
	// generates more than one constituent of each type
	// e.g., more than one dobj produced by parser for
	// "The man who I told the fact is dead."
	/** Position(s) of direct object(s) in {@link #constituents}. */
	List<Integer> dobjects = new ArrayList<Integer>();

	/** Position(s) of indirect object in {@link #constituents} */
	List<Integer> iobjects = new ArrayList<Integer>();

	/** Position of complement in {@link #constituents} (for SVC / SVOC) */
	int complement = -1;

	/** Position(s) of xcomps in {@link #constituents} */
	List<Integer> xcomps = new ArrayList<Integer>();

	/** Position(s) of ccomps in {@link #constituents} */
	List<Integer> ccomps = new ArrayList<Integer>();

	/** Position(s) of acomps in {@link #constituents} */
	List<Integer> acomps = new ArrayList<Integer>();

	/** Position(s) of adverbials in {@link #constituents} */
	List<Integer> adverbials = new ArrayList<Integer>();

	/** If a relative pronoun refers to an adverbial */
	boolean relativeAdverbial = false;

	/**
	 * Parent clause of this clause, if any. For example, in
	 * "He said this is true." the clause "this / is / true" has parent
	 * "he / said / this is true".
	 */
	Clause parentClause = null;

	/** Agent (for passive voice). Currently unused. */
	IndexedWord agent;

	// -- construction
	// ----------------------------------------------------------------------------

	// make package private
	Clause() {
	};

	@Override
	public Clause clone() {
		Clause clause = new Clause();
		clause.constituents = new ArrayList<Constituent>(constituents);
		clause.type = type;
		clause.subject = subject;
		clause.verb = verb;
		clause.dobjects = new ArrayList<Integer>(dobjects);
		clause.iobjects = new ArrayList<Integer>(iobjects);
		clause.complement = complement;
		clause.xcomps = new ArrayList<Integer>(xcomps);
		clause.ccomps = new ArrayList<Integer>(ccomps);
		clause.acomps = new ArrayList<Integer>(acomps);
		clause.adverbials = new ArrayList<Integer>(adverbials);
		clause.relativeAdverbial = relativeAdverbial;
		clause.agent = agent;
		clause.parentClause = parentClause;
		return clause;
	}

	// -- methods
	// ---------------------------------------------------------------------------------

	/** Determines the type of this clause, if still unknown. */
	void detectType(Options options) {
		if (type != Type.UNKNOWN)
			return;

		// count the total number of complements (dobj, ccomp, xcomp)
		int noComplements = noComplements();

		// sometimes the parsers gives ccomp and xcomp instead of direct objects
		// e.g., "He is expected to tell the truth."
		IndexedWord root = ((IndexedConstituent) constituents.get(verb))
				.getRoot();
		boolean hasDirectObject = dobjects.size() > 0
				|| (complement < 0 && noComplements > 0 && !options.isCop(root));
		boolean hasIndirectObject = !iobjects.isEmpty();

		// Q1: Object?
		if (hasDirectObject || hasIndirectObject) {
			// Q7: dir. and indir. object?
			if (noComplements > 0 && hasIndirectObject) {
				type = Type.SVOO;
				return;
			}

			// Q8: Complement?
			if (noComplements > 1) {
				type = Type.SVOC;
				return;
			}

			// Q9: Candidate adverbial and direct objects?
			if (!(hasCandidateAdverbial() && hasDirectObject)) {
				type = Type.SVO;
				return;
			}

			// Q10: Potentially complex transitive?
			if (options.isComTran(root)) {
				type = Type.SVOA;
				return;
			}

			// Q11: Conservative?
			if (options.conservativeSVOA) {
				type = Type.SVOA;
				return;
			} else {
				type = Type.SVO;
				return;
			}
		} else {
			// Q2: Complement?
			// not sure about acomp, can a copular be transitive?
			if (complement >= 0 || noComplements > 0 && options.isCop(root)
					|| !acomps.isEmpty()) {
				type = Type.SVC;
				return;
			}

			// Q3: Candidate adverbial
			if (!hasCandidateAdverbial()) {
				type = Type.SV;
				return;
			}

			// Q4: Known non ext. copuular
			if (options.isNotExtCop(root)) {
				type = Type.SV;
				return;
			}

			// Q5: Known ext. copular
			if (options.isExtCop(root)) {
				type = Type.SVA;
				return;
			}

			// Q6: Conservative
			if (options.conservativeSVA) {
				type = Type.SVA;
				return;
			} else {
				type = Type.SV;
				return;
			}
		}
	}

	/**
	 * Checks whether this clause has a candidate adverbial, i.e., an adverbial
	 * that can potentially be obligatory.
	 */
	public boolean hasCandidateAdverbial() {
		if (adverbials.isEmpty())
			return false;
		if (relativeAdverbial)
			return true;

		// is there an adverbial that occurs after the verb?
		if (((IndexedConstituent) constituents.get(adverbials.get(adverbials
				.size() - 1))).getRoot().index() > ((IndexedConstituent) constituents
				.get(verb)).getRoot().index())
			return true;
		return false;
	}

	/**
	 * Determines the total number of complements (includes direct objects,
	 * subject complements, etc.) present in this clause.
	 */
	int noComplements() {
		return dobjects.size() + (complement < 0 ? 0 : 1) + xcomps.size()
				+ ccomps.size();
	}

	@Override
	public String toString() {
		return toString(null);
	}

	public String toString(Options options) {
		Clause clause = this;
		StringBuffer s = new StringBuffer();
		s.append(clause.type.name());
		s.append(" (");
		String sep = "";
		for (int index = 0; index < constituents.size(); index++) {
			Constituent constituent = constituents.get(index);
			s.append(sep);
			sep = ", ";
			switch (constituent.getType()) {
			case ACOMP:
				s.append("ACOMP");
				break;
			case ADVERBIAL:
				s.append("A");
				if (options != null) {
					switch (getFlag(index, options)) {
					case IGNORE:
						s.append("-");
						break;
					case OPTIONAL:
						s.append("?");
						break;
					case REQUIRED:
						s.append("!");
						break;
					}
				}
				break;
			case CCOMP:
				s.append("CCOMP");
				break;
			case COMPLEMENT:
				s.append("C");
				break;
			case DOBJ:
				s.append("O");
				break;
			case IOBJ:
				s.append("IO");
				break;
			case SUBJECT:
				s.append("S");
				break;
			case UNKOWN:
				s.append("?");
				break;
			case VERB:
				s.append("V");
				break;
			case XCOMP:
				s.append("XCOMP");
				break;
			}
			s.append(": ");
			if (!(constituent instanceof IndexedConstituent)) {
				s.append("\"");
			}
			s.append(constituent.rootString());
			if (constituent instanceof IndexedConstituent) {
				s.append("@");
				s.append(((IndexedConstituent) constituent).getRoot().index());
			} else {
				s.append("\"");
			}
		}
		s.append(")");
		return s.toString();
	}

	/**
	 * Determines the flag of the adverbial at position {@code index} in
	 * {@link #adverbials}, i.e., whether the adverbial is required, optional,
	 * or to be ignored.
	 */
	public Flag getFlag(int index, Options options) {
		boolean first = true;
		for (int i : adverbials) {
			if (i == index && isIgnoredAdverbial(i, options))
				return Flag.IGNORE;
			else if (i == index && isIncludedAdverbial(i, options))
				return Flag.REQUIRED;
			int adv = ((IndexedConstituent) constituents.get(i)).getRoot()
					.index();
			if (constituents.get(verb) instanceof IndexedConstituent
					&& adv < ((IndexedConstituent) constituents.get(verb))
							.getRoot().index() && !relativeAdverbial) {
				if (i == index) {
					return Flag.OPTIONAL;
				}
			} else {
				if (i == index) {
					if (!first)
						return Flag.OPTIONAL;
					return !(Type.SVA.equals(type) || Type.SVOA.equals(type)) ? Flag.OPTIONAL
							: Flag.REQUIRED;
				}
				first = false;
			}
		}
		return Flag.REQUIRED;
	}

	/**
	 * Checks whether the adverbial at position {@code index} in
	 * {@link #adverbials} is to be ignored by ClausIE.
	 */
	private boolean isIgnoredAdverbial(int index, Options options) {
		Constituent constituent = constituents.get(index);
		String s;
		if (constituent instanceof IndexedConstituent) {
			IndexedConstituent indexedConstituent = (IndexedConstituent) constituent;
			IndexedWord root = indexedConstituent.getRoot();
			if (indexedConstituent.getSemanticGraph().hasChildren(root)) {
				// ||IndexedConstituent.sentSemanticGraph.getNodeByIndexSafe(root.index()
				// + 1) != null
				// &&
				// IndexedConstituent.sentSemanticGraph.getNodeByIndexSafe(root.index()
				// + 1).tag().charAt(0) == 'J') { //do not ignore if it modifies
				// an adjective. Adverbs can modify verbs or adjective no reason
				// to ignore them when they refer to adjectives (at lest in
				// triples). This is important in the case of adjectival
				// complements
				return false;
			}
			s = root.lemma();
		} else {
			s = constituent.rootString();
		}

		if (options.dictAdverbsIgnore.contains(s)
				|| (options.processCcNonVerbs && options.dictAdverbsConj
						.contains(s)))
			return true;
		else
			return false;
	}

	/**
	 * Checks whether the adverbial at position {@code index} in
	 * {@link #adverbials} is required to be output by ClausIE (e.g., adverbials
	 * indicating negation, such as "hardly").
	 */
	private boolean isIncludedAdverbial(int index, Options options) {
		Constituent constituent = constituents.get(index);
		String s;
		if (constituent instanceof IndexedConstituent) {
			IndexedConstituent indexedConstituent = (IndexedConstituent) constituent;
			IndexedWord root = indexedConstituent.getRoot();
			if (indexedConstituent.getSemanticGraph().hasChildren(root)) {
				return false;
			}
			s = root.lemma();
		} else {
			s = constituent.rootString();
		}
		return options.dictAdverbsInclude.contains(s);
	}
}
