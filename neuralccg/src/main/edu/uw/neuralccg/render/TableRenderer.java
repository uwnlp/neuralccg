package edu.uw.neuralccg.render;

import com.hp.gagawa.java.Node;
import com.hp.gagawa.java.elements.Table;
import com.hp.gagawa.java.elements.Tbody;
import com.hp.gagawa.java.elements.Td;
import com.hp.gagawa.java.elements.Tr;
import com.typesafe.config.Config;

import edu.uw.neuralccg.TableProto;
import com.github.kentonl.pipegraph.web.renderer.IResourceRenderer;

public class TableRenderer implements IResourceRenderer<TableProto.Table> {
	@Override
	public String getKey() {
		return TableProto.Table.class.toString();
	}

	@Override
	public Node render(TableProto.Table table, Config arguments) {
		final Table htmlTable = new Table().setCSSClass("table table-hover");
		final Tbody htmlbody = new Tbody();
		htmlTable.appendChild(htmlbody);
		for (final TableProto.Row row : table.getRowList()) {
			final Tr htmlRow = new Tr();
			htmlbody.appendChild(htmlRow);
			for (final String cell : row.getCellList()) {
				htmlRow.appendChild(new Td().appendText(cell));
			}
		}
		return htmlTable;
	}
}
