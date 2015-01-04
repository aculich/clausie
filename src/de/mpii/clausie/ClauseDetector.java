package de.mpii.clausie;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import de.mpii.clausie.Constituent.Type;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.trees.EnglishGrammaticalRelations;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.semgraph.SemanticGraphEdge;

/**{@link ClauseDetector} contains the methods dealing with the detection of clauses.
 * After the detection is performed a set of {@link Clause} is created.
 * 
 * {@code detectClauses} first detects the type of clause to be generated based on syntactic relations
 * and once a clause is detected a given method is used to create a {@link Clause}.
 * 
 * @date $LastChangedDate: 2013-04-24 11:35:23 +0200 (Wed, 24 Apr 2013) $
 * @version $LastChangedRevision: 739 $ */
class ClauseDetector {

    /** Set of dependency relations that do not belong to a complement */
    protected static final Set<GrammaticalRelation> EXCLUDE_RELATIONS_COMPLEMENT;
    static {
        HashSet<GrammaticalRelation> temp = new HashSet<GrammaticalRelation>();
        temp.add(EnglishGrammaticalRelations.AUX_MODIFIER);
        temp.add(EnglishGrammaticalRelations.AUX_PASSIVE_MODIFIER);
        temp.add(EnglishGrammaticalRelations.SUBJECT);
        temp.add(EnglishGrammaticalRelations.COPULA);
        temp.add(EnglishGrammaticalRelations.ADVERBIAL_MODIFIER);
        EXCLUDE_RELATIONS_COMPLEMENT = Collections.unmodifiableSet(temp);
    }

    /** Set of dependency relations that belong to the verb */
    protected static final Set<GrammaticalRelation> INCLUDE_RELATIONS_VERB;
    static {
        HashSet<GrammaticalRelation> temp = new HashSet<GrammaticalRelation>();
        temp.add(EnglishGrammaticalRelations.AUX_MODIFIER);
        temp.add(EnglishGrammaticalRelations.AUX_PASSIVE_MODIFIER);
        temp.add(EnglishGrammaticalRelations.NEGATION_MODIFIER);
        INCLUDE_RELATIONS_VERB = Collections.unmodifiableSet(temp);
    }

    private ClauseDetector() {
    };

    /** Detects clauses in the input sentence */
    static void detectClauses(ClausIE clausIE) {
        IndexedConstituent.sentSemanticGraph = clausIE.semanticGraph;
        List<IndexedWord> roots = new ArrayList<IndexedWord>();
        for (SemanticGraphEdge edge : clausIE.semanticGraph.edgeIterable()) {
            // check whether the edge identifies a clause
            if (DpUtils.isAnySubj(edge)) {
                // clauses with a subject
                IndexedWord subject = edge.getDependent();
                IndexedWord root = edge.getGovernor();
                addNsubjClause(clausIE, roots, clausIE.clauses, subject, root, false);
            } else if (clausIE.options.processAppositions && DpUtils.isAppos(edge)) {
                // clauses for appositions
                IndexedWord subject = edge.getGovernor();
                IndexedWord object = edge.getDependent();
                addApposClause(clausIE, subject, object);
                roots.add(null);
            } else if (clausIE.options.processPossessives && DpUtils.isPoss(edge)) {
                // clauses for possessives
                IndexedWord subject = edge.getDependent();
                IndexedWord object = edge.getGovernor();
                addPossessiveClause(clausIE, subject, object);
                roots.add(null);
            } else if (clausIE.options.processPartmods && DpUtils.isPartMod(edge)) {
                // clauses for participial modifiers
                IndexedWord subject = edge.getGovernor();
                IndexedWord object = edge.getDependent();
                addPartmodClause(clausIE, subject, object, roots);
            } 
        }

        // postprocess clauses
        // TODO
        for (int i = 0; i < clausIE.clauses.size(); i++) {
            Clause clause = clausIE.clauses.get(i);

            // set parents (slow and inefficient for now)
            IndexedWord root = roots.get(i);
            if (root != null) {
                int index = ancestorOf(clausIE.semanticGraph, root, roots); // recursion needed to
                                                                            // deal
                // with xcomp; more stable
                if (index >= 0) {
                    // System.out.println("Clause " + clause.toString() + " has parent " +
                    // clausIE.clauses.get(index).toString());
                    clause.parentClause = clausIE.clauses.get(index);
                }
            }

            // exclude vertexes (each constituent needs to excludes vertexes of the other
            // constituents)
            excludeVertexes(clause);
        }
    }

    /** Adds in the exclude vertex of a clause the head of the rest of the clauses */
    private static void excludeVertexes(Clause clause) {

        for (int j = 0; j < clause.constituents.size(); j++) {
            if (!(clause.constituents.get(j) instanceof IndexedConstituent))
                continue;
            IndexedConstituent constituent = (IndexedConstituent) clause.constituents.get(j);

            for (int k = 0; k < clause.constituents.size(); k++) {
                if (k == j || !(clause.constituents.get(k) instanceof IndexedConstituent))
                    continue;
                IndexedConstituent other = (IndexedConstituent) clause.constituents.get(k);

                constituent.getExcludedVertexes().add(other.getRoot());
                constituent.getExcludedVertexes().addAll(other.getAdditionalVertexes());
            }
        }

    }

    /** TODO */
    private static int ancestorOf(SemanticGraph semanticGraph, IndexedWord node,
            List<IndexedWord> ancestors) {
        for (SemanticGraphEdge e : semanticGraph.getIncomingEdgesSorted(node)) {
            int index = ancestors.indexOf(node);
            if (index >= 0)
                return index;
            index = ancestorOf(semanticGraph, e.getGovernor(), ancestors);
            if (index >= 0)
                return index;
        }
        return -1;
    }

    /** Selects constituents of a clause for clauses with internal subject or coming from a participial modifier  
     * @param roots The list of roots of the clauses in the sentence
     * @param clauses The list of clauses in the sentence
     * @param subject The subject of the clause
     * @param clauseRoot The root of the clause, either a verb or a complement
     * @param partmod Indicates if the clause is generated from a partmod relation*/
    private static void addNsubjClause(ClausIE clausIE, List<IndexedWord> roots,
            List<Clause> clauses, IndexedWord subject, IndexedWord clauseRoot, boolean partmod) {
        SemanticGraph semanticGraph = new SemanticGraph(clausIE.semanticGraph);
        Options options = clausIE.options;

        List<SemanticGraphEdge> toRemove = new ArrayList<SemanticGraphEdge>();
        //to store the heads of the clauses according to the CCs options
        List<IndexedWord> ccs = ProcessConjunctions.getIndexedWordsConj(semanticGraph,
                clausIE.depTree, clauseRoot, EnglishGrammaticalRelations.CONJUNCT, toRemove,
                options);
        for (SemanticGraphEdge edge : toRemove)
            semanticGraph.removeEdge(edge);
        
        //A new clause is generated for each clause head
        for (int i = 0; i < ccs.size(); i++) {
            IndexedWord root = ccs.get(i);
            List<SemanticGraphEdge> outgoingEdges = semanticGraph.getOutEdgesSorted(root);
            List<SemanticGraphEdge> incomingEdges = semanticGraph.getIncomingEdgesSorted(root);

            // initialize clause
            Clause clause = new Clause();
            clause.verb = -1;
            SemanticGraphEdge cop = DpUtils.findFirstOfRelation(outgoingEdges,
                    EnglishGrammaticalRelations.COPULA);
            Set<IndexedWord> exclude = null;
            Set<IndexedWord> include = null;
            if (cop != null) {
                exclude = DpUtils.exclude(semanticGraph, EXCLUDE_RELATIONS_COMPLEMENT, root);
                include = DpUtils.exclude(semanticGraph, INCLUDE_RELATIONS_VERB, root);
            } else {
                exclude = new HashSet<IndexedWord>();
            }

            // relative clause?
            SemanticGraphEdge rcmod = DpUtils.findFirstOfRelation(incomingEdges,
                    EnglishGrammaticalRelations.RELATIVE_CLAUSE_MODIFIER);
            SemanticGraphEdge poss = null;
            if (rcmod != null)
                poss = DpUtils.findDescendantRelativeRelation(semanticGraph, root,
                        EnglishGrammaticalRelations.POSSESSION_MODIFIER);

            // determine constituents of clause
            //ArrayList<IndexedWord> coordinatedConjunctions = new ArrayList<IndexedWord>(); // to
            // store
            // potential
            // conjunctions

//------------------------Set verb or complement, and subject.-------------------------------------------------
            Constituent constRoot = null;
            if (cop != null) {
                clause.complement = clause.constituents.size();
                constRoot = new IndexedConstituent(semanticGraph, root,
                        Collections.<IndexedWord> emptySet(), exclude, Constituent.Type.COMPLEMENT);
                clause.constituents.add(constRoot);

                clause.verb = clause.constituents.size();
                if (!partmod) {
                    clause.constituents.add(new IndexedConstituent(semanticGraph, cop
                            .getDependent(), include, Collections.<IndexedWord> emptySet(),
                            Constituent.Type.VERB));
                } else {
                    clause.constituents.add(new TextConstituent("be " + clauseRoot.word(),
                            Constituent.Type.VERB));
                }

            } else {
                clause.verb = clause.constituents.size();
                if (!partmod) {
                    constRoot = new IndexedConstituent(semanticGraph, root,
                            Collections.<IndexedWord> emptySet(), exclude, Constituent.Type.VERB);
                } else {
                    constRoot = new TextConstituent("be " + clauseRoot.word(),
                            Constituent.Type.VERB);
                }

                clause.constituents.add(constRoot);
            }

            clause.subject = clause.constituents.size();
            if (subject.tag().charAt(0) == 'W' && rcmod != null) {
                clause.constituents.add(createRelConstituent(semanticGraph, rcmod.getGovernor(),
                        Type.SUBJECT));
                ((IndexedConstituent) constRoot).getExcludedVertexes().add(subject);
                rcmod = null;
            } else if (poss != null && poss.getGovernor().equals(subject) && rcmod != null) {
                clause.constituents.add(createPossConstituent(semanticGraph, poss, rcmod, subject,
                        Type.SUBJECT));
                rcmod = null;
            } else if (partmod && subject.tag().charAt(0) == 'V') {
                List<SemanticGraphEdge> outsub = clausIE.semanticGraph.getOutEdgesSorted(subject);
                SemanticGraphEdge sub = DpUtils.findFirstOfRelationOrDescendent(outsub,
                        EnglishGrammaticalRelations.SUBJECT);
                if (sub != null)
                    clause.constituents.add(new IndexedConstituent(semanticGraph, sub
                            .getDependent(), Constituent.Type.SUBJECT));
                else
                    clause.constituents.add(new IndexedConstituent(semanticGraph, subject,
                            Constituent.Type.SUBJECT));

            } else
                clause.constituents.add(new IndexedConstituent(semanticGraph, subject,
                        Constituent.Type.SUBJECT));
            
           //If the clause comes from a partmod construction exclude necesary vertex
            if (partmod) {
                ((IndexedConstituent) clause.constituents.get(clause.subject)).excludedVertexes
                        .add(clauseRoot);
                // He is the man crying the whole day.
                List<SemanticGraphEdge> outsub = clausIE.semanticGraph.getOutEdgesSorted(subject);
                SemanticGraphEdge coppm = DpUtils.findFirstOfRelationOrDescendent(outsub,
                        EnglishGrammaticalRelations.COPULA);
                if (coppm != null) {
                    ((IndexedConstituent) clause.constituents.get(clause.subject)).excludedVertexes
                            .add(coppm.getDependent());
                    SemanticGraphEdge spm = DpUtils.findFirstOfRelationOrDescendent(outsub,
                            EnglishGrammaticalRelations.SUBJECT);
                    ((IndexedConstituent) clause.constituents.get(clause.subject)).excludedVertexes
                            .add(spm.getDependent());
                }

            }

 //------------------------Select constituents of the predicate-------------------------------------------------
            for (SemanticGraphEdge outgoingEdge : outgoingEdges) {
                IndexedWord dependent = outgoingEdge.getDependent();

                // to avoid compl or mark in a main clause. "I doubt if she was sure whether this was important".
                if (DpUtils.isComplm(outgoingEdge) || DpUtils.isMark(outgoingEdge)) {
                    ((IndexedConstituent) constRoot).getExcludedVertexes().add(dependent);
                //Indirect Object
                } else if (DpUtils.isIobj(outgoingEdge)) {
                    clause.iobjects.add(clause.constituents.size());
                    //If it is a relative clause headed by a relative pronoun.
                    if (dependent.tag().charAt(0) == 'W' && rcmod != null) {
                        clause.constituents.add(createRelConstituent(semanticGraph,
                                rcmod.getGovernor(), Type.IOBJ));
                        ((IndexedConstituent) constRoot).getExcludedVertexes().add(dependent);
                        rcmod = null;
                    //to deal with the possessive relative pronoun     
                    } else if (poss != null && poss.getGovernor().equals(dependent)
                            && rcmod != null) {
                        clause.constituents.add(createPossConstituent(semanticGraph, poss, rcmod,
                                dependent, Type.IOBJ));
                        rcmod = null;
                    // "regular case"    
                    } else
                        clause.constituents.add(new IndexedConstituent(semanticGraph, dependent,
                                Constituent.Type.IOBJ));
                //Direct Object
                } else if (DpUtils.isDobj(outgoingEdge)) {
                    clause.dobjects.add(clause.constituents.size());
                    if (dependent.tag().charAt(0) == 'W' && rcmod != null) {
                        clause.constituents.add(createRelConstituent(semanticGraph,
                                rcmod.getGovernor(), Type.DOBJ));
                        ((IndexedConstituent) constRoot).getExcludedVertexes().add(dependent);
                        rcmod = null;
                    } else if (poss != null && poss.getGovernor().equals(dependent)
                            && rcmod != null) {
                        clause.constituents.add(createPossConstituent(semanticGraph, poss, rcmod,
                                dependent, Type.DOBJ));
                        rcmod = null;
                    } else
                        clause.constituents.add(new IndexedConstituent(semanticGraph, dependent,
                                Constituent.Type.DOBJ));
                //CCOMPS
                } else if (DpUtils.isCcomp(outgoingEdge)) {
                    clause.ccomps.add(clause.constituents.size());
                    clause.constituents.add(new IndexedConstituent(semanticGraph, dependent,
                            Constituent.Type.CCOMP));
                //XCOMPS (Note: Need special treatment, they won't form a new clause so optional/obligatory constituents
                // are managed within the context of its parent clause)
                } else if (DpUtils.isXcomp(outgoingEdge)) {              	
                    List<IndexedWord> xcomproots = new ArrayList<IndexedWord>();
                    List<Clause> xcompclauses = new ArrayList<Clause>();
                    IndexedWord xcompsubject = null;
                    SemanticGraphEdge xcsub = DpUtils.findFirstOfRelationOrDescendent(
                            semanticGraph.getOutEdgesSorted(outgoingEdge.getDependent()),
                            EnglishGrammaticalRelations.SUBJECT);
                    if (xcsub != null)
                        xcompsubject = xcsub.getDependent();
                    //Need to identify the internal structure of the clause
                    addNsubjClause(clausIE, xcomproots, xcompclauses, subject,
                            outgoingEdge.getDependent(), false);
                    for (Clause cl : xcompclauses) {
                        if (xcompsubject != null) {
                            int verb = cl.verb;
                            ((IndexedConstituent) cl.constituents.get(verb)).additionalVertexes
                                    .add(xcompsubject);
                        }
                        excludeVertexes(cl);
                    }
                    clause.xcomps.add(clause.constituents.size());
                    clause.constituents.add(new XcompConstituent(semanticGraph, dependent,
                            Constituent.Type.XCOMP, xcompclauses));
                 //Adjective complement
                } else if (DpUtils.isAcomp(outgoingEdge)) { 
                    clause.acomps.add(clause.constituents.size());
                    clause.constituents.add(new IndexedConstituent(semanticGraph, dependent,
                            Constituent.Type.ACOMP));
                 //Various Adverbials
                } else if ((DpUtils.isAnyPrep(outgoingEdge)
						|| DpUtils.isPobj(outgoingEdge)
						|| DpUtils.isTmod(outgoingEdge)
						|| DpUtils.isAdvcl(outgoingEdge)
						|| DpUtils.isNpadvmod(outgoingEdge) || DpUtils
							.isPurpcl(outgoingEdge))

				) {
					int constint = clause.constituents.size();
					clause.adverbials.add(constint);
					clause.constituents.add(new IndexedConstituent(
							semanticGraph, dependent,
							Constituent.Type.ADVERBIAL));
				//Advmod
				} else if (DpUtils.isAdvmod(outgoingEdge)) {
                    int constint = clause.constituents.size();
                    clause.adverbials.add(constint);
                    clause.constituents.add(new IndexedConstituent(semanticGraph, dependent,
                            Constituent.Type.ADVERBIAL));
                 //Partmod    
                } else if (DpUtils.isPartMod(outgoingEdge)) { 
                    int constint = clause.constituents.size();
                    clause.adverbials.add(constint);
                    clause.constituents.add(new IndexedConstituent(semanticGraph, dependent,
                            Constituent.Type.ADVERBIAL));
                 //Rel appears in certain cases when relative pronouns act as prepositional objects "I saw the house in which I grew".
                 // We generate a new clause out of the relative clause   
                } else if (DpUtils.isRel(outgoingEdge)) {
                	processRel(outgoingEdge, semanticGraph, dependent, rcmod, clause);
                	rcmod = null;
                	
                //To process passive voice (!Not done here)
               // } else if (DpUtils.isAgent(outgoingEdge))
               //     clause.agent = dependent;
               // else if (DpUtils.isMark(outgoingEdge) || DpUtils.isComplm(outgoingEdge)) {
                    // clause.subordinateConjunction = dependent;
                } else if (DpUtils.isExpl(outgoingEdge))
                    clause.type = Clause.Type.EXISTENTIAL;
              //  else if (options.processCcAllVerbs && DpUtils.isAnyConj(outgoingEdge))
               //     coordinatedConjunctions.add(dependent);
            }

 //------------------------To process relative clauses with implicit (zero) relative pronoun-------------------------
            if (rcmod != null) { //"I saw the house I grew up in", "I saw
                                 // the house I like", "I saw the man I gave the book" ...
                Constituent candidate = searchCandidateAdverbial(clause);
                if (candidate != null) {
                    SemanticGraph newSemanticGraph = new SemanticGraph(
                            ((IndexedConstituent) candidate).getSemanticGraph());
                    IndexedConstituent tmpconst = createRelConstituent(newSemanticGraph,
                            rcmod.getGovernor(), Type.ADVERBIAL);
                    newSemanticGraph.addEdge(((IndexedConstituent) candidate).getRoot(),
                            rcmod.getGovernor(), EnglishGrammaticalRelations.PREPOSITIONAL_OBJECT,
                            rcmod.getWeight());
                    ((IndexedConstituent) candidate).getExcludedVertexes().addAll(
                            tmpconst.getExcludedVertexes());
                    ((IndexedConstituent) candidate).setSemanticGraph(newSemanticGraph);
                    rcmod = null;
                } else if (DpUtils.findFirstOfRelation(outgoingEdges,
                        EnglishGrammaticalRelations.DIRECT_OBJECT) == null) {
                    clause.dobjects.add(clause.constituents.size());
                    clause.constituents.add(createRelConstituent(semanticGraph,
                            rcmod.getGovernor(), Type.DOBJ));
                    rcmod = null;
                } else if (DpUtils.findFirstOfRelation(outgoingEdges,
                        EnglishGrammaticalRelations.INDIRECT_OBJECT) == null) {
                    clause.iobjects.add(clause.constituents.size());
                    clause.constituents.add(createRelConstituent(semanticGraph,
                            rcmod.getGovernor(), Type.IOBJ));
                    rcmod = null;
                }
            }
            
//------------------------------------------------------------------------------------------------------------------          
            //To deal with parataxis            
            SemanticGraphEdge parataxis = DpUtils.findFirstOfRelation(incomingEdges,
                    EnglishGrammaticalRelations.PARATAXIS);
            if (parataxis != null && clause.constituents.size() < 3) {
                addParataxisClause(clausIE, parataxis.getGovernor(), parataxis.getDependent(),
                        roots);
                return; // to avoid generating (John, said) in "My dog, John said, is great" //To
                        // deal with the type of parataxis. Parataxis are either like in the example
                        // above or subclauses comming from ":" or ";" this is here because is
                        // difficult to identify the type upfront. Otherwise we can count the potential
                        // constituents upfront and move this up.
            }
            
            
            //Detect type and mantain clause lists
            roots.add(root);
            if (!partmod) {
                clause.detectType(options);
            } else {
                clause.type = Clause.Type.SVA;
            }
            clauses.add(clause);
        }
    }

    /** Process relation rel, it creates a new clause out of the relative clause 
     * @param outgoingEdge The rel labeled edge
     * @param semanticGraph The semantic graph
     * @param dependent The dependent of the relation
     * @param rcmod The relative clause modifier of the relation refered by rel
     * @param clause A clause*/
    private static void processRel(SemanticGraphEdge outgoingEdge, SemanticGraph semanticGraph, IndexedWord dependent, SemanticGraphEdge rcmod, Clause clause) {
    	 SemanticGraph newSemanticGraph = new SemanticGraph(semanticGraph);
         List<SemanticGraphEdge> outdep = newSemanticGraph.getOutEdgesSorted(dependent);
         SemanticGraphEdge pobed = DpUtils.findFirstOfRelation(outdep,
                 EnglishGrammaticalRelations.PREPOSITIONAL_OBJECT);

         SemanticGraphEdge posspobj = null;
         if (pobed != null && pobed.getDependent().tag().charAt(0) != 'W') {
             List<SemanticGraphEdge> outpobj = newSemanticGraph
                     .getOutEdgesSorted(dependent);
             posspobj = DpUtils.findFirstOfRelation(outpobj,
                     EnglishGrammaticalRelations.POSSESSION_MODIFIER);
         }

         if (pobed != null && pobed.getDependent().tag().charAt(0) == 'W'
                 && rcmod != null) {
             newSemanticGraph.addEdge(dependent, rcmod.getGovernor(),
                     EnglishGrammaticalRelations.PREPOSITIONAL_OBJECT,
                     pobed.getWeight());
             newSemanticGraph.removeEdge(pobed);
             int constint = clause.constituents.size();
             clause.adverbials.add(constint);
             clause.constituents.add(createRelConstituent(newSemanticGraph,
                     rcmod.getGovernor(), Type.SUBJECT));
             ((IndexedConstituent) clause.constituents.get(constint))
                     .setRoot(dependent);
             clause.relativeAdverbial = true;
             rcmod = null;
         } else if (pobed != null && posspobj != null && rcmod != null) {
             newSemanticGraph.addEdge(posspobj.getGovernor(), rcmod.getGovernor(),
                     EnglishGrammaticalRelations.POSSESSION_MODIFIER,
                     posspobj.getWeight());
             newSemanticGraph.removeEdge(posspobj);
             int constint = clause.constituents.size();
             clause.adverbials.add(constint);
             // search pobj copy edge.
             clause.constituents.add(createRelConstituent(newSemanticGraph,
                     rcmod.getGovernor(), Type.SUBJECT));
             ((IndexedConstituent) clause.constituents.get(constint))
                     .setRoot(dependent);
             clause.relativeAdverbial = true;
         }
		
	}

	/** Finds the adverbial to which the relative clause is referring to*/
    private static Constituent searchCandidateAdverbial(Clause clause) {
        for (Constituent c : clause.constituents) {
            IndexedWord root = ((IndexedConstituent) c).getRoot();
            if (root.tag().equals("IN")
                    && !((IndexedConstituent) c).getSemanticGraph().hasChildren(root))
                return c;
        }
        return null;
    }

    /** Creates a constituent for a possessive relative clause
    * @param semanticGraph The semantic graph
    * @param poss The edge referring to the possessive relation
    * @param rcmod The relative clause modifier of the relation
    * @param constGovernor The root of the constituent
    * @param type The type of the constituent*/
    private static Constituent createPossConstituent(SemanticGraph semanticGraph,
            SemanticGraphEdge poss, SemanticGraphEdge rcmod, IndexedWord constGovernor, Type type) {

        SemanticGraph newSemanticGraph = new SemanticGraph(semanticGraph);
        double weight = poss.getWeight();
        newSemanticGraph.addEdge(poss.getGovernor(), rcmod.getGovernor(),
                EnglishGrammaticalRelations.POSSESSION_MODIFIER, weight);
        Set<IndexedWord> exclude = DpUtils.exclude(newSemanticGraph, EXCLUDE_RELATIONS_COMPLEMENT,
                rcmod.getGovernor());
        newSemanticGraph.removeEdge(poss);
        newSemanticGraph.removeEdge(rcmod);
        return new IndexedConstituent(newSemanticGraph, constGovernor,
                Collections.<IndexedWord> emptySet(), exclude, type);
    }

    /** Creates a constituent for the relative clause implied by rel 
    * @param semanticGraph The semantic graph
    * @param root The root of the constituent
    * @param type The type of the constituent*/
    private static IndexedConstituent createRelConstituent(SemanticGraph semanticGraph,
            IndexedWord root, Type type) {

        List<SemanticGraphEdge> outrcmod = semanticGraph.getOutEdgesSorted(root);
        SemanticGraphEdge rccop = DpUtils.findFirstOfRelation(outrcmod,
                EnglishGrammaticalRelations.COPULA);
        if (rccop != null) {
            Set<IndexedWord> excludercmod = DpUtils.exclude(semanticGraph,
                    EXCLUDE_RELATIONS_COMPLEMENT, root);
            return new IndexedConstituent(semanticGraph, root,
                    Collections.<IndexedWord> emptySet(), excludercmod, type);
        } else
            return new IndexedConstituent(semanticGraph, root, type);
    }

    /** Generates a clause from an apposition 
    * @param subject The subject of the clause (first argument of the appos relation)
    * @param object  The object of the clause (second argument of the appos relation)*/
    private static void addApposClause(ClausIE clausIE, IndexedWord subject, IndexedWord object) {
        Clause clause = new Clause();
        clause.subject = 0;
        clause.verb = 1;
        clause.complement = 2;
        clause.constituents.add(new IndexedConstituent(clausIE.semanticGraph, subject,
                Constituent.Type.SUBJECT));
        clause.constituents.add(new TextConstituent(clausIE.options.appositionVerb,
                Constituent.Type.VERB));
        clause.constituents.add(new IndexedConstituent(clausIE.semanticGraph, object,
                Constituent.Type.COMPLEMENT));
        clause.type = Clause.Type.SVC;
        clausIE.clauses.add(clause);
    }

    /** Generates a clause from a possessive relation
    * @param subject The subject of the clause
    * @param object  The object of the clause */
    private static void addPossessiveClause(ClausIE clausIE, IndexedWord subject,
            IndexedWord object) {
        Clause clause = new Clause();
        SemanticGraph newSemanticGraph = new SemanticGraph(clausIE.semanticGraph);
        clause.subject = 0;
        clause.verb = 1;
        clause.dobjects.add(2);
        Set<IndexedWord> excludesub = new TreeSet<IndexedWord>();
        Set<IndexedWord> excludeobj = new TreeSet<IndexedWord>();

        excludeobj.add(subject);
        List<SemanticGraphEdge> outedobj = newSemanticGraph.getOutEdgesSorted(object);
        excludeVertexPoss(outedobj, excludeobj, clausIE);

        SemanticGraphEdge rcmod = null;
        if (subject.tag().charAt(0) == 'W') {
            IndexedWord root = newSemanticGraph.getParent(object); 
            if (root.tag().equals("IN"))
                root = newSemanticGraph.getParent(root); // "I saw the man in whose wife I trust"
            List<SemanticGraphEdge> inedges = newSemanticGraph.getIncomingEdgesSorted(root);
            rcmod = DpUtils.findFirstOfRelation(inedges,
                    EnglishGrammaticalRelations.RELATIVE_CLAUSE_MODIFIER);
        } else {
            List<SemanticGraphEdge> outedges = newSemanticGraph.getOutEdgesSorted(subject);
            SemanticGraphEdge ps = DpUtils.findFirstOfRelation(outedges,
                    EnglishGrammaticalRelations.POSSESSIVE_MODIFIER);
            if (ps != null)
                excludesub.add(ps.getDependent());
        }

        if (rcmod != null) {
            clause.constituents.add(createRelConstituent(newSemanticGraph, rcmod.getGovernor(),
                    Type.SUBJECT));
            ((IndexedConstituent) clause.constituents.get(0)).getExcludedVertexes().addAll(
                    excludesub); // to avoid the s in  "Bill's clothes are great".
        } else {
            clause.constituents.add(new IndexedConstituent(newSemanticGraph, subject, Collections
                    .<IndexedWord> emptySet(), excludesub, Type.SUBJECT));
        }
        clause.constituents.add(new TextConstituent(clausIE.options.possessiveVerb,
                Constituent.Type.VERB));
        clause.constituents.add(new IndexedConstituent(newSemanticGraph, object, Collections
                .<IndexedWord> emptySet(), excludeobj, Constituent.Type.DOBJ));
        clause.type = Clause.Type.SVO;
        clausIE.clauses.add(clause);
    }

    /** Excludes vertexes for the object of a "possessive clause"
    * @param outedobj relations to be examined for exclusion
    * @param excludeobj The vertexes to be excluded*/
    private static void excludeVertexPoss(List<SemanticGraphEdge> outedobj,
            Set<IndexedWord> excludeobj, ClausIE clausIE) {
        for (SemanticGraphEdge ed : outedobj) {
            if (DpUtils.isAdvcl(ed)
                    || DpUtils.isAdvmod(ed)
                    || DpUtils.isAnyObj(ed) // currently everything is
                                            // excluded except prep and infmod
                    || DpUtils.isAnySubj(ed) || DpUtils.isAux(ed) || DpUtils.isCop(ed)
                    || DpUtils.isTmod(ed) || DpUtils.isAnyConj(ed)
                    && clausIE.options.processCcNonVerbs)
                excludeobj.add(ed.getDependent());
        }

    }

    /** Creates a clause from a partmod relation 
    * @param subject The subject of the clause
    * @param object  The object of the clause 
    * @param roots List of clause roots*/
    private static void addPartmodClause(ClausIE clausIE, IndexedWord subject, IndexedWord verb,
            List<IndexedWord> roots) {
        IndexedWord partmodsub = subject;
        addNsubjClause(clausIE, roots, clausIE.clauses, partmodsub, verb, true);
    }

    /** Creates a clause from a parataxis relation 
    * @param root Head of the parataxis relation
    * @param parroot  Dependent of the parataxis relation
    * @param roots List of clause roots*/
    private static void addParataxisClause(ClausIE clausIE, IndexedWord root, IndexedWord parroot,
            List<IndexedWord> roots) {
        Constituent verb = new IndexedConstituent(clausIE.semanticGraph, parroot, Type.VERB);
        List<SemanticGraphEdge> outedges = clausIE.semanticGraph.getOutEdgesSorted(parroot);
        SemanticGraphEdge subject = DpUtils.findFirstOfRelationOrDescendent(outedges,
                EnglishGrammaticalRelations.SUBJECT);
        if (subject != null) {
            Constituent subjectConst = new IndexedConstituent(clausIE.semanticGraph,
                    subject.getDependent(), Type.SUBJECT);
            Constituent object = new IndexedConstituent(clausIE.semanticGraph, root, Type.DOBJ);
            ((IndexedConstituent) object).excludedVertexes.add(parroot);
            Clause clause = new Clause();
            clause.subject = 0;
            clause.verb = 1;
            clause.dobjects.add(2);
            clause.constituents.add(subjectConst);
            clause.constituents.add(verb);
            clause.constituents.add(object);
            clause.type = Clause.Type.SVO;
            clausIE.clauses.add(clause);
            roots.add(null);

        }

    }
}
