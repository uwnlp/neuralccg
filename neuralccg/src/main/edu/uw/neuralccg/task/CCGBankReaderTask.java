package edu.uw.neuralccg.task;

import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;

import edu.uw.easysrl.corpora.CCGBankDependencies;
import edu.uw.easysrl.corpora.CCGBankDependencies.Partition;
import edu.uw.neuralccg.SerializationProto.Serialized;
import edu.uw.neuralccg.util.SerializationUtil;
import com.github.kentonl.pipegraph.core.Stage;
import com.github.kentonl.pipegraph.task.ITask;

public class CCGBankReaderTask implements ITask<Serialized> {

	public static Stream<Serialized> parseStream(File ccgbankDir, Partition partition) {
		try {
			return CCGBankDependencies
					.loadCorpus(ccgbankDir, partition)
					.stream()
					.map(parse -> SerializationUtil.serialize(parse));
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getKey() {
		return "read-ccgbank";
	}

	@Override
	public Stream<Serialized> run(Stage stage) {
		return parseStream(
				new File(stage.getArguments().getString("ccgbank_dir")),
				Partition.valueOf(stage.getArguments().getString("partition").toUpperCase()));
	}
}
