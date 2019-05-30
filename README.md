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

## Create a new local monitor

HTTP POST to http://localhost:8089/api/tenant/aaaaa/monitors with body:
```
{
  "labelSelector": {
    "agent_discovered_os": "darwin"
  },
  "details": {
    "type": "local",
    "plugin": {"type": "mem"}
  }
}
```

## Create a new remote monitor

HTTP POST to http://localhost:8089/api/tenant/aaaaa/monitors with body:
```
{
  "labelSelector": {
    "agent_environment": "localdev"
  },
  "details": {
    "type": "remote",
    "monitoringZones": ["dev"],
    "plugin": {
      "type": "ping",
      "urls": ["127.0.0.1"]
    }
  }
}
```

## Update an existing monitor to use template placeholders

http PUT localhost:8089/api/tenant/aaaaa/monitors/$MonitorId with body:
```
{
  "details": {
    "type": "remote",
    "monitoringZones": ["dev"],
    "plugin": {
      "type": "ping",
      "urls": ["${resource.labels.agent_discovered_hostname}"]
    }
  }
}
```

## Get all monitors
```
curl localhost:8089/api/monitors?page=0&size=100
```

> **Notes**:
>
> httpie will not receive the stream the same way curl does.  Currently unsure why.


## Delete a resource
```
http DELETE localhost:8089/api/tenant/aaaaa/monitors/$MonitorId
```

# REST client usage of Monitor Management

Services that need to interact with Monitor Management's REST API can include the client dependency

```xml
  <dependency>
      <groupId>com.rackspace.salus</groupId>
      <artifactId>salus-telemetry-monitor-management</artifactId>
      <version>${model-management.version}</version>
      <classifier>client</classifier>
      <exclusions>
          <exclusion>
              <groupId>*</groupId>
              <artifactId>*</artifactId>
          </exclusion>
      </exclusions>
  </dependency>
```

A limited set of operations are implemented as a component in the 
`com.rackspace.salus.monitor_management.web.client.MonitorManagementClient`
