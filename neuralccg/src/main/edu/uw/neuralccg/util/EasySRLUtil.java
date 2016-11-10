package edu.uw.neuralccg.util;

import com.typesafe.config.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import edu.uw.easysrl.syntax.model.SupertagFactoredModel.SupertagFactoredModelFactory;
import edu.uw.easysrl.syntax.parser.ParserAStar;
import edu.uw.easysrl.syntax.parser.ParserBuilder;
import edu.uw.easysrl.syntax.tagger.Tagger;
import edu.uw.neuralccg.grammar.NormalFormCustom;

public class EasySRLUtil {
    public static final Logger log = LoggerFactory.getLogger(EasySRLUtil.class);

    private EasySRLUtil() {
    }

    public static Tagger loadTagger(final Config arguments) {
        try {
            return Tagger.make(
                    new File(arguments.getString("model_dir")),
                    arguments.getDouble("supertagger_beam"),
                    50,
                    null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T extends ParserBuilder<T>> T parserBuilder(final T builder, final Config arguments) {
        return builder.maximumSentenceLength(arguments.getInt("max_sentence_length"))
                .nBest(arguments.getInt("n_best"))
                .maxChartSize(arguments.getInt("max_chart_size"))
                .maxAgendaSize(arguments.getInt("max_agenda_size"))
                .allowUnseenRules(true)
                .normalForm(new NormalFormCustom());
    }

    public static ParserAStar.Builder parserBuilder(final Config arguments) {
        return parserBuilder(new ParserAStar.Builder(new File(arguments.getString("model_dir"))), arguments);
    }

    public static ParserBuilder backoffParserBuilder(final Config arguments) {
        final ParserBuilder<?> builder = new ParserAStar.Builder(new File(arguments.getString("model_dir")));
        return builder
                .modelFactory(new SupertagFactoredModelFactory(null, builder.getLexicalCategories(), false))
                .maximumSentenceLength(arguments.getInt("max_sentence_length"))
                .maxChartSize(arguments.getInt("max_chart_size"))
                .maxAgendaSize(arguments.getInt("max_agenda_size"));
    }
}
