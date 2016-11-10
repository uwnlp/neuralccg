package edu.uw.neuralccg.printer;

import com.hp.gagawa.java.Node;
import com.hp.gagawa.java.elements.Br;
import com.hp.gagawa.java.elements.Hr;
import com.hp.gagawa.java.elements.P;
import com.hp.gagawa.java.elements.Span;
import com.hp.gagawa.java.elements.Table;
import com.hp.gagawa.java.elements.Td;
import com.hp.gagawa.java.elements.Tr;

import org.apache.commons.lang3.StringEscapeUtils;

import java.util.ArrayList;
import java.util.List;

import edu.uw.easysrl.main.InputReader.InputWord;
import edu.uw.easysrl.main.ParsePrinter;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode;
import edu.uw.easysrl.syntax.grammar.SyntaxTreeNode.SyntaxTreeNodeLeaf;
import edu.uw.neuralccg.util.GateUtil.AbstractGateVisitor;

public class GatedHtmlPrinter extends ParsePrinter {
    private AbstractGateVisitor gateVisitor;
    private List<InputWord> words;

    public void setWords(final List<InputWord> words) {
        this.words = words;
    }

    public void setGateVisitor(final AbstractGateVisitor gateVisitor) {
        this.gateVisitor = gateVisitor;
    }

    @Override
    protected void printFailure(final StringBuilder result) {
        result.append(new P().appendText("Parse failed!").write());
    }

    @Override
    protected void printHeader(final int id, final StringBuilder result) {
        // Do nothing.
    }

    @Override
    protected void printFooter(final StringBuilder result) {
        // Do nothing.
    }

    private List<List<SyntaxTreeNode>> getRows(SyntaxTreeNode parse) {
        final List<List<SyntaxTreeNode>> rows = new ArrayList<>();
        getRows(parse, rows, 0);
        return rows;
    }

    private int getRows(final SyntaxTreeNode node, final List<List<SyntaxTreeNode>> rows,
                        final int minIndentation) {
        int maxChildLevel = 0;
        int i = minIndentation;
        for (final SyntaxTreeNode child : node.getChildren()) {
            maxChildLevel = Math.max(getRows(child, rows, i), maxChildLevel);
            i = i + getWidth(child);
        }

        int level;
        if (node.getChildren().size() > 0) {
            level = maxChildLevel + 1;
        } else {
            level = 0;
        }

        while (rows.size() < level + 1) {
            rows.add(new ArrayList<>());
        }
        while (rows.get(level).size() < minIndentation + 1) {
            rows.get(level).add(null);
        }

        rows.get(level).set(minIndentation, node);
        return level;
    }

    private int getWidth(SyntaxTreeNode node) {
        if (node.getChildren().size() == 0) {
            return 1;
        } else {
            return node.getChildren().stream().mapToInt(this::getWidth).sum();
        }
    }

    private static Node makePopover(String cssClass, Node content) {
        final Span button = new Span().setCSSClass(cssClass);
        button.setStyle("padding: 5px;");
        button.setAttribute("data-html", "true");
        button.setAttribute("data-toggle", "popover");
        button.setAttribute("data-placement", "top");
        button.setAttribute("data-trigger", "focus");
        button.setAttribute("tabindex", "0");
        button.setAttribute("data-content", StringEscapeUtils.escapeHtml4(content.write()));
        return button;
    }

    private Span makeGatedWord(InputWord word, double gate) {
        return new Span()
                .setStyle("background-color:" + gateToHex(gate))
                .appendText(word.word);
    }

    private Td makeTokenCell(SyntaxTreeNodeLeaf node) {
        final Td cell = makeEmptyCell();
        if (gateVisitor != null) {
            final Span forwardSpan = new Span();
            final List<Double> forwardGates = gateVisitor.getForwardGateMap().get(node);
            for (int i = forwardGates.size() - 1; i >= 0; i--) {
                forwardSpan.appendChild(makeGatedWord(words.get(node.getStartIndex() - i), forwardGates.get(i)));
                forwardSpan.appendText("&nbsp");
            }

            final Span backwardSpan = new Span();
            final List<Double> backwardGates = gateVisitor.getBackwardGateMap().get(node);
            for (int i = 0; i < backwardGates.size(); i++) {
                backwardSpan.appendChild(makeGatedWord(words.get(node.getStartIndex() + i), backwardGates.get(i)));
                backwardSpan.appendText("&nbsp");
            }

            cell.appendChild(makePopover("glyphicon glyphicon-arrow-right", forwardSpan));
            cell.appendText(node.getWord());
            cell.appendChild(makePopover("glyphicon glyphicon-arrow-left", backwardSpan));
        } else {
            cell.appendText(node.getWord());
        }
        return cell;
    }

    private Td makeEmptyCell() {
        return new Td().setStyle("padding-left:10px;padding-right:10px;text-align:center;");
    }

    private Td makeLineCell(int width) {
        return makeEmptyCell().setColspan(Integer.toString(width)).appendChild(new Hr());
    }

    private static String gateToHex(double gate) {
        int otherValue = (int) Math.round((1.0 - gate) * 255);
        return rgbToHex(255, otherValue, otherValue);
    }

    private static String rgbToHex(int r, int g, int b) {
        return String.format("#%02x%02x%02x", r, g, b);
    }

    private static String renderStep(SyntaxTreeNode node) {
        final String renderedCategory = node.getCategory().toString()
                .replace("[", "<sub>")
                .replace("]", "</sub>");
        return renderedCategory;
    }

    private Td makeCategoryCell(SyntaxTreeNode node, double gate, int width) {
        return makeEmptyCell()
                .appendText(renderStep(node))
                .setColspan(Integer.toString(width))
                .setAlign("center")
                .setBgcolor(gateToHex(gate));
    }

    @Override
    protected void printParse(final SyntaxTreeNode parse, final int sentenceNumber, final StringBuilder result) {
        final Table table = new Table();
        final Tr leafRow = new Tr();
        table.appendChild(leafRow);
        for (final SyntaxTreeNodeLeaf leaf : parse.getLeaves()) {
            leafRow.appendChild(makeTokenCell(leaf));
        }

        for (final List<SyntaxTreeNode> row : getRows(parse)) {
            final Tr lineRow = new Tr();
            final Tr categoryRow = new Tr();
            table.appendChild(lineRow);
            table.appendChild(categoryRow);
            int indent = 0;
            while (indent < row.size()) {
                final SyntaxTreeNode cell = row.get(indent);
                if (cell == null) {
                    lineRow.appendChild(makeEmptyCell());
                    categoryRow.appendChild(makeEmptyCell());
                    indent = indent + 1;
                } else {
                    double cellGate = gateVisitor != null ? gateVisitor.getGateMap().get(cell) : 0.0;
                    final int width = getWidth(cell);
                    lineRow.appendChild(makeLineCell(width));
                    categoryRow.appendChild(makeCategoryCell(cell, cellGate, width));
                    indent = indent + width;
                }
            }
        }
        result.append(table.write());
        result.append(new Br().write());
        result.append(new Br().write());
        result.append(new Br().write());
    }

    @Override
    protected void printFileHeader(final StringBuilder result) {
        // Do nothing.
    }

    @Override
    protected boolean outputsLogic() {
        return false;
    }

    @Override
    protected boolean outputsDependencies() {
        return false;
    }
}