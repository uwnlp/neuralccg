package edu.uw.neuralccg.util;

import junit.framework.TestCase;

import org.hamcrest.Matchers;
import org.junit.Assert;

import java.io.IOException;

import edu.uw.neuralccg.render.EvaluationRenderer;

public class TestRenderer extends TestCase {
    public void testXify() throws IOException {
        Assert.assertThat(EvaluationRenderer.xify("cd_32-cg_true-og_false-do_0.3"), Matchers.equalTo("cd_32-cg_true-og_false-do_0_3_x"));
    }

    public void testYify() throws IOException {
        Assert.assertThat(EvaluationRenderer.yify("cd_32-cg_true-og_false-do_0.3"), Matchers.equalTo("cd_32-cg_true-og_false-do_0_3"));
    }
}