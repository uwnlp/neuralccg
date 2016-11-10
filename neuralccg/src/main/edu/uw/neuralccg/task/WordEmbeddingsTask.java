package edu.uw.neuralccg.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.stream.Stream;

import edu.uw.neuralccg.TrainProto.WordEmbedding;
import com.github.kentonl.pipegraph.core.Stage;
import com.github.kentonl.pipegraph.task.ITask;

public class WordEmbeddingsTask implements ITask<WordEmbedding> {
    public static final Logger log = LoggerFactory.getLogger(WordEmbeddingsTask.class);

    @Override
    public String getKey() {
        return "word-embeddings";
    }

    private static WordEmbedding getWordEmbedding(String line) {
        final String[] splits = line.split(" ");
        return WordEmbedding.newBuilder()
                .setWord(splits[0].trim())
                .addAllValue(() -> Arrays.stream(splits).skip(1).map(Float::parseFloat).iterator()).build();
    }

    @Override
    public Stream<WordEmbedding> run(Stage stage) {
        final File wordEmbeddingsFile = new File(stage.getArguments().getString("word_embeddings"));
        try {
            return Files.lines(wordEmbeddingsFile.toPath())
                    .map(WordEmbeddingsTask::getWordEmbedding);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}