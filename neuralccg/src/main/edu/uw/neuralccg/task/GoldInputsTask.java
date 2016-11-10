package edu.uw.neuralccg.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Stream;

import edu.uw.easysrl.corpora.CCGBankDependencies.DependencyParse;
import edu.uw.neuralccg.SerializationProto.Serialized;
import edu.uw.neuralccg.model.OracleModel.GoldInputToParser;
import edu.uw.neuralccg.util.SerializationUtil;
import com.github.kentonl.pipegraph.core.Stage;
import com.github.kentonl.pipegraph.task.ITask;

public class GoldInputsTask implements ITask<Serialized> {
    public static final Logger log = LoggerFactory.getLogger(GoldInputsTask.class);

    @Override
    public String getKey() {
        return "gold-inputs";
    }

    @Override
    public Stream<Serialized> run(Stage stage) {
        return stage.read("gold", Serialized.class)
                .map(SerializationUtil::<DependencyParse>deserialize)
                .map(parse -> new GoldInputToParser(parse, null))
                .map(SerializationUtil::serialize);
    }
}