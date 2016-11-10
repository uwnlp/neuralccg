package edu.uw.neuralccg;

import com.google.common.collect.ImmutableList;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.util.Optional;

import com.github.kentonl.pipegraph.core.Pipegraph;
import com.github.kentonl.pipegraph.runner.AsynchronousPipegraphRunner;
import com.github.kentonl.pipegraph.runner.IPipegraphRunner;

public class Main {
    public static void main(String[] args) {
        final CommandLineParser parser = new DefaultParser();
        final Options options = new Options()
                .addOption(Option.builder("c").hasArg(true).desc("experiment configuration file").required().build())
                .addOption(Option.builder("g").hasArg(true).desc("goal to run").build())
                .addOption(Option.builder("p").hasArg(true).desc("port").build());
        try {
            CommandLine commandLine = parser.parse(options, args);
            final Pipegraph graph = new Pipegraph(
                    new File("results"),
                    new File(commandLine.getOptionValue("c")),
                    Optional.of(commandLine)
                            .filter(cl -> cl.hasOption("g"))
                            .map(cl -> cl.getOptionValue("g"))
                            .map(ImmutableList::of));
            final IPipegraphRunner runner = new AsynchronousPipegraphRunner();
            runner.run(graph, Optional.of(commandLine)
                    .filter(cl -> cl.hasOption("p"))
                    .map(cl -> cl.getOptionValue("p"))
                    .map(Integer::parseInt));
        } catch (final ParseException exp) {
            System.err.println(exp.getMessage());
        }
    }
}
