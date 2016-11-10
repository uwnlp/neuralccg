package edu.uw.neuralccg.util;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;

import junit.framework.TestCase;

import org.hamcrest.Matchers;
import org.junit.Assert;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.Combinator;
import edu.uw.easysrl.syntax.parser.AbstractParser.UnaryRule;

public class TestSyntaxUtil extends TestCase {
    public void testPossibleCategories() throws IOException {
        final List<Category> lexicalCategories = ImmutableList.of(Category.N, Category.valueOf("S/NP"));
        final Collection<Combinator> combinators = Combinator.STANDARD_COMBINATORS;
        final ListMultimap<Category, UnaryRule> unaryRules = ArrayListMultimap.create();
        unaryRules.put(Category.N, new UnaryRule(0, "N", "NP", null));
        final Set<Category> possibleCategories = SyntaxUtil.getPossibleCategories(lexicalCategories, combinators, unaryRules, Optional.empty());
        Assert.assertThat(possibleCategories, Matchers.equalTo(Sets.newHashSet(Category.N, Category.NP, Category.S, Category.valueOf("S/NP"))));
    }
}