package de.mpii.clausie;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import de.mpii.clausie.Constituent.Flag;
import edu.stanford.nlp.io.EncodingPrintWriter.out;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.objectbank.TokenizerFactory;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.parser.lexparser.LexicalizedParserQuery;
import edu.stanford.nlp.pipeline.ParserAnnotatorUtils;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.semgraph.SemanticGraph;

public class ClausIE {
	Tree depTree;
	SemanticGraph semanticGraph;

	List<Clause> clauses = new ArrayList<Clause>();

	List<Proposition> propositions = new ArrayList<Proposition>();

	PropositionGenerator propositionGenerator = new DefaultPropositionGenerator(
			this);

	Options options;

	private LexicalizedParser lp;
	private TokenizerFactory<CoreLabel> tokenizerFactory;
	private LexicalizedParserQuery lpq;

	// Indicates if the clause processed comes from an xcomp constituent of the
	// original sentence
	boolean xcomp = false;

	// -- construction
	// ----------------------------------------------------------------------------

	public ClausIE(Options options) {
		this.options = options;
	}

	public ClausIE() {
		this(new Options());
	}
	
	public ClausIE(LexicalizedParser lp, TokenizerFactory<CoreLabel> tokenizerFactory,
			LexicalizedParserQuery lpq) {
		this(new Options());
		this.lp = lp;
		this.tokenizerFactory = tokenizerFactory;
		this.lpq = lpq;
	}

	// -- misc method
	// -----------------------------------------------------------------------------

	public Options getOptions() {
		return options;
	}

	public void clear() {
		semanticGraph = null;
		depTree = null;
		clauses.clear();
		propositions.clear();
	}

	// -- parsing
	// ---------------------------------------------------------------------------------

	/** Initializes the Stanford parser. */
	public void initParser() {
		lp = LexicalizedParser
				.loadModel("edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz");
		tokenizerFactory = PTBTokenizer
				.factory(new CoreLabelTokenFactory(), "");
		lpq = lp.parserQuery();
	}

	/** Clears and parses a new sentence. */
	public void parse(String sentence) {
		clear();
		List<CoreLabel> tokenizedSentence = tokenizerFactory.getTokenizer(
				new StringReader(sentence)).tokenize();
		lpq.parse(tokenizedSentence); // what about the confidence?
		depTree = lpq.getBestParse();
		// use uncollapsed dependencies to facilitate tree creation
		semanticGraph = ParserAnnotatorUtils
				.generateUncollapsedDependencies(depTree);
	}

	/** Returns the constituent tree for the sentence. */
	public Tree getDepTree() {
		return depTree;
	}

	/** Returns the dependency tree for the sentence. */
	public SemanticGraph getSemanticGraph() {
		return semanticGraph;
	}

	// -- clause detection
	// ------------------------------------------------------------------------

	/** Detects clauses in the sentence. */
	public void detectClauses() {
		ClauseDetector.detectClauses(this);
	}

	/** Returns clauses in the sentence. */
	public List<Clause> getClauses() {
		return clauses;
	}

	// -- proposition generation
	// ------------------------------------------------------------------

	/** Generates propositions from the clauses in the sentence. */
	public void generatePropositions() {
		propositions.clear();

		// holds alternative options for each constituents (obtained by
		// processing coordinated conjunctions and xcomps)
		final List<List<Constituent>> constituents = new ArrayList<List<Constituent>>();

		// which of the constituents are required?
		final List<Flag> flags = new ArrayList<Flag>();
		final List<Boolean> include = new ArrayList<Boolean>();

		// holds all valid combination of constituents for which a proposition
		// is to be generated
		final List<List<Boolean>> includeConstituents = new ArrayList<List<Boolean>>();

		// let's start
		for (Clause clause : clauses) {
			// process coordinating conjunctions
			constituents.clear();
			for (int i = 0; i < clause.constituents.size(); i++) {
				// if(xcomp && clause.subject == i) continue; //An xcomp does
				// not have an internal subject so should not be processed here
				Constituent constituent = clause.constituents.get(i);
				List<Constituent> alternatives;
				if (!(xcomp && clause.subject == i)
						&& constituent instanceof IndexedConstituent
						// the processing of the xcomps is done in Default
						// proposition generator. 
						// Otherwise we get duplicate propositions.
						&& !clause.xcomps.contains(i)
						&& ((i == clause.verb && options.processCcAllVerbs) || (i != clause.verb && options.processCcNonVerbs))) {
					alternatives = ProcessConjunctions.processCC(depTree,
							clause, constituent, i);
				} else if (!(xcomp && clause.subject == i)
						&& clause.xcomps.contains(i)) {
					alternatives = new ArrayList<Constituent>();
					ClausIE xclausIE = new ClausIE(options);
					xclausIE.semanticGraph = semanticGraph;
					xclausIE.depTree = depTree;
					xclausIE.xcomp = true;
					xclausIE.clauses = ((XcompConstituent) clause.constituents
							.get(i)).getClauses();
					xclausIE.generatePropositions();
					for (Proposition p : xclausIE.propositions) {
						StringBuilder sb = new StringBuilder();
						String sep = "";
						for (int j = 0; j < p.constituents.size(); j++) {
							if (j == 0)    // to avoid including the subjecct, We
								continue;  // could also generate the prop
							               // without the subject											
							sb.append(sep);
							sb.append(p.constituents.get(j));
							sep = " ";
						}
						alternatives.add(new TextConstituent(sb.toString(),
								constituent.type));
					}
				} else {
					alternatives = new ArrayList<Constituent>(1);
					alternatives.add(constituent);
				}
				constituents.add(alternatives);
			}

			// create a list of all combinations of constituents for which a
			// proposition should be generated
			includeConstituents.clear();
			flags.clear();
			include.clear();
			for (int i = 0; i < clause.constituents.size(); i++) {
				Flag flag = clause.getFlag(i, options);
				flags.add(flag);
				include.add(!flag.equals(Flag.IGNORE));
			}
			if (options.nary) {
				// we always include all constituents for n-ary ouput 
				// (optional parts marked later)
				includeConstituents.add(include);
			} else {
				// triple mode; determine which parts are required
				for (int i = 0; i < clause.constituents.size(); i++) {
					include.set(i, flags.get(i).equals(Flag.REQUIRED));
				}

				// create combinations of required/optional constituents
				new Runnable() {
					int noOptional;

					@Override
					public void run() {
						noOptional = 0;
						for (Flag f : flags) {
							if (f.equals(Flag.OPTIONAL))
								noOptional++;
						}
						run(0, 0, new ArrayList<Boolean>());
					}

					private void run(int pos, int selected, List<Boolean> prefix) {
						if (pos >= include.size()) {
							if (selected >= Math.min(options.minOptionalArgs,
									noOptional)
									&& selected <= options.maxOptionalArgs) {
								includeConstituents.add(new ArrayList<Boolean>(
										prefix));
							}
							return;
						}
						prefix.add(true);
						if (include.get(pos)) {
							run(pos + 1, selected, prefix);
						} else {
							if (!flags.get(pos).equals(Flag.IGNORE)) {
								run(pos + 1, selected + 1, prefix);
							}
							prefix.set(prefix.size() - 1, false);
							run(pos + 1, selected, prefix);
						}
						prefix.remove(prefix.size() - 1);
					}
				}.run();
			}

			// create a temporary clause for which to generate a proposition
			final Clause tempClause = clause.clone();

			// generate propositions
			new Runnable() {
				@Override
				public void run() {
					// select which constituents to include
					for (List<Boolean> include : includeConstituents) {
						// now select an alternative for each constituent
						selectConstituent(0, include);
					}
				}

				void selectConstituent(int i, List<Boolean> include) {
					if (i < constituents.size()) {
						if (include.get(i)) {
							List<Constituent> alternatives = constituents
									.get(i);
							for (int j = 0; j < alternatives.size(); j++) {
								tempClause.constituents.set(i,
										alternatives.get(j));
								selectConstituent(i + 1, include);
							}
						} else {
							selectConstituent(i + 1, include);
						}
					} else {
						// everything selected; generate
						propositionGenerator.generate(propositions, tempClause,
								include);
					}
				}
			}.run();
		}
	}

	public List<Proposition> getPropositions() {
		return propositions;
	}

	// -- command-line interface
	// ------------------------------------------------------------------

	public static void main(String[] args) throws IOException {
		OptionParser optionParser = new OptionParser();
		optionParser
				.accepts("f",
						"input file (if absent, ClausIE reads from stdin)")
				.withRequiredArg().describedAs("file").ofType(String.class);
		optionParser
				.accepts(
						"l",
						"if set, sentence identifier is read from input file (with lines of form: <id>\\t<sentence>)");
		optionParser
				.accepts("o",
						"output file (if absent, ClausIE writes to stdout)")
				.withRequiredArg().describedAs("file").ofType(String.class);
		optionParser.accepts("c", "configuration file").withRequiredArg()
				.describedAs("file").ofType(String.class);
		optionParser.accepts("v", "verbose output");
		optionParser.accepts("h", "print help");
		optionParser.accepts("s", "print sentence");
		optionParser.accepts("p", "print sentence confidence");
		OptionSet options;
		try {
			options = optionParser.parse(args);
		} catch (OptionException e) {
			System.err.println(e.getMessage());
			out.println("");
			optionParser.printHelpOn(System.out);
			return;
		}
		// help
		if (options.has("h")) {
			optionParser.printHelpOn(System.out);
		}

		// setup input and output
		InputStream in = System.in;
		OutputStream out = System.out;
		if (options.has("f")) {
			in = new FileInputStream((String) options.valueOf("f"));
		}
		if (options.has("o")) {
			out = new FileOutputStream((String) options.valueOf("o"));
		}

		// is there an options file

		// create a ClausIE instance and set options
		ClausIE clausIE;
		if (options.has("c")) {
			clausIE = new ClausIE(new Options((String) options.valueOf("c")));
		} else {
			clausIE = new ClausIE();
		}
		clausIE.initParser();
		if (options.has("v")) {
			clausIE.getOptions().print(out, "# ");
		}

		// run
		DataInput din = new DataInputStream(in);
		PrintStream dout = new PrintStream(out);
		int lineNo = 1;
		for (String line = din.readLine(); line != null; line = din.readLine(), lineNo++) {
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#"))
				continue;
			int sentenceId = lineNo;
			if (options.has("l")) {
				int tabIndex = line.indexOf('\t');
				sentenceId = Integer.parseInt(line.substring(0, tabIndex));
				line = line.substring(tabIndex + 1).trim();
			}
			if (options.has("v")) {
				dout.print("# Line ");
				dout.print(lineNo);
				if (options.has("l")) {
					dout.print(" (id ");
					dout.print(sentenceId);
					dout.print(")");
				}
				dout.print(": ");
				dout.print(line);
				dout.println();
			}
			clausIE.parse(line);
			if (options.has("v")) {
				dout.print("# Semantic graph: ");
				dout.println(clausIE.getSemanticGraph().toFormattedString()
						.replaceAll("\n", "\n#                ").trim());
			}
			clausIE.detectClauses();
			if (options.has("v")) {
				dout.print("#   Detected ");
				dout.print(clausIE.getClauses().size());
				dout.println(" clause(s).");
				for (Clause clause : clausIE.getClauses()) {
					dout.print("#   - ");
					dout.print(clause.toString(clausIE.options));
					dout.println();
				}
			}
			clausIE.generatePropositions();
			// dout.print("\t");
			if (options.has("s")) {
				dout.print(line);
				dout.println();
			}

			// dout.print("\t");
			// dout.println();
			for (Proposition p : clausIE.getPropositions()) {
				dout.print(sentenceId);
				for (String c : p.constituents) {
					// TODO: correct escaping
					dout.print("\t\"");
					dout.print(c);
					dout.print("\"");
				}
				if (options.has("p")) {
					dout.print("\t");
					dout.print(clausIE.lpq.getPCFGScore());
				}
				dout.println();
			}
		}

		// shutdown
		if (options.has("f")) {
			in.close();
		}
		if (options.has("o")) {
			out.close();
		}
	}
}
