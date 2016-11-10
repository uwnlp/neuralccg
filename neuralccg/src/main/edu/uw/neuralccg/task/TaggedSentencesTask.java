package edu.uw.neuralccg.task;

import com.github.kentonl.pipegraph.core.Stage;
import com.github.kentonl.pipegraph.task.ITask;
import com.github.kentonl.pipegraph.util.LambdaUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.stream.Stream;

import edu.uw.Taggerflow;
import edu.uw.TaggerflowProtos.TaggedSentence;
import edu.uw.TaggerflowProtos.TaggingInput;
import edu.uw.easysrl.main.InputReader.InputToParser;
import edu.uw.easysrl.syntax.tagger.TaggerflowLSTM;
import edu.uw.neuralccg.SerializationProto.Serialized;
import edu.uw.neuralccg.util.DataUtil;
import edu.uw.neuralccg.util.ProgressLogger;
import edu.uw.neuralccg.util.SerializationUtil;

public class TaggedSentencesTask implements ITask<TaggedSentence> {
    public static final Logger log = LoggerFactory.getLogger(TaggedSentencesTask.class);

    @Override
    public String getKey() {
        return "tagged-sentences";
    }

    public static Stream<TaggedSentence> taggedSentenceStream(final Taggerflow taggerflow,
                                                              final Stream<InputToParser> inputs) {
        return taggerflow.predict(TaggingInput.newBuilder().addAllSentence(() ->
                inputs.map(input -> TaggerflowLSTM.wordsToSentence(input.getInputWords())).iterator())
                .build());
    }

    @Override
    public Stream<TaggedSentence> run(final Stage stage) {
        final Taggerflow taggerflow = new Taggerflow(
                new File(new File(stage.getArguments().getString("model_dir")), "taggerflow"),
                stage.getArguments().getDouble("supertagger_beam"));
        final Stream<InputToParser> inputs = stage
                .read("inputs", Serialized.class)
                .map(SerializationUtil::<InputToParser>deserialize);
        if (stage.getArguments().hasPath("partition_size")) {
            final ProgressLogger progressLogger = new ProgressLogger(
                    stage.getArguments().getInt("partition_size"),
                    -1,
                    "Sentences tagged",
                    stage::setProgress);
            return DataUtil.lazyPartition(inputs, stage.getArguments().getInt("partition_size"))
                    .flatMap(partitionInputs -> taggedSentenceStream(taggerflow, partitionInputs))
                    .peek(LambdaUtil.toConsumer(progressLogger::maybeLog));
        } else {
            return taggedSentenceStream(taggerflow, inputs);
        }
    }
}