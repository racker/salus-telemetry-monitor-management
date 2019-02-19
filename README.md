# Dev Dependencies

The kafka and mysql containers from [salus-telemetry-bundle](https://github.com/racker/salus-telemetry-bundle#runningdeveloping-locally) must be running.

[salus-telemetry-model](https://github.com/racker/salus-telemetry-model) must be recent and have been built to generate the `Monitor_` sources under `target/classes/generated-sources/annotations/com.rackspace.salus.telemetry.model`.  If building the `-model` module via IntelliJ does not create this, you should try a `mvn clean` in that project before building.


The `dev` profile should be set to ensure the datastore properties from `application.yml` get picked up.

# Dev Testing

To see events being posted to Kafka you can run this command:
```
docker exec -it telemetry-infra_kafka_1 \
kafka-console-consumer --bootstrap-server localhost:9093 --topic telemetry.monitors.json
```

You can trigger these events to be posted by utilizing some of the API operations below.

# API Operations
Examples of a subset of the available API operations.

## Create a new monitor
```
echo '{"monitorId":"mon1", "content":"content1", "agentType":"FILEBEAT"}' | http POST 'localhost:8089/api/tenant/aaaaa/monitors'
```

## Update an existing monitor
```
echo '{"content":"content1xxxxx"}' | http PUT 'localhost:8089/api/tenant/aaaaa/monitors/mon1'
```

## Get a stream of all monitors
```
curl localhost:8089/api/monitorsAsStream
```

> **Notes**:
>
> httpie will not receive the stream the same way curl does.  Currently unsure why.


## Delete a resource
```
http DELETE 'localhost:8089/api/tenant/aaaaa/monitors/mon1'
```
