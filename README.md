# TrafficFlow

## Overview

Urban traffic light control and congestion-based routing.

Domain entities: intersections, districts, signal types.

Every class in this repo lives in a single flat package, `co.wethinkcode.trafficflow`. TrafficFlow is built
as a small set of independent services, following a growth path from simple data
cleanup through synchronous REST calls to asynchronous MQ decoupling and alerting:

1. clean a messy legacy CSV export (`intersections-legacy.csv`) — handled by **IngestionServiceApp**
2. serve it up and act on it, via three REST services calling each other directly
   over HTTP
3. decouple the relevant services with an ActiveMQ topic (`congestion-topic`) instead of
   direct calls — shared broker setup lives in [`common/`](common)
4. raise the alarm on failure — handled by **IntersectionWatchdogApp**


| Service                 | Folder                                            | Port | Role                                                                                         |
| ------------------------- | --------------------------------------------------- | ------ | ---------------------------------------------------------------------------------------------- |
| IngestionServiceApp     | [`ingestion-service/`](ingestion-service)         | 7020 | Parses and cleans `intersections-legacy.csv`                                                 |
| IntersectionServiceApp  | [`intersection-service/`](intersection-service)   | 7021 | Validates intersection/district names (source of truth).                                     |
| CongestionServiceApp    | [`congestion-service/`](congestion-service)       | 7022 | Tracks the city-wide Congestion Level (0-8).                                                 |
| RoutingServiceApp       | [`routing-service/`](routing-service)             | 7023 | Provides estimated travel times based on congestion and intersection.                        |
| IntersectionWatchdogApp | [`intersection-watchdog/`](intersection-watchdog) | 7024 | cries for help if the Intersection Service crashes, since routes can no longer be validated. |

Plus [`common/`](common) (no port) — the shared ActiveMQ broker and MQ config notes
for `congestion-topic`: Routing Service becomes aware of congestion changes via an ActiveMQ Topic instead of querying Congestion Service directly.

## Implementation status

Partial implementation for the Systems Integration second-project messaging attempt.

- **Implemented:** The heartbeat portion of Stage 4 uses the ActiveMQ queue `intersection-heartbeat-queue` to connect `intersection-service` (producer) and `intersection-watchdog` (consumer). It includes per-intersection tracking, independent missing-heartbeat checks, timeout alerts, recovery, and unit/integration tests. Both services expose `/health` and `/status`; the watchdog also exposes `/alerts`.
- **Demo data:** Heartbeats use demo records matching `intersections-legacy.csv`: `INT-1001` (Downtown), `INT-1002` (Midtown), and `INT-1005` (Downtown). The CSV is not yet ingested into this flow.
- **Required stages still incomplete:** Stage 1 CSV parsing/cleaning and Stage 2 canonical domain REST endpoints and synchronous service integration.
- **Stretch work still incomplete:** Stage 3 `congestion-topic` pub/sub integration and Stage 4 dead-letter queue-specific alerting. Missing-heartbeat detection is implemented; dedicated dead-letter monitoring is not.
- **Service details:** See [`intersection-service/README.md`](intersection-service/README.md) and [`intersection-watchdog/README.md`](intersection-watchdog/README.md) for configuration and the live demonstration.

## Your task

Build out the four stages below, roughly in order — each stage builds on the last.
Stages 1-2 are the core exercise; stages 3-4 are stretch goals if you have time left.
Exact field names and response shapes are your call throughout — see
"Integration contracts" below for illustrative shapes, not a spec to match exactly.


| Stage | What                                                                                                                                                                                       | Required? | Rough effort |
| ------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------- | -------------- |
| 1     | Clean `intersections-legacy.csv` in **IngestionServiceApp** and expose the cleaned records (see [`ingestion-service/README.md`](ingestion-service) for the specific data issues to handle) | Required  | 1-2 hrs      |
| 2     | Implement the domain endpoints in **IntersectionServiceApp**, **CongestionServiceApp**, and **RoutingServiceApp**, wired together with direct synchronous REST calls                       | Required  | 2-3 hrs      |
| 3     | Decouple Congestion → Routing with the `congestion-topic` ActiveMQ topic instead of a direct REST call (see [`common/README.md`](common))                                                 | Stretch   | 1 hr         |
| 4     | Add heartbeat/dead-letter alerting in **IntersectionWatchdogApp** so it notices when the Intersection Service goes down                                                                    | Stretch   | 1 hr         |

(Effort is a rough guide, not a hard budget — go with what feels right for your pace.)

## Integration contracts

Every place one service calls or messages another, with an illustrative shape.
None of these field names are binding — match the intent, not the exact JSON.


| From → To                                    | Stage | Mechanism                                     | Shape                                                                                                                                                                                                                                                                                                                                                                                                            |
| ----------------------------------------------- | ------- | ----------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| ingestion-service → intersection-service     | 1     | REST, `GET`                                   | `GET /intersections` on ingestion-service (port 7020) → `200 OK` + JSON array of cleaned records, e.g. `[{"id": "INT-1001", "district": "Downtown", "signalType": "4-way", "active": true}, ...]`. intersection-service loads this as its canonical list.                                                                                                                                                       |
| routing-service → intersection-service       | 2     | REST, `GET`                                   | `GET /intersections/{id}` on intersection-service (port 7021) → `200 OK` with the record, or `404` if the id/district isn't recognized. routing-service calls this to validate a route's endpoints before estimating travel time.                                                                                                                                                                               |
| routing-service → congestion-service         | 2     | REST, `GET`                                   | `GET /congestion` on congestion-service (port 7022) → `200 OK` + `{"level": 0-8}`. routing-service polls this per-request in stage 2.                                                                                                                                                                                                                                                                           |
| congestion-service → routing-service         | 3     | ActiveMQ Topic `congestion-topic`             | Once stage 3 is in place, congestion-service publishes `{"level": 0-8}` to `congestion-topic` whenever the level changes, and routing-service subscribes instead of polling `GET /congestion`. Broker URL + topic name come from the shared `MqConfig` class — see [`common/README.md`](common).                                                                                                                |
| intersection-service → intersection-watchdog | 4     | ActiveMQ Queue `intersection-heartbeat-queue` | intersection-service publishes a periodic heartbeat message to `intersection-heartbeat-queue`; intersection-watchdog consumes it and raises an alert (e.g. logs, or its own `/alert` state) if a heartbeat is missed or lands in the dead-letter queue. Queue name comes from the shared `MqConfig` class, same pattern as `congestion-topic` — see [`intersection-watchdog/README.md`](intersection-watchdog). |

## Project structure

```
trafficflow/
├── README.md
├── .gitignore
├── ingestion-service/          (port 7020)
│   ├── pom.xml
│   ├── README.md
│   └── src/main/
│       ├── java/co/wethinkcode/trafficflow/IngestionServiceApp.java
│       └── resources/intersections-legacy.csv
├── intersection-service/          (port 7021)
├── congestion-service/          (port 7022)
├── routing-service/          (port 7023)
├── common/
│   ├── docker-compose.yml
│   └── README.md
└── intersection-watchdog/          (port 7024)
```

## Build

Requirements: Java 17+, Maven 3.8+, Docker (for the broker in `common/`).

Every folder here (`ingestion-service/`, each domain service, and `intersection-watchdog/`) is
an **independent** Maven project — there is no parent/aggregator pom. Build one at a
time, e.g.:

```bash
mvn -f intersection-service/pom.xml package
```

...or build every module in the repo in one pass from the project root:

```bash
find . -name pom.xml -execdir mvn -q package \;
```

## Run

Run the commands below from the repository root. Build the relevant modules
first, and start each Java service in its own terminal.

Start the broker before the implemented heartbeat services:

```bash
docker compose -f common/docker-compose.yml up -d
```

Start the watchdog:

```bash
java -jar intersection-watchdog/target/intersection-watchdog.jar
```

Start the heartbeat producer in another terminal:

```bash
java -jar intersection-service/target/intersection-service.jar
```

The other services remain scaffolds. After building them, their health
endpoints can be checked by starting each in a separate terminal:

```bash
java -jar ingestion-service/target/ingestion-service.jar
java -jar congestion-service/target/congestion-service.jar
java -jar routing-service/target/routing-service.jar
```


| Service                                           | Port |
| --------------------------------------------------- | ------ |
| IngestionServiceApp (`ingestion-service`)         | 7020 |
| IntersectionServiceApp (`intersection-service`)   | 7021 |
| CongestionServiceApp (`congestion-service`)       | 7022 |
| RoutingServiceApp (`routing-service`)             | 7023 |
| IntersectionWatchdogApp (`intersection-watchdog`) | 7024 |

## Test

### Automated Unit Tests (No Broker Required)

Run fast, isolated unit tests without needing ActiveMQ or Docker running:

Run these commands from the repository root.

```bash
# Unit tests in intersection-service (5 tests: serialization, validation, producer defaults)
mvn -f intersection-service/pom.xml test

# Unit tests in intersection-watchdog (8 tests: tracking, timeouts, invalid message discard, recovery)
mvn -f intersection-watchdog/pom.xml test
```

### Clean Build and Integration Verification (Real Broker Required)

From the repository root, start ActiveMQ:

```bash
docker compose -f common/docker-compose.yml up -d
```

Then run:

```bash
# Unit tests, packaging, and the producer integration test
mvn -f intersection-service/pom.xml clean verify

# Unit tests, packaging, and the watchdog integration test
mvn -f intersection-watchdog/pom.xml clean verify
```

`clean verify` removes previous build output, compiles the source and tests,
runs the unit tests, creates the shaded JAR, and runs and verifies the Failsafe
integration tests. The integration tests use test queues separate from the
demonstration queue. A reachable broker is required for this verification.

### Manual Sanity Checks

Each running service exposes `/health`. For the implemented heartbeat flow:

```bash
curl http://localhost:7021/health   # -> OK
curl http://localhost:7024/health   # -> OK
curl -i http://localhost:7024/alerts
```

With the default demo records, confirm that heartbeats have arrived and
`monitoredCount` is `3`. Normal operation returns `200 OK` with `alertCount: 0`
and intersection states of `HEALTHY`. Missing heartbeats produce `503 Service Unavailable`; receiving valid heartbeats again clears the alerts.

The `/health` endpoint reports service liveness. Use `/alerts` and the receipt
logs to check heartbeat monitoring. See the watchdog README for the complete
stop/restart demonstration.
