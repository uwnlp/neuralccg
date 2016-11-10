package edu.uw.neuralccg.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.stream.Stream;

import edu.uw.easysrl.corpora.CCGBankDependencies.DependencyParse;
import edu.uw.neuralccg.SerializationProto.Serialized;
import edu.uw.neuralccg.evaluation.DependencyEvaluator;
import edu.uw.neuralccg.util.SerializationUtil;
import com.github.kentonl.pipegraph.core.Stage;
import com.github.kentonl.pipegraph.task.ITask;

public class DependencyEvaluatorTask implements ITask<Serialized> {
    public static final Logger log = LoggerFactory.getLogger(DependencyEvaluatorTask.class);

    @Override
    public String getKey() {
        return "dependency-evaluator";
    }

    @Override
    public Stream<Serialized> run(Stage stage) {
        final File modelDir = new File(
                stage.getArguments().getString("model_dir"));
        final Stream<DependencyParse> trainCorpus = stage.read("train", Serialized.class)
                .map(SerializationUtil::deserialize);
        final DependencyEvaluator evaluator = new DependencyEvaluator(modelDir, trainCorpus);
        return Stream.of(SerializationUtil.serialize(evaluator));
    }
}