package edu.uw.neuralccg.task;

import com.google.protobuf.Any;

import com.github.kentonl.pipegraph.core.Stage;
import com.github.kentonl.pipegraph.task.ITask;
import com.hp.gagawa.java.Node;
import com.hp.gagawa.java.elements.Br;
import com.hp.gagawa.java.elements.Div;
import com.hp.gagawa.java.elements.Form;
import com.hp.gagawa.java.elements.Input;
import com.hp.gagawa.java.elements.Pre;
import com.typesafe.config.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.parser.Parser;
import edu.uw.easysrl.syntax.tagger.Tagger;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;
import edu.uw.easysrl.util.Util.Scored;
import edu.uw.neuralccg.TrainProto.RunConfig;
import edu.uw.neuralccg.model.TreeFactoredModel.TreeFactoredModelFactory;
import edu.uw.neuralccg.printer.GatedHtmlPrinter;
import edu.uw.neuralccg.printer.LatexPrinter;
import edu.uw.neuralccg.util.EasySRLUtil;
import edu.uw.neuralccg.util.GateUtil.GlobalGateVisitor;
import edu.uw.neuralccg.util.SyntaxUtil;
import edu.uw.neuralccg.util.WebUtil;

public class DemoTask implements ITask<Any> {
    public static final Logger log = LoggerFactory.getLogger(DemoTask.class);

    private TreeFactoredModelFactory modelFactory;
    private Parser parser;
    private final LatexPrinter latexPrinter = new LatexPrinter();
    private final GatedHtmlPrinter gatedPrinter = new GatedHtmlPrinter();

    @Override
    public String getKey() {
        return "demo";
    }

    @Override
    public Optional<Node> render(Config parameters) {
        if (parser == null) {
            return Optional.empty();
        }
        final Div element = new Div();
        final String sentence = parameters.hasPath("sentence")
                ? parameters.getString("sentence") : "";
        final Form form = WebUtil.formWithParameters("", parameters.withoutPath("sentence"));
        element.appendChild(form);
        form.appendChild(
                new Input().setType("text").setSize("40").setName("sentence")
                        .setCSSClass("form-control").setValue(sentence));
        form.appendChild(new Input().setType("submit").setValue("Parse!")
                .setCSSClass("btn btn-default"));

        if (!sentence.isEmpty()) {
            final InputToParser input = InputToParser.fromTokens(Arrays.asList(sentence.split(" ")));
            synchronized (parser) {
                final List<Scored<SyntaxTreeNode>> result = parser.doParsing(input);
                if (result != null) {
                    for (final Scored<SyntaxTreeNode> parse : result) {
                        SyntaxUtil
                                .subtreeStream(result.get(0).getObject())
                                .forEach(subtree -> {
                                    final GlobalGateVisitor visitor = new GlobalGateVisitor(modelFactory.getLastModel());
                                    subtree.accept(visitor);
                                    gatedPrinter.setGateVisitor(visitor);
                                    gatedPrinter.setWords(input.getInputWords());
                                    element.appendText(gatedPrinter.print(subtree, 0));
                                    element.appendChild(new Br());
                                    element.appendChild(new Br());
                                    element.appendChild(new Br());
                                });
                        element.appendChild(new Br());
                        element.appendChild(new Br());
                        element.appendChild(new Br());
                        element.appendChild(new Pre().appendText(latexPrinter.print(parse.getObject(), 0)));
                        element.appendChild(new Br());
                        element.appendChild(new Br());
                        element.appendChild(new Br());
                    }
                } else {
                    element.appendText("Parse failed.");
                }
            }
        }
        return Optional.of(element);
    }

    @Override
    public Stream<Any> run(Stage stage) {
        final File modelDir = new File(stage.getArguments().getString("model_dir"));

        final File checkpointPath = new File(stage.getArguments().getString("checkpoint_path"));

        final Collection<Category> categories;
        try {
            categories = TaggerEmbeddings.loadCategories(new File(modelDir, "categories"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final Tagger tagger = EasySRLUtil.loadTagger(stage.getArguments());

        TreeFactoredModelFactory.initializeCNN(RunConfig.newBuilder()
                .setMemory(stage.getArguments().getInt("native_memory")).build());

        synchronized (TreeFactoredModelFactory.class) {
            modelFactory = new TreeFactoredModelFactory(
                    Optional.of(tagger),
                    categories,
                    stage.getArguments(),
                    true,
                    true,
                    Optional.empty(),
                    checkpointPath,
                    Optional.empty(),
                    Optional.empty());

            parser = EasySRLUtil.parserBuilder(stage.getArguments())
                    .modelFactory(modelFactory)
                    .listeners(Collections.singletonList(modelFactory))
                    .build();

            try {
                while (true) {
                    Thread.sleep(Long.MAX_VALUE);
                }
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}