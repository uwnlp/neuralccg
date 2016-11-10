package edu.uw.neuralccg.util;

import com.google.common.collect.ListMultimap;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import edu.uw.easysrl.dependencies.ResolvedDependency;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator;
import edu.uw.easysrl.syntax.grammar.Combinator.RuleProduction;
import edu.uw.easysrl.syntax.grammar.SeenRules;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.AbstractParser;
import edu.uw.easysrl.syntax.parser.AbstractParser.UnaryRule;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;
import edu.uw.neuralccg.SyntaxProto.CategoryProto;
import edu.uw.neuralccg.SyntaxProto.ParseProto;
import edu.uw.neuralccg.SyntaxProto.RuleTypeProto;
import edu.uw.neuralccg.SyntaxProto.SlashProto;
import com.github.kentonl.pipegraph.util.CollectionUtil;

public class SyntaxUtil {
    private SyntaxUtil() {
    }

    public static Stream<SyntaxTreeNode> subtreeStream(final SyntaxTreeNode node) {
        return Stream.concat(Stream.of(node), node.getChildren().stream().flatMap(SyntaxUtil::subtreeStream));
    }

    public static boolean isDependencyInSpan(final ResolvedDependency dependency, final SyntaxTreeNode node) {
        return isIndexInSpan(dependency.getArgument(), node) && isIndexInSpan(dependency.getHead(), node);
    }

    public static boolean isIndexInSpan(final int i, final SyntaxTreeNode node) {
        return i >= node.getStartIndex() && i < node.getEndIndex();
    }

    public static boolean isDependencyAtStep(final ResolvedDependency dependency, final SyntaxTreeNode node) {
        return node.getChildren().size() == 2
                && (isDependencyBetweenSpans(dependency, node.getChild(0), node.getChild(1))
                || isDependencyBetweenSpans(dependency, node.getChild(1), node.getChild(0)));
    }

    public static boolean isDependencyBetweenSpans(final ResolvedDependency dependency, final SyntaxTreeNode head, final SyntaxTreeNode argument) {
        return isIndexInSpan(dependency.getHead(), head) && isIndexInSpan(dependency.getArgument(), argument);
    }

    public static boolean parsesEqual(final SyntaxTreeNode parse1, final SyntaxTreeNode parse2) {
        return parse1.getStartIndex() == parse2.getStartIndex()
                && parse1.getEndIndex() == parse2.getEndIndex()
                && parse1.getCategory() == parse2.getCategory()
                && parse1.getRuleType().ordinal() == parse2.getRuleType().ordinal()
                && parse1.getChildren().size() == parse2.getChildren().size()
                && CollectionUtil.zip(parse1.getChildren().stream(), parse2.getChildren().stream(), SyntaxUtil::parsesEqual).allMatch(Boolean::booleanValue);
    }

    public static Set<Category> getPossibleCategories(final File modelDir) {
        try {
            final File lexicalCategoryFile = new File(modelDir, "categories");
            final File binaryRulesFile = new File(modelDir, "binaryRules");
            final File unaryRulesFile = new File(modelDir, "unaryRules");
            final Collection<Category> lexicalCategories = TaggerEmbeddings.loadCategories(lexicalCategoryFile);
            final List<Combinator> combinators = new ArrayList<>(Combinator.STANDARD_COMBINATORS);
            if (binaryRulesFile.exists()) {
                combinators.addAll(Combinator.loadSpecialCombinators(binaryRulesFile));
            }
            final ListMultimap<Category, UnaryRule> unaryRules = AbstractParser.loadUnaryRules(unaryRulesFile);
            final SeenRules seenRules = new SeenRules(new File(modelDir, "seenRules"), lexicalCategories);
            return getPossibleCategories(lexicalCategories, combinators, unaryRules, Optional.of(seenRules));
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Set<Category> getPossibleCategories(final Collection<Category> lexicalCategories,
                                                      final Collection<Combinator> combinators,
                                                      final ListMultimap<Category, UnaryRule> unaryRules,
                                                      final Optional<SeenRules> seenRules) {
        final Set<Category> agenda = new HashSet<>(lexicalCategories);
        final Set<Category> explored = new HashSet<>();
        while (!agenda.isEmpty()) {
            final Iterator<Category> iterator = agenda.iterator();
            final Category current = iterator.next();
            iterator.remove();
            if (explored.add(current)) {
                Stream.concat(
                        unaryRules.get(current).stream()
                                .map(UnaryRule::getCategory),
                        explored.stream()
                                .flatMap(other -> Stream.concat(
                                        seenRules
                                                .filter(sr -> !sr.isSeen(other, current))
                                                .map(sr -> Collections.<RuleProduction>emptyList())
                                                .orElseGet(() -> Combinator.getRules(other, current, combinators))
                                                .stream(),
                                        seenRules
                                                .filter(sr -> !sr.isSeen(current, other))
                                                .map(sr -> Collections.<RuleProduction>emptyList())
                                                .orElseGet(() -> Combinator.getRules(current, other, combinators))
                                                .stream())
                                        .map(RuleProduction::getCategory)))
                        .forEach(agenda::add);
            }
        }
        return explored;
    }

    public static int parseHash(final SyntaxTreeNode parse) {
        int stepHash = Objects.hash(parse.getStartIndex(), parse.getEndIndex(), parse.getCategory(), parse.getRuleType().ordinal());
        int childrenHash = Arrays.hashCode(parse.getChildren().stream().mapToInt(SyntaxUtil::parseHash).toArray());
        return Objects.hash(stepHash, childrenHash);
    }

    public static CategoryProto.Builder toProto(Category category) {
        if (category.getNumberOfArguments() > 0) {
            return CategoryProto.newBuilder()
                    .setSlash(SlashProto.valueOf(category.getSlash().name()))
                    .setLeft(toProto(category.getLeft()))
                    .setRight(toProto(category.getRight()));
        } else {
            return CategoryProto.newBuilder()
                    .setAtomic(category.toString());
        }
    }

    public static Stream<Category> atomicStream(Category category) {
        if (category.getNumberOfArguments() > 0) {
            return Stream.concat(atomicStream(category.getLeft()), atomicStream(category.getRight()));
        } else {
            return Stream.of(category);
        }
    }

    public static ParseProto.Builder toProto(SyntaxTreeNode node, Map<SyntaxTreeNode, Integer> chartIndexes) {
        final ParseProto.Builder parseBuilder = ParseProto.newBuilder();
        parseBuilder.setCategory(toProto(node.getCategory()));
        parseBuilder.setRuleType(RuleTypeProto.valueOf(node.getRuleType().toString()));
        parseBuilder.addAllChild(() -> node.getChildren()
                .stream()
                .map(chartIndexes::get)
                .iterator());
        parseBuilder.setStart(node.getStartIndex());
        parseBuilder.setEnd(node.getEndIndex() - 1);
        return parseBuilder;
    }
}
