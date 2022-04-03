package org.logstashplugins;

import co.elastic.logstash.api.Configuration;
import co.elastic.logstash.api.Context;
import co.elastic.logstash.api.Event;
import co.elastic.logstash.api.LogstashPlugin;
import co.elastic.logstash.api.Output;
import co.elastic.logstash.api.PluginConfigSpec;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

// class name must match plugin name
@LogstashPlugin(name = "influx_v2")
public class InfluxV2 implements Output {

    public static final PluginConfigSpec<String> ORG = PluginConfigSpec.stringSetting("org", "", false, true);

    private final String id;
    private final CountDownLatch done = new CountDownLatch(1);
    private final Context context;
    private final Configuration config;
    private volatile boolean stopped = false;

    // all plugins must provide a constructor that accepts id, Configuration, and Context
    public InfluxV2(final String id, final Configuration configuration, final Context context) {
        this.id = id;
        this.config = configuration;
        this.context = context;
    }

    @Override
    public void output(final Collection<Event> events) {
    }

    @Override
    public void stop() {
        stopped = true;
        done.countDown();
    }

    @Override
    public void awaitStop() throws InterruptedException {
        done.await();
    }

    @Override
    public Collection<PluginConfigSpec<?>> configSchema() {
        // should return a list of all configuration options for this plugin
        return Collections.singletonList(ORG);
    }

    @Override
    public String getId() {
        return id;
    }
}
