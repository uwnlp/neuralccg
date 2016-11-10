package edu.uw.neuralccg.evaluation;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Multiset.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.uw.easysrl.corpora.CCGBankDependencies.DependencyParse;
import edu.uw.easysrl.dependencies.DependencyGenerator;
import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.dependencies.UnlabelledDependency;
import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.syntax.evaluation.CCGBankEvaluation;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.neuralccg.util.RetrievalStatistics;
import edu.uw.neuralccg.util.SyntaxUtil;

public class DependencyEvaluator implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final Logger log = LoggerFactory.getLogger(DependencyEvaluator.class);

    private final DependencyGenerator dependencyGenerator;
    private final Set<String> validDependencies;

    public DependencyEvaluator(final File modelDir,
                               final Stream<DependencyParse> trainSentences) {
        try {
            dependencyGenerator = new DependencyGenerator(modelDir);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

        log.info("Counting valid dependencies...");
        final ConcurrentHashMultiset<String> dependencyCounts = ConcurrentHashMultiset.create();
        trainSentences
                .parallel()
                .map(DependencyParse::getDependencies)
                .map(CCGBankEvaluation::asResolvedDependencies)
                .flatMap(Set::stream)
                .forEach(dep -> dependencyCounts.add(
                        dep.getCategory().toString() + dep.getArgNumber()));
        validDependencies = dependencyCounts.entrySet().stream()
                .filter(entry -> entry.getCount() >= 10)
                .map(Entry::getElement)
                .collect(Collectors.toSet());
        log.info("{} valid dependencies found", validDependencies.size());
    }

    public RetrievalStatistics evaluate(final Set<ResolvedDependency> goldDependencies,
                                        final SyntaxTreeNode predictedParse) {
        final RetrievalStatistics stats = new RetrievalStatistics();
        evaluate(goldDependencies, predictedParse, stats);
        return stats;
    }

    public RetrievalStatistics evaluateStep(final Set<ResolvedDependency> goldDependencies,
                                            final SyntaxTreeNode predictedParse,
                                            final boolean generateDependencies,
                                            final List<InputWord> leaves) {
        final RetrievalStatistics stats = new RetrievalStatistics();
        stats.update(goldDependencies.stream()
                        .filter(dep -> SyntaxUtil.isDependencyAtStep(dep, predictedParse)),
                predictedDependencyStream(predictedParse, generateDependencies, leaves, true));
        return stats;
    }

    public void evaluate(final Set<ResolvedDependency> goldDependencies,
                         final SyntaxTreeNode predictedParse,
                         final RetrievalStatistics stats) {
        stats.update(goldDependencies.stream(),
                predictedDependencyStream(predictedParse));
    }

    public Stream<ResolvedDependency> predictedDependencyStream(final SyntaxTreeNode parse,
                                                                final boolean generateDependencies,
                                                                final List<InputWord> leaves,
                                                                final boolean local) {
        if (parse == null) {
            return Stream.empty();
        }
        final Set<UnlabelledDependency> unlabeledDependencies = new HashSet<>();
        if (generateDependencies) {
            dependencyGenerator.generateDependencies(parse, unlabeledDependencies);
        } else {
            if (local) {
                unlabeledDependencies.addAll(parse.getResolvedUnlabelledDependencies());
            } else {
                SyntaxUtil.subtreeStream(parse).forEach(subtree -> unlabeledDependencies.addAll(subtree.getResolvedUnlabelledDependencies()));
            }
        }
        return CCGBankEvaluation
                .convertDeps(leaves, unlabeledDependencies)
                .stream()
                .filter(x -> x.getHead() != x.getArgument())
                .filter(x -> validDependencies.contains(x.getCategory().toString() + x.getArgNumber()));
    }

    public Stream<ResolvedDependency> predictedDependencyStream(final SyntaxTreeNode parse,
                                                                final boolean generateDependencies,
                                                                final List<InputWord> leaves) {
        return predictedDependencyStream(parse, generateDependencies, leaves, false);
    }

    public Stream<ResolvedDependency> predictedDependencyStream(final SyntaxTreeNode parse,
                                                                boolean generateDependencies) {
        if (parse == null) {
            return Stream.empty();
        }
        return predictedDependencyStream(
                parse,
                generateDependencies,
                InputWord.fromLeaves(parse.getLeaves()));
    }

    public Stream<ResolvedDependency> predictedDependencyStream(final SyntaxTreeNode parse) {
        return predictedDependencyStream(parse, true);
    }
}
