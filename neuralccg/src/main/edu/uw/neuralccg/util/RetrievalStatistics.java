package edu.uw.neuralccg.util;

import com.google.common.collect.Sets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.uw.neuralccg.TableProto.Row;
import edu.uw.neuralccg.TableProto.Table;

public class RetrievalStatistics {
	public static final Logger	log	= LoggerFactory.getLogger(RetrievalStatistics.class);

	private final AtomicInteger	correct;
	private final AtomicInteger	gold;
	private final AtomicInteger	predicted;

	public RetrievalStatistics() {
		correct = new AtomicInteger(0);
		gold = new AtomicInteger(0);
		predicted = new AtomicInteger(0);
	}

	private static double maybeDivide(double x, double y) {
		return y == 0 ? 0 : x / y;
	}

	public Table.Builder addToTable(Table.Builder builder, String prefix) {
		builder.addRow(Row.newBuilder().addCell(prefix).addCell("Correct")
				.addCell(String.format("%d", correct.get())));
		builder.addRow(Row.newBuilder().addCell(prefix).addCell("Gold")
				.addCell(String.format("%d", gold.get())));
		builder.addRow(Row.newBuilder().addCell(prefix).addCell("Predicted")
				.addCell(String.format("%d", predicted.get())));
		builder.addRow(Row.newBuilder().addCell(prefix).addCell("Precision")
				.addCell(String.format("%.2f%%", 100 * getPrecision())));
		builder.addRow(Row.newBuilder().addCell(prefix).addCell("Recall")
				.addCell(String.format("%.2f%%", 100 * getRecall())));
		builder.addRow(Row.newBuilder().addCell(prefix).addCell("F1")
				.addCell(String.format("%.2f%%", 100 * getF1())));
		return builder;
	}

	public int getCorrect() {
		return correct.get();
	}

	public int getErrors() {
        return getPredicted() - getCorrect() + getGold() - getCorrect();
    }

	public double getF1() {
		final double precision = getPrecision();
		final double recall = getRecall();
		return maybeDivide(2 * precision * recall, precision + recall);
	}
    
	public int getGold() {
		return gold.get();
	}

	public double getPrecision() {
		return maybeDivide(getCorrect(), getPredicted());
	}

	public int getPredicted() {
		return predicted.get();
	}

	public double getRecall() {
		return maybeDivide(getCorrect(), getGold());
	}

	public void log() {
		log.info("Recall: {}%", 100 * getRecall());
		log.info("Precision: {}%", 100 * getPrecision());
		log.info("F1: {}%", 100 * getF1());
	}

	public void merge(RetrievalStatistics other) {
		correct.addAndGet(other.correct.get());
		gold.addAndGet(other.gold.get());
		predicted.addAndGet(other.predicted.get());
	}

	public <T> RetrievalStatistics update(Stream<T> goldStream, Stream<T> predictedStream) {
		final Set<T> goldSet = goldStream.collect(Collectors.toSet());
		final Set<T> predictedSet = predictedStream.collect(Collectors.toSet());
		final Set<T> correctSet = Sets.intersection(predictedSet, goldSet);
		gold.addAndGet(goldSet.size());
		predicted.addAndGet(predictedSet.size());
		correct.addAndGet(correctSet.size());
		return this;
	}
}
