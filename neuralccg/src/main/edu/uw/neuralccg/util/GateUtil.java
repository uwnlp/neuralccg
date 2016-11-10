package edu.uw.neuralccg.util;

import com.google.common.base.Preconditions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeBinary;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLabelling;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeUnary;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeVisitor;
import edu.uw.neuralccg.TrainProto.GatesProto;
import edu.uw.neuralccg.TrainProto.InitialGatesProto;
import edu.uw.neuralccg.model.TreeFactoredModel;
import com.github.kentonl.pipegraph.util.map.DefaultHashMap;
import neuralccg.Tensor.TensorProto;

public class GateUtil {
    private GateUtil() {
    }

    public static class GlobalGateVisitor extends AbstractGateVisitor {

        public GlobalGateVisitor(final TreeFactoredModel model) {
            super(model);
        }

        @Override
        protected double getGateSummary() {
            return IntStream.range(0, gateStack.peek().getValueCount())
                    .mapToDouble(i -> gateStack
                            .stream()
                            .peek(t -> Preconditions.checkState(t.getValueCount() == 64))
                            .map(t -> t.getValue(i))
                            .reduce(1.0f, (x, y) -> x * y))
                    .average().getAsDouble();
        }
    }

    public abstract static class AbstractGateVisitor implements SyntaxTreeNodeVisitor {
        protected final Map<SyntaxTreeNode, Integer> chartIndexes;
        protected final InitialGatesProto initialGates;
        protected final List<GatesProto> gates;
        protected final Deque<TensorProto> gateStack;
        protected final Map<SyntaxTreeNode, Double> gateMap;
        protected final Map<SyntaxTreeNodeLeaf, List<Double>> forwardGateMap;
        protected final Map<SyntaxTreeNodeLeaf, List<Double>> backwardGateMap;

        public AbstractGateVisitor(final TreeFactoredModel model) {
            this.chartIndexes = model.getChartIndexes();
            this.initialGates = model.getInitialGates();
            this.gates = model.getGates();
            this.gateStack = new ArrayDeque<>();
            this.gateMap = new HashMap<>();
            this.forwardGateMap = new DefaultHashMap<>(ArrayList::new);
            this.backwardGateMap = new DefaultHashMap<>(ArrayList::new);
        }

        protected abstract double getGateSummary();

        @Override
        public void visit(SyntaxTreeNodeBinary node) {
            final GatesProto currentGates = gates.get(chartIndexes.get(node));

            gateStack.push(currentGates.getInputGate());
            gateMap.put(node, getGateSummary());
            gateStack.pop();

            gateStack.push(currentGates.getLeftForgetGate());
            node.getLeftChild().accept(this);
            gateStack.pop();

            gateStack.push(currentGates.getRightForgetGate());
            node.getRightChild().accept(this);
            gateStack.pop();
        }

        @Override
        public void visit(SyntaxTreeNodeLeaf node) {
            final GatesProto currentGates = gates.get(chartIndexes.get(node));

            gateStack.push(currentGates.getInputGate());
            gateMap.put(node, getGateSummary());
            gateStack.pop();

            gateStack.push(currentGates.getLeftForgetGate());
            visitLeft(node, node.getStartIndex());
            gateStack.pop();

            gateStack.push(currentGates.getRightForgetGate());
            visitRight(node, node.getStartIndex());
            gateStack.pop();
        }

        private void visitLeft(SyntaxTreeNodeLeaf node, int index) {
            if (index >= 0) {
                final GatesProto currentGate = initialGates.getForwardGates(index);

                gateStack.push(currentGate.getInputGate());
                forwardGateMap.get(node).add(getGateSummary());
                gateStack.pop();

                gateStack.push(currentGate.getLeftForgetGate());
                visitLeft(node, index - 1);
                gateStack.pop();
            }
        }

        private void visitRight(SyntaxTreeNodeLeaf node, int index) {
            if (index < initialGates.getBackwardGatesCount()) {
                final GatesProto currentGate = initialGates.getBackwardGates(index);

                gateStack.push(currentGate.getInputGate());
                backwardGateMap.get(node).add(getGateSummary());
                gateStack.pop();

                gateStack.push(currentGate.getLeftForgetGate());
                visitRight(node, index + 1);
                gateStack.pop();
            }
        }

        @Override
        public void visit(SyntaxTreeNodeUnary node) {
            final GatesProto currentGates = gates.get(chartIndexes.get(node));

            gateStack.push(currentGates.getInputGate());
            gateMap.put(node, getGateSummary());
            gateStack.pop();

            gateStack.push(currentGates.getRightForgetGate());
            node.getChild().accept(this);
            gateStack.pop();
        }

        @Override
        public void visit(SyntaxTreeNodeLabelling syntaxTreeNodeLabelling) {
            throw new UnsupportedOperationException();
        }


        public Map<SyntaxTreeNode, Double> getGateMap() {
            return gateMap;
        }

        public Map<SyntaxTreeNodeLeaf, List<Double>> getForwardGateMap() {
            return forwardGateMap;
        }

        public Map<SyntaxTreeNodeLeaf, List<Double>> getBackwardGateMap() {
            return backwardGateMap;
        }
    }
}
