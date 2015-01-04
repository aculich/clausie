package de.mpii.clausie;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import de.mpii.clausie.Constituent.Type;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.trees.EnglishGrammaticalRelations;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.semgraph.SemanticGraphEdge;

/** This is a provisory implementation of the processing of coordinating conjunctions.
 * 
 * Coordinating conjunctions are still a difficult issue for the parser and therefore 
 * the source of a significant loss in precision by ClausIE.
 * 
 * Code is not clean or optimally efficient. More work needs to be done in how to handle CCs.
 * 
 * @date $  $
 * @version $ $ */
public class ProcessConjunctions {

	
	/** Process CCs of a given constituent */
    public static List<Constituent> processCC(Tree depTree, Clause clause,
            Constituent constituent, int index) {
        return generateConstituents(depTree, clause, (IndexedConstituent) constituent, index);
    }

    /** Generates a set of constituents from a CC for a given constituent */    
    private static List<Constituent> generateConstituents(Tree depTree, Clause clause,
            IndexedConstituent constituent, int index) {
        IndexedConstituent copy = constituent.clone();
        copy.setSemanticGraph( copy.createReducedSemanticGraph() );
        List<Constituent> result = new ArrayList<Constituent>();
        result.add(copy);
        generateConstituents(copy.getSemanticGraph(), depTree, copy, copy.getRoot(),
                result, true);
        return result;

    }

    // Process CCs by exploring the graph from one constituent and generating more constituents as
    // it encounters ccs
    private static void generateConstituents(SemanticGraph semanticGraph, Tree depTree,
            IndexedConstituent constituent, IndexedWord root, List<Constituent> constituents,
            boolean firstLevel) {

        List<SemanticGraphEdge> outedges = semanticGraph.getOutEdgesSorted(root);
        List<SemanticGraphEdge> conjunct = DpUtils.getEdges(outedges,
                EnglishGrammaticalRelations.COORDINATION);
        
        Boolean processCC = true;
        SemanticGraphEdge predet = null;
        
        //to avoid processing under certain circunstances must be design properly when final setup is decided 
		if (conjunct != null) {
			SemanticGraphEdge con = DpUtils.findFirstOfRelation(outedges,
					EnglishGrammaticalRelations.QUANTIFIER_MODIFIER);
			if (con != null && con.getDependent().lemma().equals("between"))
				processCC = false;
			List<SemanticGraphEdge> inedg = semanticGraph
					.getIncomingEdgesSorted(root);
			SemanticGraphEdge pobj = DpUtils.findFirstOfRelation(inedg,
					EnglishGrammaticalRelations.PREPOSITIONAL_OBJECT);
			// this wont work with collapsed dependencies
			if (pobj != null && pobj.getGovernor().lemma().equals("between"))
				processCC = false;
			Collection<IndexedWord> sibs = semanticGraph.getSiblings(root);
			for (IndexedWord sib : sibs) {
				List<SemanticGraphEdge> insib = semanticGraph
						.getIncomingEdgesSorted(sib);
				predet = DpUtils.findFirstOfRelation(insib,
						EnglishGrammaticalRelations.PREDETERMINER);
				if (predet == null)
					predet = DpUtils.findFirstOfRelation(insib,
							EnglishGrammaticalRelations.DETERMINER);
				if (predet != null)
					break;
			}
		}
		
       
        for (SemanticGraphEdge edge : outedges) {
        	if (DpUtils.isParataxis(edge) || DpUtils.isRcmod(edge) || DpUtils.isAppos(edge) ||(DpUtils.isDep(edge) && constituent.type.equals(Type.VERB) ) ) continue;//to avoid processing relative clauses and appositions which are included as an independent clause in the clauses list of the sentence, also no dep in verbs are processed. To reproduce the results of the paper comment this line and eliminate the duplicate propositions that may be generated.
            if (DpUtils.isAnyConj(edge) && processCC) {           	
            	boolean cont = false;
            	for(SemanticGraphEdge c : conjunct) {
            		if(c.getDependent().lemma().equals("&") && nextToVerb(depTree, root.index(), edge.getDependent().index(), c.getDependent().index())) {
            			cont = true;
            			break;
            		}
            	}
            	
            	if(cont)
            		continue;
            	
                IndexedWord newRoot = edge.getDependent();
                SemanticGraph newSemanticGraph = new SemanticGraph(semanticGraph);
                if(predet != null && predet.getDependent().lemma().equals("both"))
                	constituent.getExcludedVertexes().add(predet.getDependent()); 
                
                IndexedConstituent newConstituent = constituent.clone();
                newConstituent.setSemanticGraph(newSemanticGraph);
                if (firstLevel)
                    newConstituent.setRoot(newRoot);
                constituents.add(newConstituent);
                
                // Assign all the parents to the conjoint
                Collection<IndexedWord> parents = newSemanticGraph.getParents(root);
                for (IndexedWord parent : parents) {
                    GrammaticalRelation reln = newSemanticGraph.reln(parent, root);
                    double weight = newSemanticGraph.getEdge(parent, root).getWeight();
                    newSemanticGraph.addEdge(parent, newRoot, reln, weight);
                }

                // Checks if the children also belong to the conjoint and if they do, it assignes
                // them
                for (SemanticGraphEdge ed : outedges) {
                    IndexedWord child = ed.getDependent();
                    if(DpUtils.isPredet(ed) && ed.getDependent().lemma().equals("both")) { //if it is one level down
                    	semanticGraph.removeEdge(ed);
                    } else if (!DpUtils.isAnyConj(ed) && !DpUtils.isCc(ed) && !DpUtils.isPreconj(ed)
                            && isDescendant(depTree, newRoot.index(), root.index(), child.index())) {
                        GrammaticalRelation reln = newSemanticGraph.reln(root, child);
                        double weight = newSemanticGraph.getEdge(root, child).getWeight();
                        newSemanticGraph.addEdge(newRoot, child, reln, weight);
                    }
                }

                // disconect the root of the conjoint from the new graph
                List<SemanticGraphEdge> inedges = newSemanticGraph.getIncomingEdgesSorted(root);
                for (SemanticGraphEdge redge : inedges)
                    newSemanticGraph.removeEdge(redge);
                semanticGraph.removeEdge(edge);

                // It passes the constituent with the correct root, if it is the first level it
                // should be the new constituent
                if (firstLevel) {
                    generateConstituents(newSemanticGraph, depTree, newConstituent, newRoot,
                            constituents, false);
                } else {
                    generateConstituents(newSemanticGraph, depTree, constituent, newRoot,
                            constituents, false);
                }
                
                
                // deletes the edge containing the conjunction e.g. and, or, but, etc
            } else if ((DpUtils.isCc(edge) || DpUtils.isPreconj(edge))&& processCC && !edge.getDependent().lemma().equals("&")) {
                semanticGraph.removeEdge(edge);
            } else if(!DpUtils.isPredet(edge) && !constituent.excludedVertexes.contains(edge.getDependent()))
                generateConstituents(semanticGraph, depTree, constituent, edge.getDependent(),
                        constituents, false);
        }

    }

	/** Checks if a node depending on one conjoint also depends to the other */
    //"He buys and sells electronic products" "Is products depending on both sells and buys?"
    private static boolean isDescendant(Tree parse, int indexCheck, int indexPivot,
            int indexElement) {
        Tree pivot = parse.getLeaves().get(indexPivot - 1); // because tree parse indexing system
                                                            // starts with 0
        Tree check = parse.getLeaves().get(indexCheck - 1);
        Tree element = parse.getLeaves().get(indexElement - 1);

        while ((!element.value().equals("ROOT"))) {// find a common parent between the head conjoint
                                                   // and the constituent of the element
            if (element.pathNodeToNode(element, pivot) != null) // is this efficient enough?
                break;
            element = element.parent(parse);
        }

        List<Tree> path = element.pathNodeToNode(element, check); // find a path between the common
                                                                  // parent and the other conjoint

        if (path != null)
            return true;
        else
            return false;
    }


    /** Retrieves the heads of the clauses according to the CCs processing options. The result contains
     * verbs conjoined and a complement if it is conjoined with a verb.*/
    public static List<IndexedWord> getIndexedWordsConj(SemanticGraph semanticGraph, Tree depTree,
            IndexedWord root, GrammaticalRelation rel, List<SemanticGraphEdge> toRemove,
            Options option) {
        List<IndexedWord> ccs = new ArrayList<IndexedWord>(); // to store the conjoints
        ccs.add(root);
        List<SemanticGraphEdge> outedges = semanticGraph.outgoingEdgeList(root);
        for (SemanticGraphEdge edge : outedges) {
            if (edge.getRelation().equals(rel)) {
                List<SemanticGraphEdge> outed = semanticGraph
                        .outgoingEdgeList(edge.getDependent());
                // first condition tests if verbs are involved in the conjoints. Conjunctions between complements are treated elsewhere. 
                boolean ccVerbs = edge.getDependent().tag().charAt(0) == 'V'
                        || edge.getGovernor().tag().charAt(0) == 'V';
                //This condition will check if there is a cop conjoined with a verb
                boolean ccCop = DpUtils.findFirstOfRelationOrDescendent(outed,
                        EnglishGrammaticalRelations.COPULA) != null;               
                // this condition checks if there are two main clauses conjoined by the CC
                boolean ccMainClauses = DpUtils.findFirstOfRelationOrDescendent(outed,
                        EnglishGrammaticalRelations.SUBJECT) != null ||  DpUtils.findFirstOfRelationOrDescendent(outed,
                                EnglishGrammaticalRelations.EXPLETIVE) != null;
                
                // This flag will check if the cc should be processed according to the flag and the
                // shared elements.
                boolean notProcess = !option.processCcAllVerbs && outed.isEmpty()
                        && shareAll(outedges, depTree, root, edge.getDependent());

                if ((ccVerbs || ccCop) && !ccMainClauses && !notProcess) {
                	ccs.add(edge.getDependent());
                 }
                    
                // Disconnects the conjoints. Independent clauses are always disconnected.
                if (((ccVerbs || ccCop) && !notProcess) || ccMainClauses) {
                    toRemove.add(edge);
                    
                    //To remove the coordination
                    if (option.processCcAllVerbs || !notProcess) {
                        List<SemanticGraphEdge> conjunct = DpUtils.getEdges(outedges,
                                EnglishGrammaticalRelations.COORDINATION);
                        for (SemanticGraphEdge e : conjunct) {
                            if (e.getDependent().index() > edge.getDependent().index())
                                continue;
                            if (nextToVerb(depTree, root.index(), edge.getDependent().index(), e
                                    .getDependent().index())) {
                                toRemove.add(e);
                                break;
                            }
                        }
                    }
                }
            }
        }
        if(ccs.size() > 1)
        	rewriteGraph(semanticGraph, depTree, ccs);
        return ccs;
    }

    /** Rewrites the graph so that each conjoint is independent from each other.
     * They will be disconnected and each dependent correspondignly assigned */
    private static void rewriteGraph(SemanticGraph semanticGraph, Tree depTree,
			List<IndexedWord> ccs) {
        
    	for(int i = 0; i < ccs.size(); i++) {
    		for(int j = i + 1; j < ccs.size(); j++) {
    			//Connect each node in ccs to its parent
    	        for (SemanticGraphEdge ed : semanticGraph.getIncomingEdgesSorted(ccs.get(i))) {
    	        	if(semanticGraph.getParents(ccs.get(j)).contains(ed.getGovernor())) continue;
    	            semanticGraph.addEdge(ed.getGovernor(), ccs.get(j), ed.getRelation(), ed.getWeight());
    	        }

    	       //Check if the dependents of the main conjoint are also dependent on each of the conjoints
    	       // and assign them in each case.
    	        for (SemanticGraphEdge ed : semanticGraph.getOutEdgesSorted(ccs.get(i))) {
    	            IndexedWord child = ed.getDependent();
    	            if(semanticGraph.getChildren(ccs.get(j)).contains(child)) continue;
    	            if (!DpUtils.isAnyConj(ed) && !DpUtils.isCc(ed)
    	                    && isDescendant(depTree, ccs.get(j).index(), ccs.get(i).index(), child.index())) {
    	                semanticGraph.addEdge(ccs.get(j), child, ed.getRelation(), ed.getWeight());
    	            }
    	        }
    			
    		}
    	}    	
	}

    /** Checks if two nodes are conjoined by a given conjunction */
    private static boolean nextToVerb(Tree depTree, int firstVerb, int secondVerb, int conj) {
        Tree fverb = depTree.getLeaves().get(firstVerb - 1);
        Tree sverb = depTree.getLeaves().get(secondVerb - 1);
        Tree conjv = depTree.getLeaves().get(conj - 1);

        // This will lead us to the level in the tree we want to compare
        conjv = conjv.parent(depTree);

        
        List<Tree> siblings = conjv.siblings(depTree);
        Tree[] children = conjv.parent(depTree).children();
        if (children.length == 0)
            return false;

        // This will give the node of the conjoint dominating the coordination
        while (!siblings.contains(fverb)) {
            fverb = fverb.parent(depTree);
            if (fverb.equals(depTree))
                return false;
        }

        // same for the other conjoint
        while (!siblings.contains(sverb)) {
            sverb = sverb.parent(depTree);
            if (sverb.equals(depTree))
                return false;
        }

        Integer fv = null;
        Integer sv = null;

        // This will take the indexes of the nodes dominating the conjoint
        for (int i = 0; i < children.length; i++) {
            if (children[i].equals(fverb))
                fv = i;
            else if (children[i].equals(sverb))
                sv = i;
            if (fv != null & sv != null)
                break;
        }
        

        // This will check if they are continuous
        if(fv == null || sv == null)
        	return false;
      //Assumes that the minimum distance between adjacent conjoints is 2 in the most usual case---> a,b,c and d
      //It is <= 3 to work in the case a,b,c,and, d In the last one the distance is 3.
        else if (sv - fv <= 3)    
        	return true;                   
            
        else
            return false;

    }

    /** Checks if two conjoints verbs share all dependents */
    private static boolean shareAll(List<SemanticGraphEdge> outedges, Tree depTree,
            IndexedWord root, IndexedWord conj) {
        for (SemanticGraphEdge edge : outedges) {
            if (DpUtils.isAnySubj(edge) || edge.getDependent().equals(conj))
                continue;
            else if (!isDescendant(depTree, conj.index(), root.index(), edge.getDependent()
                    .index()))
                return false;
        }

        return true;
    }

}
