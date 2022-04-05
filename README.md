# Logstash Output Influx V2 Plugin

[![Travis Build Status](https://travis-ci.com/logstash-plugins/logstash-output-java_output_example.svg)](https://travis-ci.com/logstash-plugins/logstash-output-java_output_example)

This is a Java plugin for [Logstash](https://github.com/elastic/logstash).

It is fully free and fully open source. The license is Apache 2.0, meaning you are free to use it however you want.

The documentation for Logstash Java plugins is available [here](https://www.elastic.co/guide/en/logstash/6.7/contributing-java-plugin.html).

## install

in logstash home dir:

```shell
bin/logstash-plugin install --no-verify --local dir-to-plugin/logstash-output-influx_v2-1.0.1.gem
```

## configuration

```
output {
    influx_v2 {
        org => "xxxx"
        bucket => "xxxx"
        token => "xxxx"
        url => "http://127.0.0.1:8086"
        ...
    }
}
```

other configuration:

| item             | type                 | description                                              | required |
|------------------|----------------------|----------------------------------------------------------|----------|
| org              | string               | organization                                             | true     |
| bucket           | string               | bucket                                                   | true     |
| token            | string               | access token                                             | true     |
| url              | string               | the endpoint of influxdb                                 | true     |
| default_tags     | hash<string, string> | default tags to append for every points, default is null | false    |
| batch_size       | number               | batch size to send, default 1000                         | false    |
| flush_interval   | number               | flush interval. default is 1000                          | false    |
| jitter_interval  | number               | jitter interval. default is 0                            | false    |
| retry_interval   | number               | retry interval. default is 6000                          | false    |
| max_retries      | number               | max retry times. default is 3                            | false    |
| max_retry_delay  | number               | max retry delay. default is 180000                       | false    |
| exponential_base | number               | exponential base. default is 5                           | false    |
| time_precision   | number               | time precision, ns, us, ms, s, default is ns             | false    |
| measurement      | string               | measurement of point                                     | false    |
| tags             | array<string>        | column in tags to add to tag                             | false    |
| excludes         | array<string>        | column to be discard                                     | false    |


