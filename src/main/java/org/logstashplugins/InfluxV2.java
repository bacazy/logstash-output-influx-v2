package org.logstashplugins;

import co.elastic.logstash.api.*;
import com.influxdb.client.*;
import com.influxdb.client.domain.WritePrecision;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

// class name must match plugin name
@LogstashPlugin(name = "influx_v2")
public class InfluxV2 implements Output {
    private static final Logger logger = Logger.getLogger(InfluxV2.class.getName());

    public static final PluginConfigSpec<String> ORG = PluginConfigSpec.stringSetting("org", "", false, true);
    public static final PluginConfigSpec<String> URL = PluginConfigSpec.stringSetting("url", "", false, true);
    public static final PluginConfigSpec<String> BUCKET = PluginConfigSpec.stringSetting("bucket", "", false, true);
    public static final PluginConfigSpec<String> TOKEN = PluginConfigSpec.stringSetting("token", "", false, true);
    public static final PluginConfigSpec<Map<String, Object>> DEFAULT_TAGS = PluginConfigSpec.hashSetting("default_tags");
    public static final PluginConfigSpec<Long> BATCH_SIZE = PluginConfigSpec.numSetting("batch_size", 1000);
    public static final PluginConfigSpec<Long> FLUSH_INTERVAL = PluginConfigSpec.numSetting("flush_interval", 1000);
    public static final PluginConfigSpec<Long> JITTER_INTERVAL = PluginConfigSpec.numSetting("jitter_interval", 0);
    public static final PluginConfigSpec<Long> RETRY_INTERVAL = PluginConfigSpec.numSetting("retry_interval", 5000);
    public static final PluginConfigSpec<Long> MAX_RETRIES = PluginConfigSpec.numSetting("max_retries", 3);
    public static final PluginConfigSpec<Long> MAX_RETRY_DELAY = PluginConfigSpec.numSetting("max_retry_delay", 180000);
    public static final PluginConfigSpec<Long> EXPONENTIAL_BASE = PluginConfigSpec.numSetting("exponential_base", 5);
    public static final PluginConfigSpec<Long> BUFFER_LIMIT = PluginConfigSpec.numSetting("buffer_limit", 10000);
    public static final PluginConfigSpec<String> TIME_PRECISION = PluginConfigSpec.stringSetting("time_precision", "ns");
    public static final PluginConfigSpec<String> MEASUREMENT = PluginConfigSpec.stringSetting("measurement", "logstash");
    public static final PluginConfigSpec<List<Object>> TAGS = PluginConfigSpec.arraySetting("tags");
    public static final PluginConfigSpec<List<Object>> EXCLUDES = PluginConfigSpec.arraySetting("excludes");

    private final String id;
    private final CountDownLatch done = new CountDownLatch(1);
    private final Context context;
    private final Configuration config;
    private final InfluxDBClient client;
    private final WriteApi writer;
    private final HashSet<Object> excludes;
    private final HashSet<Object> tags;
    private volatile boolean stopped = false;

    public InfluxV2(final String id, final Configuration configuration, final Context context) {
        this.id = id;
        this.config = configuration;
        this.context = context;
        InfluxDBClientOptions options = buildOptions();
        this.client = InfluxDBClientFactory.create(options);
        WriteOptions writeOptions = buildWriteOptions();
        this.writer = client.getWriteApi(writeOptions);
        this.excludes = new HashSet<>(Objects.nonNull(config.get(EXCLUDES))?config.get(EXCLUDES):new ArrayList<>());
        this.tags = new HashSet<>(Objects.nonNull(config.get(TAGS))?config.get(TAGS):new ArrayList<>());
    }

    private WriteOptions buildWriteOptions() {
        return WriteOptions.builder()
                .batchSize(config.get(BATCH_SIZE).intValue())
                .flushInterval(config.get(FLUSH_INTERVAL).intValue())
                .jitterInterval(config.get(JITTER_INTERVAL).intValue())
                .retryInterval(config.get(RETRY_INTERVAL).intValue())
                .maxRetries(config.get(MAX_RETRIES).intValue())
                .maxRetryDelay(config.get(MAX_RETRY_DELAY).intValue())
                .exponentialBase(config.get(EXPONENTIAL_BASE).intValue())
                .bufferLimit(config.get(BUFFER_LIMIT).intValue())
                .build();
    }

    private InfluxDBClientOptions buildOptions() {
        InfluxDBClientOptions.Builder builder = InfluxDBClientOptions.builder();
        builder.bucket(config.get(BUCKET));
        builder.org(config.get(ORG));
        builder.url(config.get(URL));
        builder.authenticateToken(config.get(TOKEN).toCharArray());
        Map<String, Object> tags = config.get(DEFAULT_TAGS);
        if (Objects.nonNull(tags)) {
            for (Map.Entry<String, Object> entry: tags.entrySet()) {
                builder.addDefaultTag(entry.getKey(), entry.getValue().toString());
            }
        }
        return builder.build();
    }

    @Override
    public void output(final Collection<Event> events) {
        for (Event event : events) {
            writer.writeRecord(getWritePrecision(), convert(event).toLineProtocol());
        }
    }

    private InfluxPoint convert(Event event) {
        InfluxPoint point = InfluxPoint.measurement(getMeasurement());
        point.time(event.getEventTimestamp(), getWritePrecision());
        Map<String, Object> data = event.getData();
        Map<String, Object> fields = new HashMap<>();

        if (Objects.nonNull(config.get(DEFAULT_TAGS))) {
            for (Map.Entry<String, Object> entry: config.get(DEFAULT_TAGS).entrySet()) {
                point.addTag(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        for (Map.Entry<String, Object> entry :
                data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if ("@timestamp".equals(key) || "@version".equals(key)) {
                continue;
            }
            if (excludes.contains(key)) {
                continue;
            }
            if (tags.contains(key)) {
                point.addTag(key, String.valueOf(value));
            }
            point.addTag("version", "v2");

            fields.put(key, value);
        }

        point.addFields(fields);
        return point;
    }

    private WritePrecision getWritePrecision() {
        switch (config.get(TIME_PRECISION)) {
            case "ms":
                return WritePrecision.MS;
            case "s":
                return WritePrecision.S;
            case "us":
                return WritePrecision.US;
            default:
                return WritePrecision.NS;
        }
    }

    private String getMeasurement() {
        return config.get(MEASUREMENT);
    }

    @Override
    public void stop() {
        stopped = true;
        done.countDown();
        this.client.close();
    }

    @Override
    public void awaitStop() throws InterruptedException {
        done.await();
    }

    @Override
    public Collection<PluginConfigSpec<?>> configSchema() {
        List<PluginConfigSpec<?>> schema = new ArrayList<>();
        schema.add(ORG);
        schema.add(URL);
        schema.add(BUCKET);
        schema.add(TOKEN);
        schema.add(DEFAULT_TAGS);
        schema.add(BATCH_SIZE);
        schema.add(FLUSH_INTERVAL);
        schema.add(JITTER_INTERVAL);
        schema.add(RETRY_INTERVAL);
        schema.add(MAX_RETRIES);
        schema.add(MAX_RETRY_DELAY);
        schema.add(EXPONENTIAL_BASE);
        schema.add(BUFFER_LIMIT);
        schema.add(TIME_PRECISION);
        schema.add(MEASUREMENT);
        schema.add(TAGS);
        schema.add(EXCLUDES);
        return schema;
    }

    @Override
    public String getId() {
        return id;
    }
}
