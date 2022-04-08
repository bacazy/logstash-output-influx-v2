package org.logstashplugins;

import co.elastic.logstash.api.Configuration;
import co.elastic.logstash.api.Event;
import org.junit.Assert;
import org.junit.Test;
import org.logstash.plugins.ConfigurationImpl;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class InfluxV2Test {

    @Test
    public void testJavaOutputExample() {
        Map<String, Object> configValues = new HashMap<>();
        configValues.put(InfluxV2.ORG.name(), "bacazy");
        configValues.put(InfluxV2.BUCKET.name(), "test");
        configValues.put(InfluxV2.TOKEN.name(), "_B1R-KWhTgfd_QYDiiO1uSmmr9utit0d4vR47YXfI4vpUa1wGzKC-doLitjOBfnQWuor4NinAhSP2NYocWIwGA==");
        configValues.put(InfluxV2.URL.name(), "http://localhost:8086");
        Configuration config = new ConfigurationImpl(configValues);
        InfluxV2 output = new InfluxV2("test-id", config, null);

        int eventCount = 50000;
        Collection<Event> events = new ArrayList<>();
        for (int k = 0; k < eventCount; k++) {
            Event e = new org.logstash.Event();
            e.setField("tag_index", "tag_value_" + k);
            e.setField("f_index", k);
            e.setField("f_empty", "");
            e.setField("f_null", null);
            events.add(e);
        }

        output.output(events);
    }
}
