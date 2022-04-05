package org.logstashplugins;

import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.internal.NanosecondConverter;
import com.influxdb.utils.Arguments;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class InfluxPoint {
    private final String measurement;
    private Number time;
    private WritePrecision precision = WritePrecision.NS;
    private final Map<String, String> tags = new HashMap<>();
    private final Map<String, Object> fields = new HashMap<>();
    private static final int MAX_FRACTION_DIGITS = 340;
    private static final ThreadLocal<NumberFormat> NUMBER_FORMATTER =
            ThreadLocal.withInitial(() -> {
                NumberFormat numberFormat = NumberFormat.getInstance(Locale.ENGLISH);
                numberFormat.setMaximumFractionDigits(MAX_FRACTION_DIGITS);
                numberFormat.setGroupingUsed(false);
                numberFormat.setMinimumFractionDigits(1);
                return numberFormat;
            });

    public InfluxPoint(String measurement) {
        Arguments.checkNotNull(measurement, "measurement");
        this.measurement = measurement;
    }

    public static InfluxPoint measurement(String measurement) {
        return new InfluxPoint(measurement);
    }

    public void time(Instant timestamp, WritePrecision precision) {
        this.time = NanosecondConverter.convert(timestamp, precision);
        this.precision = precision;
    }

    public void addTag(String key, String value) {
        this.tags.put(key, value);
    }

    public void addFields(Map<String, Object> fields) {
        this.fields.putAll(fields);
    }

    public String toLineProtocol() {
        StringBuilder sb = new StringBuilder();

        escapeKey(sb, measurement, false);
        appendTags(sb);
        boolean appendedFields = appendFields(sb);
        if (!appendedFields) {
            return "";
        }
        appendTime(sb, precision);

        return sb.toString();
    }

    @Nonnull
    private InfluxPoint putField(@Nonnull final String field, @Nullable final Object value) {

        Arguments.checkNonEmpty(field, "fieldName");

        fields.put(field, value);
        return this;
    }

    private void appendTags(@Nonnull final StringBuilder sb) {
        Set<Map.Entry<String, String>> entries = this.tags.entrySet();

        for (Map.Entry<String, String> tag : entries) {

            String key = tag.getKey();
            String value = tag.getValue();

            if (key.isEmpty() || value == null || value.isEmpty()) {
                continue;
            }

            sb.append(',');
            escapeKey(sb, key);
            sb.append('=');
            escapeKey(sb, value);
        }
        sb.append(' ');
    }

    private boolean appendFields(@Nonnull final StringBuilder sb) {
        boolean appended = false;
        for (Map.Entry<String, Object> field : this.fields.entrySet()) {
            Object value = field.getValue();
            if (isNotDefined(value)) {
                continue;
            }
            escapeKey(sb, field.getKey());
            sb.append('=');
            if (value.getClass().getSimpleName().contains("String")) {
                String stringValue = String.valueOf(value);
                sb.append('"');
                escapeValue(sb, stringValue);
                sb.append('"');
            } else {
                sb.append(value);
            }

            sb.append(',');

            appended = true;
        }

        // efficiently chop off the trailing comma
        int lengthMinusOne = sb.length() - 1;
        if (sb.charAt(lengthMinusOne) == ',') {
            sb.setLength(lengthMinusOne);
        }

        return appended;
    }

    private void appendTime(@Nonnull final StringBuilder sb, final WritePrecision precision) {
        if (this.time == null) {
            return;
        }

        sb.append(" ");

        if (this.precision == precision) {
            sb.append(this.time.longValue());
        } else {
            sb.append(toTimeUnit(precision).convert(time.longValue(), toTimeUnit(this.precision)));
        }
    }

    private void escapeKey(@Nonnull final StringBuilder sb, @Nonnull final String key) {
        escapeKey(sb, key, true);
    }

    private void escapeKey(@Nonnull final StringBuilder sb, @Nonnull final String key, final boolean escapeEqual) {
        for (int i = 0; i < key.length(); i++) {
            switch (key.charAt(i)) {
                case '\n':
                    sb.append("\\n");
                    continue;
                case '\r':
                    sb.append("\\r");
                    continue;
                case '\t':
                    sb.append("\\t");
                    continue;
                case ' ':
                case ',':
                    sb.append('\\');
                    break;
                case '=':
                    if (escapeEqual) {
                        sb.append('\\');
                    }
                    break;
                default:
            }

            sb.append(key.charAt(i));
        }
    }

    private void escapeValue(@Nonnull final StringBuilder sb, @Nonnull final String value) {
        for (int i = 0; i < value.length(); i++) {
            switch (value.charAt(i)) {
                case '\\':
                case '\"':
                    sb.append('\\');
                default:
                    sb.append(value.charAt(i));
            }
        }
    }

    private boolean isNotDefined(final Object value) {
        return value == null
                || (value instanceof Double && !Double.isFinite((Double) value))
                || (value instanceof Float && !Float.isFinite((Float) value));
    }

    @Nonnull
    private TimeUnit toTimeUnit(@Nonnull final WritePrecision precision) {
        switch (precision) {
            case MS:
                return TimeUnit.MILLISECONDS;
            case S:
                return TimeUnit.SECONDS;
            case US:
                return TimeUnit.MICROSECONDS;
            case NS:
                return TimeUnit.NANOSECONDS;
            default:
                throw new IllegalStateException("Unexpected value: " + precision);
        }
    }
}
