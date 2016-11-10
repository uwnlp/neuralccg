package edu.uw.neuralccg.util;

import com.hp.gagawa.java.elements.Form;
import com.hp.gagawa.java.elements.Input;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

import java.util.Map.Entry;

public class WebUtil {
    public static Form formWithParameters(String action, Config parameters) {
        final Form form = new Form(action);
        for (final Entry<String, ConfigValue> entry : parameters.entrySet()) {
            form.appendChild(
                    new Input().setType("hidden").setName(entry.getKey())
                            .setValue(entry.getValue().unwrapped().toString()));
        }
        return form;
    }
}