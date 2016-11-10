package edu.uw.neuralccg.model;

import com.google.common.base.Preconditions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import edu.uw.easysrl.corpora.CCGBankDependencies.DependencyParse;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.main.InputReader;
import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.evaluation.CCGBankEvaluation;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.syntax.model.AgendaItem;
import edu.uw.easysrl.syntax.model.Model;
import edu.uw.easysrl.syntax.parser.AbstractParser;
import edu.uw.easysrl.syntax.parser.Agenda;
import edu.uw.easysrl.syntax.tagger.Tagger;
import edu.uw.easysrl.syntax.tagger.Tagger.ScoredCategory;
import edu.uw.neuralccg.evaluation.DependencyEvaluator;
import edu.uw.neuralccg.util.RetrievalStatistics;

public class OracleModel extends Model {
    public static final Logger log = LoggerFactory.getLogger(OracleModel.class);

    private static final double LEFT_BRANCH_PENALTY = 0.00001;
    private static final double UNARY_PENALTY = 0.001;
    private static final double INCORRECT_PENALTY = Double.POSITIVE_INFINITY;
    private final DependencyEvaluator evaluator;
    private final List<Category> goldCategories;
    private final List<InputWord> leaves;
    private final Set<ResolvedDependency> goldDependencies;
    private final Set<Category> categories;

    public OracleModel(final List<InputWord> leaves,
                       final List<Category> goldCategories,
                       final Set<ResolvedDependency> goldDependencies,
                       final Collection<Category> lexicalCategories,
                       final DependencyEvaluator evaluator) {
        super(leaves.size());
        this.goldCategories = goldCategories;
        this.goldDependencies = goldDependencies;
        this.categories = new HashSet<>(lexicalCategories);
        this.leaves = leaves;
        this.evaluator = evaluator;
    }

    private double getPenalty(SyntaxTreeNode node) {
        final RetrievalStatistics stats = evaluator.evaluateStep(goldDependencies, node, false, leaves);
        if (stats.getCorrect() == stats.getPredicted() && stats.getCorrect() == stats.getGold()) {
            return 0.0;
        } else {
            return (stats.getPredicted() - stats.getCorrect() + stats.getGold() - stats.getCorrect()) * INCORRECT_PENALTY;
        }
    }

    @Override
    public void buildAgenda(final Agenda agenda, final List<InputReader.InputWord> words) {
        Preconditions.checkArgument(goldCategories.size() == words.size());
        for (int i = 0; i < words.size(); i++) {
            if (categories.contains(goldCategories.get(i))) {
                final InputReader.InputWord word = words.get(i);
                final SyntaxTreeNode node = new SyntaxTreeNodeLeaf(word.word, word.pos, word.ner,
                        goldCategories.get(i), i, true);
                agenda.add(new AgendaItem(node, 0, 0, i, 1, true));
            }
        }
    }

    @Override
    public AgendaItem combineNodes(final AgendaItem leftChild, final AgendaItem rightChild, final SyntaxTreeNode node) {
        double score = leftChild.getInsideScore()
                + rightChild.getInsideScore()
                - leftChild.getSpanLength() * LEFT_BRANCH_PENALTY
                - getPenalty(node);
        final int length = leftChild.getSpanLength() + rightChild.getSpanLength();
        return new AgendaItem(node, score, 0, leftChild.getStartOfSpan(), length, true);
    }

    @Override
    public AgendaItem unary(final AgendaItem child, final SyntaxTreeNode node, final AbstractParser.UnaryRule rule) {
        double score = child.getInsideScore()
                - UNARY_PENALTY
                - getPenalty(node);
        return new AgendaItem(node, score, 0, child.getStartOfSpan(), child.getSpanLength(), true);
    }

    @Override
    public double getUpperBoundForWord(int index) {
        return 0;
    }

    public static class GoldInputToParser extends InputToParser {
        private static final long serialVersionUID = 1L;

        private final Set<ResolvedDependency> goldDependencies;
        private SyntaxTreeNode oracleParse;

        public GoldInputToParser(final DependencyParse goldParse) {
            this(goldParse, goldParse.getLeaves()
                    .stream()
                    .map(SyntaxTreeNode::getCategory)
                    .map(c -> new Tagger.ScoredCategory(c, 0))
                    .map(Collections::singletonList)
                    .collect(Collectors.toList()));
            this.oracleParse = null;
        }

        public GoldInputToParser(final DependencyParse goldParse, final List<List<ScoredCategory>> inputSupertags) {
            super(
                    goldParse.getWords(),
                    goldParse.getLeaves()
                            .stream()
                            .map(SyntaxTreeNode::getCategory)
                            .collect(Collectors.toList()),
                    inputSupertags,
                    inputSupertags != null);
            this.goldDependencies = CCGBankEvaluation.asResolvedDependencies(goldParse.getDependencies());
            this.oracleParse = null;
        }

        public GoldInputToParser(final GoldInputToParser other, final List<List<ScoredCategory>> inputSupertags) {
            super(
                    other.getInputWords(),
                    other.getGoldCategories(),
                    inputSupertags,
                    inputSupertags != null);
            this.goldDependencies = other.goldDependencies;
            this.oracleParse = other.oracleParse;
        }

        public Set<ResolvedDependency> getGoldDependencies() {
            return goldDependencies;
        }

        public void setOracleParse(final SyntaxTreeNode oracleParse) {
            this.oracleParse = oracleParse;
        }

        public SyntaxTreeNode getOracleParse() {
            return oracleParse;
        }
    }

    public static class OracleModelFactory extends ModelFactory {
        private final Collection<Category> lexicalCategories;
        private final DependencyEvaluator evaluator;

        public OracleModelFactory(final Collection<Category> lexicalCategories,
                                  final DependencyEvaluator evaluator) {
            super();
            this.lexicalCategories = lexicalCategories;
            this.evaluator = evaluator;
        }

        @Override
        public OracleModel make(final InputToParser input) {
            Preconditions.checkArgument(input instanceof GoldInputToParser);
            return new OracleModel(
                    input.getInputWords(),
                    input.getGoldCategories(),
                    ((GoldInputToParser) input).goldDependencies,
                    lexicalCategories,
                    evaluator);
        }

        @Override
        public Collection<Category> getLexicalCategories() {
            return lexicalCategories;
        }

        @Override
        public boolean isUsingDependencies() {
            return true;
        }

        @Override
        public boolean isUsingDynamicProgram() {
            return false;
        }
    }
}