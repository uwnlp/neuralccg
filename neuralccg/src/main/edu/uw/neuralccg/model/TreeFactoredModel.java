package edu.uw.neuralccg.model;

import com.google.common.base.Preconditions;
import com.google.protobuf.InvalidProtocolBufferException;

import com.typesafe.config.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.syntax.model.AgendaItem;
import edu.uw.easysrl.syntax.parser.AbstractParser;
import edu.uw.easysrl.syntax.parser.Agenda;
import edu.uw.easysrl.syntax.parser.ParserListener;
import edu.uw.easysrl.syntax.tagger.Tagger;
import edu.uw.easysrl.syntax.tagger.Tagger.ScoredCategory;
import edu.uw.easysrl.util.Util.Scored;
import edu.uw.neuralccg.SyntaxProto.ParseProto;
import edu.uw.neuralccg.SyntaxProto.SentenceProto;
import edu.uw.neuralccg.TrainProto.GatesProto;
import edu.uw.neuralccg.TrainProto.InitialGatesProto;
import edu.uw.neuralccg.TrainProto.RunConfig;
import edu.uw.neuralccg.TrainProto.ScorerConfig;
import edu.uw.neuralccg.TrainProto.TrainConfig;
import edu.uw.neuralccg.TrainProto.UpdateProto;
import edu.uw.neuralccg.agenda.LazyAgenda;
import edu.uw.neuralccg.agenda.MultiHeadedAgenda;
import edu.uw.neuralccg.agenda.PredicatedAgenda;
import edu.uw.neuralccg.evaluation.DependencyEvaluator;
import edu.uw.neuralccg.evaluation.analysis.ParserStatistics;
import edu.uw.neuralccg.model.OracleModel.GoldInputToParser;
import edu.uw.neuralccg.trainer.Trainer;
import edu.uw.neuralccg.util.IdentityWrapper;
import edu.uw.neuralccg.util.SyntaxUtil;

// If multi-threaded, all usages of this class should be wrapped with
// synchronized(TreeFactoredModelFactory.class) { }
public class TreeFactoredModel extends TrainableModel {
    public static final Logger log = LoggerFactory.getLogger(TreeFactoredModel.class);

    private final static boolean AGENDA_DEPS = false;
    private final static boolean PARSE_DEPS = true;
    private final static boolean USE_ENSEMBLE = true;
    private final static boolean BEST_FIRST = false;

    private static native void initializeCNN(byte[] runConfig);

    private static native void initializeScorer(byte[] scorerConfig);

    private static native void initializeScorer(String checkpointPath);

    private static native void initializeTrainer(byte[] trainConfig);

    private static native void saveCheckpoint(String checkpointPath);

    private static native void initializeSentence(byte[] sentence);

    private static native byte[] initializeSentenceWithGates(byte[] sentence);

    private static native float scoreAndBuildRepresentation(byte[] parse);

    private static native byte[] scoreAndBuildRepresentationWithGates(byte[] parse);

    private static native void applyUpdate(byte[] update);

    private final List<List<Tagger.ScoredCategory>> tagsForWords;
    private final Map<SyntaxTreeNode, Integer> chartIndexes;
    private final TreeFactoredModelFactory factory;
    private final Optional<GoldInputToParser> goldInput;
    private final Optional<Set<IdentityWrapper<SyntaxTreeNode>>> oracleSteps;
    private final InitialGatesProto.Builder initialGates;
    private final List<GatesProto> gates;
    private List<InputWord> words;
    private final Optional<Trainer> trainer;
    private final double constantUpperBound;

    public TreeFactoredModel(final List<List<Tagger.ScoredCategory>> tagsForWords,
                             final TreeFactoredModelFactory factory,
                             final Optional<GoldInputToParser> goldInput,
                             final Optional<Trainer> trainer) {
        super(tagsForWords.size(), trainer);
        this.tagsForWords = tagsForWords;
        this.factory = factory;
        this.goldInput = goldInput;
        this.oracleSteps = goldInput
                .map(GoldInputToParser::getOracleParse)
                .map(oracleParse -> SyntaxUtil.subtreeStream(oracleParse)
                        .map(s -> new IdentityWrapper<>(s, SyntaxUtil::parsesEqual, SyntaxUtil::parseHash))
                        .collect(Collectors.toSet()));
        if (BEST_FIRST) {
            this.constantUpperBound = tagsForWords.stream().map(t -> t.get(0)).mapToDouble(ScoredCategory::getScore).sum();
        } else {
            this.constantUpperBound = 0;
        }
        computeOutsideProbabilities();
        this.chartIndexes = new HashMap<>();
        this.initialGates = InitialGatesProto.newBuilder();
        this.gates = new ArrayList<>();
        this.trainer = trainer;
    }

    public Map<SyntaxTreeNode, Integer> getChartIndexes() {
        return chartIndexes;
    }

    public List<GatesProto> getGates() {
        return gates;
    }

    public InitialGatesProto getInitialGates() {
        return initialGates.build();
    }

    private double scoreAndBuildRepresentation(final SyntaxTreeNode node) {
        final ParseProto parse = SyntaxUtil.toProto(node, chartIndexes).build();
        Preconditions.checkState(chartIndexes.put(node, chartIndexes.size()) == null, node);
        final double score;
        if (factory.keepGates) {
            try {
                final GatesProto currentGates = GatesProto
                        .parseFrom(scoreAndBuildRepresentationWithGates(parse.toByteArray()));
                gates.add(currentGates);
                score = currentGates.getScore();
            } catch (InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
        } else {
            score = scoreAndBuildRepresentation(parse.toByteArray());
        }
        Preconditions.checkState(score <= 0.0, score);
        factory.stats.ifPresent(s -> s.addNeuralScore(score));
        factory.neuralCount.incrementAndGet();
        return score;
    }

    @Override
    public void update(final Stream<AgendaItem> incorrect,
                       final Stream<AgendaItem> correct,
                       final boolean useCrfLoss) {
        final UpdateProto.Builder builder = UpdateProto.newBuilder().setUseCrfLoss(useCrfLoss);
        incorrect.forEach(item -> builder.addIncorrect(chartIndexes.get(item.getParse())));
        correct.forEach(item -> builder.addCorrect(chartIndexes.get(item.getParse())));
        if (builder.getIncorrectCount() > 0 && builder.getCorrectCount() > 0) {
            applyUpdate(builder.build().toByteArray());
        }
    }

    public List<InputWord> getWords() {
        return words;
    }

    @Override
    public void buildAgenda(final Agenda agenda, final List<InputWord> words) {
        this.words = words;

        final SentenceProto sentence = SentenceProto.newBuilder()
                .addAllWord(() -> words.stream().map(w -> w.word).iterator())
                .setIsEval(!trainer.isPresent())
                .build();

        if (factory.keepGates) {
            try {
                initialGates.mergeFrom(initializeSentenceWithGates(sentence.toByteArray()));
            } catch (final InvalidProtocolBufferException e) {
                throw new RuntimeException(e);
            }
        } else {
            initializeSentence(sentence.toByteArray());
        }

        for (int i = 0; i < words.size(); i++) {
            final InputWord word = words.get(i);
            for (final Tagger.ScoredCategory category : tagsForWords.get(i)) {
                final SyntaxTreeNode node = new SyntaxTreeNodeLeaf(word.word, word.pos, word.ner,
                        category.getCategory(), i, PARSE_DEPS);
                final TrainableAgendaItem item = new TrainableAgendaItem(
                        node,
                        USE_ENSEMBLE ? category.getScore() : 0,
                        getOutsideUpperBound(i, i + 1),
                        i,
                        1,
                        AGENDA_DEPS,
                        isGold(node),
                        computeLoss(node));
                if (factory.useLazyAgenda) {
                    agenda.add(item);
                } else {
                    agenda.add(this.tighten(item));
                }
            }
        }
    }

    private boolean isGold(final SyntaxTreeNode node, final AgendaItem... children) {
        return Arrays.stream(children).filter(TrainableAgendaItem::isTrainable).allMatch(TrainableAgendaItem::isGold)
                && oracleSteps.filter(s -> s.contains(new IdentityWrapper<>(node, SyntaxUtil::parsesEqual, SyntaxUtil::parseHash))).isPresent();
    }

    @Override
    public AgendaItem combineNodes(final AgendaItem leftChild, final AgendaItem rightChild, final SyntaxTreeNode node) {
        final int length = leftChild.getSpanLength() + rightChild.getSpanLength();

        final TrainableAgendaItem item = new TrainableAgendaItem(
                node,
                leftChild.getInsideScore() + rightChild.getInsideScore(),
                getOutsideUpperBound(leftChild.getStartOfSpan(), leftChild.getStartOfSpan() + length),
                leftChild.getStartOfSpan(),
                length,
                AGENDA_DEPS,
                isGold(node, leftChild, rightChild),
                computeLoss(node, leftChild, rightChild));

        if (factory.useLazyAgenda) {
            return item;
        } else {
            return this.tighten(item);
        }
    }

    @Override
    public AgendaItem unary(final AgendaItem child, final SyntaxTreeNode node, final AbstractParser.UnaryRule rule) {
        final TrainableAgendaItem item = new TrainableAgendaItem(
                node,
                child.getInsideScore(),
                child.getOutsideScoreUpperbound(),
                child.getStartOfSpan(),
                child.getSpanLength(),
                AGENDA_DEPS,
                isGold(node, child),
                computeLoss(node, child));

        if (factory.useLazyAgenda) {
            return item;
        } else {
            return this.tighten(item);
        }
    }

    private double computeLoss(final SyntaxTreeNode node, final AgendaItem... children) {
        return goldInput
                .filter(i -> factory.evaluator.isPresent())
                .map(i -> factory.evaluator.get().evaluateStep(i.getGoldDependencies(), node, false, words))
                .map(stats -> factory.lossScale * stats.getErrors())
                .orElse(0.0) +
                Arrays.stream(children)
                        .filter(TrainableAgendaItem::isTrainable)
                        .mapToDouble(TrainableAgendaItem::getLoss)
                        .sum();
    }

    @Override
    public double getUpperBoundForWord(int index) {
        if (BEST_FIRST) {
            return constantUpperBound;
        } else if (USE_ENSEMBLE) {
            return tagsForWords.get(index).get(0).getScore();
        } else {
            return 0;
        }
    }

    private AgendaItem tighten(final AgendaItem item) {
        return TrainableAgendaItem.tighten(item, () -> scoreAndBuildRepresentation(item.getParse()));
    }

    @Override
    public Agenda makeAgenda() {
        final Agenda naturalAgenda = new LazyAgenda(TrainableAgendaItem::isTight, this::tighten, Comparator.naturalOrder());
        final Agenda lossAugmentedAgenda = new LazyAgenda(TrainableAgendaItem::isTight, this::tighten, TrainableAgendaItem::lossAugmentedCompare);
        final Agenda incorrectAgenda = new MultiHeadedAgenda(naturalAgenda, lossAugmentedAgenda);
        final Agenda correctAgenda = new LazyAgenda(TrainableAgendaItem::isTight, this::tighten, Comparator.naturalOrder());
        return new PredicatedAgenda(TrainableAgendaItem::isGold, correctAgenda, incorrectAgenda, Comparator.naturalOrder());
    }

    public static class TreeFactoredModelFactory extends TrainableModelFactory implements ParserListener {
        private final Optional<Tagger> tagger;
        private final Collection<Category> lexicalCategories;
        private final Optional<ParserStatistics> stats;
        private final double lossScale;
        private final boolean keepGates;
        private final Optional<DependencyEvaluator> evaluator;
        private TreeFactoredModel lastModel;
        private static boolean hasInitializedCNN = false;
        private final AtomicInteger neuralCount;
        private final int maxNeuralCount;
        private final boolean useLazyAgenda;

        private TreeFactoredModelFactory(final Optional<Tagger> tagger,
                                         final Collection<Category> lexicalCategories,
                                         final Config arguments,
                                         final boolean keepGates,
                                         final boolean useLazyAgenda,
                                         final Optional<DependencyEvaluator> evaluator,
                                         final Optional<ParserStatistics> stats) {
            this.tagger = tagger;
            this.lexicalCategories = lexicalCategories;
            this.lossScale = arguments.hasPath("loss_scale") ? arguments.getDouble("loss_scale") : 0;
            this.keepGates = keepGates;
            this.useLazyAgenda = useLazyAgenda;
            this.maxNeuralCount = arguments.getInt("max_neural_count");
            this.evaluator = evaluator;
            this.stats = stats;
            this.neuralCount = new AtomicInteger(0);
            Preconditions.checkState(hasInitializedCNN);
        }

        public TreeFactoredModelFactory(final Optional<Tagger> tagger,
                                        final Collection<Category> lexicalCategories,
                                        final Config arguments,
                                        final boolean keepGates,
                                        final boolean useLazyAgenda,
                                        final Optional<DependencyEvaluator> evaluator,
                                        final ScorerConfig scorerConfig,
                                        final Optional<TrainConfig> trainConfig,
                                        final Optional<ParserStatistics> stats) {
            this(tagger, lexicalCategories, arguments, keepGates, useLazyAgenda, evaluator, stats);
            initializeScorer(scorerConfig.toByteArray());
            trainConfig
                    .map(TrainConfig::toByteArray)
                    .ifPresent(TreeFactoredModel::initializeTrainer);
        }

        public TreeFactoredModelFactory(final Optional<Tagger> tagger,
                                        final Collection<Category> lexicalCategories,
                                        final Config arguments,
                                        final boolean keepGates,
                                        final boolean useLazyAgenda,
                                        final Optional<DependencyEvaluator> evaluator,
                                        final File checkpointPath,
                                        final Optional<TrainConfig> trainConfig,
                                        final Optional<ParserStatistics> stats) {
            this(tagger, lexicalCategories, arguments, keepGates, useLazyAgenda, evaluator, stats);
            initializeScorer(checkpointPath.getAbsolutePath());
            trainConfig
                    .map(TrainConfig::toByteArray)
                    .ifPresent(TreeFactoredModel::initializeTrainer);
        }

        public TreeFactoredModel getLastModel() {
            return lastModel;
        }

        public void saveCheckpoint(File checkpointPath) {
            TreeFactoredModel.saveCheckpoint(checkpointPath.getAbsolutePath());
        }

        @Override
        public TreeFactoredModel make(final InputToParser input) {
            lastModel = new TreeFactoredModel(
                    input.isAlreadyTagged() ?
                            input.getInputSupertags() :
                            tagger.orElseThrow(() -> new IllegalArgumentException("Input must be already tagged if no tagger is provided"))
                                    .tag(input.getInputWords()),
                    this,
                    Optional.of(input)
                            .filter(i -> trainer.isPresent())
                            .filter(i -> i instanceof GoldInputToParser)
                            .map(i -> (GoldInputToParser) i),
                    trainer);
            return lastModel;
        }

        @Override
        public Collection<Category> getLexicalCategories() {
            return lexicalCategories;
        }

        @Override
        public boolean isUsingDependencies() {
            return PARSE_DEPS;
        }

        @Override
        public boolean isUsingDynamicProgram() {
            return false;
        }

        // Should be called exactly once for every process.
        public synchronized static void initializeCNN(final RunConfig runConfig) {
            if (hasInitializedCNN) {
                log.info("CNN already initialized. Ignoring second run configuration.");
            } else {
                System.setProperty("java.library.path", "lib");
                System.loadLibrary("decoder");
                TreeFactoredModel.initializeCNN(runConfig.toByteArray());
                hasInitializedCNN = true;
            }
        }

        @Override
        public void handleNewSentence(final List<InputWord> sentence) {
            neuralCount.set(0);
        }

        @Override
        public boolean handleChartInsertion(final Agenda agenda) {
            return neuralCount.get() < maxNeuralCount;
        }

        @Override
        public void handleSearchCompletion(final List<Scored<SyntaxTreeNode>> result,
                                           final Agenda agenda,
                                           final int chartSize) {
            // Do nothing.
        }
    }
}