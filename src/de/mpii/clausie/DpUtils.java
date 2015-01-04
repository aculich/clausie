package de.mpii.clausie;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.trees.EnglishGrammaticalRelations;
import edu.stanford.nlp.trees.GrammaticalRelation;
import edu.stanford.nlp.trees.semgraph.SemanticGraph;
import edu.stanford.nlp.trees.semgraph.SemanticGraphEdge;


/** This class provides a set of utilities to work with {@link SemanticGraph}
 * For details on the Dependency parser @see <a href="nlp.stanford.edu/software/dependencies_manual.pdf">the Stanford Parser manual  
 * 
 * @date $LastChangedDate: 2013-04-24 11:35:23 +0200 (Wed, 24 Apr 2013) $
 * @version $LastChangedRevision: 739 $ */
public class DpUtils {

	/** Finds the first occurrence of a grammatical relation in a set of edges */
    public static SemanticGraphEdge findFirstOfRelation(List<SemanticGraphEdge> edges,
            GrammaticalRelation rel) {
        for (SemanticGraphEdge e : edges) {
            if (rel.equals(e.getRelation())) {
                return e;
            }
        }
        return null;
    }

    /** Finds the first occurrence of a grammatical relation or its descendants in a set of edges */
    public static SemanticGraphEdge findFirstOfRelationOrDescendent(List<SemanticGraphEdge> edges,
            GrammaticalRelation rel) {
        for (SemanticGraphEdge e : edges) {
            if (rel.isAncestor(e.getRelation())) {
                return e;
            }
        }
        return null;
    }

    /** Finds the first occurrence of a grammatical relation or its descendants for a relative pronoun */
    public static SemanticGraphEdge findDescendantRelativeRelation(SemanticGraph semanticGraph,
            IndexedWord root, GrammaticalRelation rel) {
        List<SemanticGraphEdge> outedges = semanticGraph.getOutEdgesSorted(root);
        for (SemanticGraphEdge e : outedges) {
            if (e.getDependent().tag().charAt(0) == 'W' && rel.isAncestor(e.getRelation())) {
                return e;
            } else
                return findDescendantRelativeRelation(semanticGraph, e.getDependent(), rel);
        }
        return null;
    }

    /** Finds all occurrences of a grammatical relation or its descendants in a set of edges */
    public static List<SemanticGraphEdge> getEdges(List<SemanticGraphEdge> edges,
            GrammaticalRelation rel) {
        List<SemanticGraphEdge> result = new ArrayList<SemanticGraphEdge>();
        for (SemanticGraphEdge e : edges) {
            if (rel.isAncestor(e.getRelation())) {
                result.add(e);
            }
        }
        return result;
    }

    /** Checks if a given grammatical relation is contained in a set of edges */
    public static boolean containsRelation(List<SemanticGraphEdge> edges, GrammaticalRelation rel) {
        return findFirstOfRelation(edges, rel) != null;
    }

    /** Checks if a given edge holds a subject relation*/
    public static boolean isAnySubj(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.SUBJECT.isAncestor(edge.getRelation());
    }

    /** Checks if a given edge holds a nominal subject relation*/
    public static boolean isNsubj(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.NOMINAL_SUBJECT.equals(edge.getRelation());
    }

    /** Checks if a given edge holds a clausal subject relation*/
    public static boolean isCsubj(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.CLAUSAL_SUBJECT.equals(edge.getRelation());
    }

    /** Checks if a given edge holds a clausal passive subject relation*/
    public static boolean isCsubjpass(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.CLAUSAL_PASSIVE_SUBJECT.equals(edge.getRelation());
    }

    /** Checks if a given edge holds a nominal passive subject relation*/
    public static boolean isNsubjpass(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.NOMINAL_PASSIVE_SUBJECT.equals(edge.getRelation());
    }

    /** Checks if a given edge holds an external subject relation of an xcomp relation */
    public static boolean isXsubj(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.CONTROLLING_SUBJECT.equals(edge.getRelation());
    }

    /** Checks if a given edge holds an object relation */
    public static boolean isAnyObj(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.OBJECT.isAncestor(edge.getRelation());
    }

    /** Checks if a given edge holds a prepositional object relation*/
    public static boolean isPobj(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.PREPOSITIONAL_OBJECT.equals(edge.getRelation());
    }

    /** Checks if a given edge holds a direct object relation */
    public static boolean isDobj(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.DIRECT_OBJECT.equals(edge.getRelation());
    }

    /** Checks if a given edge holds an indirect object relation */
    public static boolean isIobj(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.INDIRECT_OBJECT.equals(edge.getRelation());
    }

    /** Checks if a given edge holds a negation relation */
    static boolean isNeg(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.NEGATION_MODIFIER.equals(edge.getRelation());
    }

    /** Checks if a given edge holds the 'dep' relation */
    static boolean isDep(SemanticGraphEdge edge) {
        return edge.toString().equals("dep");
    }

    /** Checks if a given edge holds a phrasal verb particle relation */
    static boolean isPrt(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.PHRASAL_VERB_PARTICLE.equals(edge.getRelation());
    }

    /** Checks if a given edge holds an apposittional relation */
    static boolean isAppos(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.APPOSITIONAL_MODIFIER.equals(edge.getRelation());
    }

    /** Checks if a given edge holds an purpose clause modifier relation */
    public static boolean isPurpcl(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.PURPOSE_CLAUSE_MODIFIER.equals(edge.getRelation());
    }

    /** Checks if a given edge holds a xcomp relation */
    public static boolean isXcomp(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.XCLAUSAL_COMPLEMENT.equals(edge.getRelation());
    }

    /** Checks if a given edge holds a complementizer relation */
    public static boolean isComplm(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.COMPLEMENTIZER.equals(edge.getRelation());
    }

    /** Checks if a given edge holds an agent relation */
    public static boolean isAgent(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.AGENT.equals(edge.getRelation());
    }

    /** Checks if a given edge holds an expletive relation */
    public static boolean isExpl(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.EXPLETIVE.equals(edge.getRelation());
    }

    /** Checks if a given edge holds an adjectival complement relation */
    public static boolean isAcomp(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.ADJECTIVAL_COMPLEMENT.equals(edge.getRelation());
    }

    /** Checks if a given edge holds a prepositional modifier relation */
    public static boolean isAnyPrep(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.PREPOSITIONAL_MODIFIER.isAncestor(edge.getRelation());
    }

    /** Checks if a given edge holds a copular relation */
    public static boolean isCop(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.COPULA.equals(edge.getRelation());
    }

    /** Checks if a given edge holds an adverbial clausal relation */
    public static boolean isAdvcl(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.ADV_CLAUSE_MODIFIER.equals(edge.getRelation());
    }

    /** Checks if a given edge holds a relative clause modifier relation */
    public static boolean isRcmod(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.RELATIVE_CLAUSE_MODIFIER.equals(edge.getRelation());
    }

    /** Checks if a given edge holds a clausal complement relation */
    public static boolean isCcomp(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.CLAUSAL_COMPLEMENT.equals(edge.getRelation());
    }

    /** Checks if a given edge holds an adverbial modifier relation */
    public static boolean isAdvmod(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.ADVERBIAL_MODIFIER.equals(edge.getRelation());
    }

    /** Checks if a given edge holds an np adverbial modifier relation */
    public static boolean isNpadvmod(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.NP_ADVERBIAL_MODIFIER.equals(edge.getRelation());
    }

    /** Checks if a given edge holds a marker relation */
    public static boolean isMark(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.MARKER.equals(edge.getRelation());
    }

    /** Checks if a given edge holds a propositional complement relation */
    public static boolean isPcomp(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.PREPOSITIONAL_COMPLEMENT.equals(edge.getRelation());
    }

    /** Checks if a given edge holds a possession modifier relation */
    public static boolean isPoss(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.POSSESSION_MODIFIER.equals(edge.getRelation());
    }

    /** Checks if a given edge holds a possessive modifier relation */
    public static boolean isPosse(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.POSSESSIVE_MODIFIER.equals(edge.getRelation());
    }

    /** Checks if a given edge holds a participial modifier relation */
    public static boolean isPartMod(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.PARTICIPIAL_MODIFIER.equals(edge.getRelation());
    }

    /** Checks if a given edge holds a temporal modifier relation */
    public static boolean isTmod(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.TEMPORAL_MODIFIER.equals(edge.getRelation());
    }

    /** Checks if a given edge holds a conjunct relation */
    public static boolean isAnyConj(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.CONJUNCT.isAncestor(edge.getRelation());
    }

    /** Checks if a given edge holds a preconjunct modifier relation */
    public static boolean isPreconj(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.PRECONJUNCT.equals(edge.getRelation());
    }

    /** Checks if a given edge holds a coordination relation */
    public static boolean isCc(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.COORDINATION.equals(edge.getRelation());
    }

    /** Checks if a given edge holds an auxiliar modifier relation */
    public static boolean isAux(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.AUX_MODIFIER.equals(edge.getRelation());
    }

    /** Checks if a given edge holds an auxiliar passive modifier relation */
    public static boolean isAuxPass(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.AUX_PASSIVE_MODIFIER.equals(edge.getRelation());
    }

    /** Checks if a given edge holds a 'rel' relation */
    public static boolean isRel(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.RELATIVE.equals(edge.getRelation());
    }
    
    /** Checks if a given edge holds a multi word expression relation */
    public static boolean isMwe(SemanticGraphEdge edge) {
		return EnglishGrammaticalRelations.MULTI_WORD_EXPRESSION.equals(edge.getRelation());
	}
    
    /** Checks if a given edge holds a parataxis relation */
    public static boolean isParataxis(SemanticGraphEdge edge) {
        return EnglishGrammaticalRelations.PARATAXIS.equals(edge.getRelation());
    }
    
    /** Checks if a given edge holds an infinitival modifier relation */
    public static boolean isInfmod(SemanticGraphEdge edge) {
		return EnglishGrammaticalRelations.INFINITIVAL_MODIFIER.equals(edge.getRelation());
	}
    
    /** Checks if a given edge holds a predeterminer relation */
    public static boolean isPredet(SemanticGraphEdge edge) {
    	return EnglishGrammaticalRelations.PREDETERMINER.equals(edge.getRelation());
	}

    /** Removes some edges from the given semantic graph.
     * 
     * This method traverses the semantic graph starting from the given root. An edge is removed if
     * (1) its child appears in <code>excludeVertexes</code>, (2) its relation appears in
     * <code>excludeRelations</code>, or (3) the edge has the root as parent and its relation
     * appears in <code>excludeRelationsTop</code>. */
    public static void removeEdges(SemanticGraph graph, IndexedWord root,
            Collection<IndexedWord> excludeVertexes,
            Collection<GrammaticalRelation> excludeRelations,
            Collection<GrammaticalRelation> excludeRelationsTop) {
        if (!excludeVertexes.contains(root)) {
            List<SemanticGraphEdge> edgesToRemove = new ArrayList<SemanticGraphEdge>();
            subgraph(graph, root, excludeVertexes, excludeRelations, excludeRelationsTop,
                    edgesToRemove);
            for (SemanticGraphEdge edge : edgesToRemove) {
                graph.removeEdge(edge);
            }
        }
    }

    /** Removes some edges from the given semantic graph.
     * 
     * This method traverses the semantic graph starting from the given root. An edge is removed if
     * its child appears in <code>excludeVertexes</code>. */
    public static void removeEdges(SemanticGraph graph, IndexedWord root,
            Collection<IndexedWord> excludeVertexes) {
        removeEdges(graph, root, excludeVertexes, Collections.<GrammaticalRelation> emptySet(),
                Collections.<GrammaticalRelation> emptySet());
    }

    /** Removes some edges from the given semantic graph.
     * 
     * This method traverses the semantic graph starting from the given root. An edge is removed if
     * its relation appears in <code>excludeRelations</code> or the edge has the root as parent and
     * its relation appears in <code>excludeRelationsTop</code>. */
    public static void removeEdges(SemanticGraph graph, IndexedWord root,
            Collection<GrammaticalRelation> excludeRelations,
            Collection<GrammaticalRelation> excludeRelationsTop) {
        removeEdges(graph, root, Collections.<IndexedWord> emptySet(), excludeRelations,
                excludeRelationsTop);
    }

    /** Implementation for
     * {@link #removeEdges(SemanticGraph, IndexedWord, Collection, Collection, Collection)} */
    private static void subgraph(SemanticGraph graph, IndexedWord root,
            Collection<IndexedWord> excludeVertexes,
            Collection<GrammaticalRelation> excludeRelations,
            Collection<GrammaticalRelation> excludeRelationsTop,
            Collection<SemanticGraphEdge> edgesToRemove) {
        List<SemanticGraphEdge> edges = graph.getOutEdgesSorted(root);
        for (SemanticGraphEdge e : edges) {
            IndexedWord child = e.getDependent();
            if (excludeVertexes.contains(child) || excludeRelations.contains(e.getRelation())
                    || excludeRelationsTop.contains(e.getRelation())) {
                edgesToRemove.add(graph.getEdge(root, child));
            } else {
                subgraph(graph, child, excludeVertexes, excludeRelations,
                        Collections.<GrammaticalRelation> emptySet(), edgesToRemove);
            }
        }
    }

    /** Disconnects independent clauses by removing the edge representing the coordinating conjunction */
    public static void disconectClauses(SemanticGraph graph, Constituent constituent) {
        List<SemanticGraphEdge> outedges = graph
                .getOutEdgesSorted(((IndexedConstituent) constituent).getRoot());
        for (int i = 0; i < outedges.size(); i++) {
            SemanticGraphEdge e = outedges.get(i);
            if (DpUtils.isAnyConj(e)) {
                IndexedWord child = e.getDependent();
                List<SemanticGraphEdge> outNewRoot = graph.getOutEdgesSorted(child);
                SemanticGraphEdge sub = DpUtils.findFirstOfRelationOrDescendent(outNewRoot,
                        EnglishGrammaticalRelations.SUBJECT);
                if (sub != null)
                    graph.removeEdge(e);
            }
        }
    }

    /** Return a set of vertexes to be excluded according to a given collection of grammatical relations */
    public static Set<IndexedWord> exclude(SemanticGraph semanticGraph,
            Collection<GrammaticalRelation> rels, IndexedWord root) {
        Set<IndexedWord> exclude = new TreeSet<IndexedWord>();
        List<SemanticGraphEdge> outedges = semanticGraph.getOutEdgesSorted(root);
        for (SemanticGraphEdge edge : outedges) {
            if (containsAncestor(rels, edge)) {
                exclude.add(edge.getDependent());
            }
        }
        return exclude;
    }

    /** Check if an edge is descendant of any grammatical relation in the given set */
    private static boolean containsAncestor(Collection<GrammaticalRelation> rels,
            SemanticGraphEdge edge) {
        for (GrammaticalRelation rel : rels) {
            if (rel.isAncestor(edge.getRelation()))
                return true;
        }
        return false;
    }
   	
}
