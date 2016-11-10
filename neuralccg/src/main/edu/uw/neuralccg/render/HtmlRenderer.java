package edu.uw.neuralccg.render;

import com.hp.gagawa.java.Node;
import com.hp.gagawa.java.elements.Div;
import com.typesafe.config.Config;

import edu.uw.neuralccg.HtmlProto;
import com.github.kentonl.pipegraph.web.renderer.IResourceRenderer;

public class HtmlRenderer implements IResourceRenderer<HtmlProto.Html> {
	@Override
	public String getKey() {
		return HtmlProto.Html.class.toString();
	}

	@Override
	public Node render(HtmlProto.Html html, Config arguments) {
		return new Div().appendText(html.getContent());
	}
}
