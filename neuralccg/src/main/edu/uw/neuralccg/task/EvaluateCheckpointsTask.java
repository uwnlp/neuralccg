package edu.uw.neuralccg.task;

import com.github.kentonl.pipegraph.core.Stage;
import com.github.kentonl.pipegraph.task.ITask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.uw.easysrl.syntax.grammar.Category;
import edu.uw.easysrl.syntax.parser.ParserBuilder;
import edu.uw.easysrl.syntax.tagger.TaggerEmbeddings;
import edu.uw.neuralccg.AnalysisProto.EvaluationEvent;
import edu.uw.neuralccg.SerializationProto.Serialized;
import edu.uw.neuralccg.TrainProto.RunConfig;
import edu.uw.neuralccg.evaluation.DependencyEvaluator;
import edu.uw.neuralccg.model.OracleModel.GoldInputToParser;
import edu.uw.neuralccg.model.TreeFactoredModel.TreeFactoredModelFactory;
import edu.uw.neuralccg.util.EasySRLUtil;
import edu.uw.neuralccg.util.ProgressLogger;
import edu.uw.neuralccg.util.SerializationUtil;

public class EvaluateCheckpointsTask implements ITask<EvaluationEvent> {
    public static final Logger log = LoggerFactory.getLogger(EvaluateCheckpointsTask.class);

    final Set<File> evaluated = new HashSet<>();
    final Map<String, EvaluationEvent> bestEvents = new HashMap<>();
    final

    @Override
    public String getKey() {
        return "evaluate-checkpoints";
    }

    // Returns the checkpoint file to remove.
    private Optional<File> updateBestEvents(final EvaluationEvent event) {
        final EvaluationEvent oldBestEvent = bestEvents.get(event.getName());
        if (oldBestEvent == null) {
            bestEvents.put(event.getName(), event);
            return Optional.empty();
        } else if (event.getEvalBackoff().getF1() > oldBestEvent.getEvalBackoff().getF1()) {
            bestEvents.put(event.getName(), event);
            return Optional.of(new File(oldBestEvent.getCheckpointPath()));
        } else {
            return Optional.of(new File(event.getCheckpointPath()));
        }
    }

    private Stream<EvaluationEvent> evaluateCheckpoints(final File checkpointDir,
                                                        final List<File> toDelete,
                                                        final Function<File, EvaluationEvent> evaluate) {
        toDelete.forEach(File::delete);
        try {
            Thread.sleep(10000);
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }
        try {
              return Files.walk(checkpointDir.toPath())
                    .map(Path::toFile)
                    .filter(File::isFile)
                    .filter(checkpointPath -> !evaluated.contains(checkpointPath))
                    .filter(checkpointPath -> System.nanoTime() - checkpointPath.lastModified() > 10000)
                    .peek(checkpointPath -> log.info("Evaluating new checkpoint at {}", checkpointPath.getAbsolutePath()))
                    .peek(evaluated::add)
                    .map(evaluate)
                    .peek(event -> updateBestEvents(event).ifPresent(toDelete::add));
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Stream<EvaluationEvent> run(Stage stage) {
        final File modelDir = new File(stage.getArguments().getString("model_dir"));
        final List<Category> categories;
        try {
            categories = TaggerEmbeddings.loadCategories(new File(modelDir, "categories"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        final ParserBuilder<?> backoffParserBuilder = EasySRLUtil.backoffParserBuilder(stage.getArguments());

        final List<GoldInputToParser> devInputs =
                TrainParserTask.getTaggedInputs(stage, "dev-inputs", "dev-tags", categories)
                        .filter(input -> input.getInputWords().size() <= backoffParserBuilder.getMaxSentenceLength())
                        .limit(stage.getArguments().hasPath("dev_limit") ?
                                stage.getArguments().getLong("dev_limit") : Long.MAX_VALUE)
                        .collect(Collectors.toList());
        final DependencyEvaluator evaluator = stage.read("evaluator", Serialized.class)
                .findAny()
                .map(SerializationUtil::<DependencyEvaluator>deserialize)
                .get();

        TreeFactoredModelFactory.initializeCNN(RunConfig.newBuilder()
                .setMemory(stage.getArguments().getInt("native_memory")).build());
        final File checkpointDir = new File(stage.getArguments().getString("checkpoints_dir"));
        checkpointDir.mkdirs();
        final List<File> toDelete = new ArrayList<>();
        return Stream.generate(() -> evaluateCheckpoints(checkpointDir, toDelete, checkpointPath ->
                EvaluateParserTask.evaluateCheckpoint(
                        checkpointPath,
                        devInputs,
                        stage.getArguments(),
                        backoffParserBuilder,
                        evaluator,
                        categories,
                        Optional.of(new ProgressLogger(
                                100,
                                devInputs.size(),
                                "Analyzed sentence",
                                stage::setProgress))))).flatMap(Function.identity());
    }
}
