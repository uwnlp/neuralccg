package edu.uw.neuralccg.printer;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import edu.uw.easysrl.main.ParsePrinter;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;

public class LatexPrinter extends ParsePrinter {

    private static String escapeLatex(String s) {
        return s.replaceAll("\\\\", "\\\\bs ")
                .replaceAll("(#|\\$|%|&|\\_|\\{|\\})", "\\\\$0");
    }

    private static List<List<SyntaxTreeNode>> getRows(SyntaxTreeNode parse) {
        final List<List<SyntaxTreeNode>> result = new ArrayList<>();
        getRows(parse, result, 0);
        return result;
    }

    private static int getRows(SyntaxTreeNode node,
                               List<List<SyntaxTreeNode>> result, int minIndentation) {
        if (node.getDependenciesLabelledAtThisNode().size() > 0) {
            return getRows(node.getChild(0), result, minIndentation);
        }
        int maxChildLevel = 0;
        int i = minIndentation;
        for (final SyntaxTreeNode child : node.getChildren()) {
            maxChildLevel = Math.max(getRows(child, result, i), maxChildLevel);
            i = i + getWidth(child);
        }

        final int level = node.getChildren().isEmpty() ? 0 : maxChildLevel + 1;

        while (result.size() < level + 1) {
            result.add(new ArrayList<SyntaxTreeNode>());
        }
        while (result.get(level).size() < minIndentation + 1) {
            result.get(level).add(null);
        }

        result.get(level).set(minIndentation, node);
        return level;
    }

    private static int getWidth(SyntaxTreeNode node) {
        return node.getChildren().isEmpty() ? 1
                : node.getChildren().stream().mapToInt(LatexPrinter::getWidth)
                .sum();
    }

    private static String latexify(String s) {
        return s.replaceAll("\\#", "\\\\lambda ").replaceAll("\\&", "\\\\land ")
                .replaceAll("∃", "\\\\exists ");
    }

    @Override
    protected boolean outputsLogic() {
        return true;
    }

    @Override
    protected void printFailure(StringBuilder result) {
        // Do nothing.
    }

    @Override
    protected void printFileHeader(StringBuilder result) {
        // Do nothing.
    }

    @Override
    protected void printFooter(StringBuilder result) {
        // Do nothing.
    }

    @Override
    protected void printHeader(int id, StringBuilder result) {
        // Do nothing.
    }

    private static String getLineType(final SyntaxTreeNode node) {
        switch (node.getRuleType()) {
            case FA:
                return "fapply";
            case BA:
                return "bapply";
            default:
                return "uline";
        }
    }

    @Override
    protected void printParse(SyntaxTreeNode parse, int sentenceNumber,
                              StringBuilder result) {
        result.append(
                String.format("\\deriv{%d}{\n", parse.getLeaves().size()));
        result.append(parse.getLeaves().stream()
                .map(SyntaxTreeNodeLeaf::getWord).map(LatexPrinter::escapeLatex)
                .map(escapedWord -> String.format("\\mc{1}{\\mbox{%s}}",
                        escapedWord))
                .collect(Collectors.joining(" & ")) + "\\\\\n");
        for (final List<SyntaxTreeNode> row : getRows(parse)) {
            final List<String> lines = new LinkedList<>();
            final List<String> syntax = new LinkedList<>();
            final List<String> semantics = new LinkedList<>();
            int indent = 0;
            while (indent < row.size()) {
                final SyntaxTreeNode cell = row.get(indent);
                if (cell == null) {
                    lines.add("");
                    syntax.add("");
                    semantics.add("");
                    indent++;
                } else {
                    final int width = getWidth(cell);
                    lines.add(String.format("\\%s{%d}", getLineType(cell), width));
                    syntax.add(String.format("\\mc{%d}{%s}", width,
                            escapeLatex(cell.getCategory().toString())));
                    if (cell.getSemantics().isPresent()) {
                        semantics.add(String.format("\\mc{%d}{%s}", width,
                                cell.getSemantics()
                                        .map(Object::toString)
                                        .map(LatexPrinter::latexify)
                                        .get()));
                    }
                    indent += width;
                }
            }
            result.append(lines.stream().collect(Collectors.joining(" & "))
                    + "\\\\\n");
            result.append(syntax.stream().collect(Collectors.joining(" & "))
                    + "\\\\\n");
            if (parse.getSemantics().isPresent()) {
                result.append(semantics.stream().collect(Collectors.joining(" & "))
                        + "\\\\\n");
            }
        }
        result.append("}\n");
    }
}
