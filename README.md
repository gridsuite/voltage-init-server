# Voltage Init Server

[![Actions Status](https://github.com/gridsuite/voltage-init-server/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/gridsuite/voltage-init-server/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=org.gridsuite%3Avoltage-init-server&metric=coverage)](https://sonarcloud.io/component_measures?id=org.gridsuite%3Avoltage-init-server&metric=coverage)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)

## Description

The **voltage-init-server** is a microservice of the [GridSuite](https://github.com/gridsuite) platform dedicated to **voltage profile initialization** on electrical power networks. It computes an optimal starting voltage profile (voltage magnitudes and angles at all buses) before running a power flow or optimization, which is a critical pre-processing step in power system simulation.

It provides the following capabilities:

- **Run voltage initialization computations** on a network using [PowSyBL Optimizer](https://github.com/powsybl/powsybl-optimizer), an optimization-based reactive power and voltage initializer.
- **Translate the OpenReac result** into a set of network modifications (generator setpoints, transformer tap positions, shunt compensator sections, SVC/VSC setpoints, bus voltages) and send them to **network-modification-server**.
- **Store results** (voltage profile per bus, reactive slacks, optimizer indicators) in a relational database.
- **Query results** with optional filtering by voltage level (via filter-server).
- **Manage parameter sets** (create, read, update, duplicate, delete) with voltage limit overrides and equipment selection via filters.
- Run computations either **asynchronously** (via RabbitMQ) with cancellation support.
- Emit **debug files** (AMPL intermediate files zipped and stored in S3-compatible object storage) when `debug=true`.

---

## Technical Stack

- Spring Boot (Web, Data JPA, Actuator, Cloud Stream)
- PostgreSQL + Liquibase
- RabbitMQ via Spring Cloud Stream
- PowSyBL Optimizer — optimization-based voltage initializer
- PowSyBL network store client
- [gridsuite-computation](https://github.com/gridsuite/computation)
- S3-compatible object storage (debug files)
- API documentation: OpenAPI / Swagger (`springdoc`)
- Micrometer / Prometheus

---

## Optimizer

The voltage-init-server and powsybl-optimizer do not embed an AMPL solver. To run computations, you need to integrate a compatible  non-linear solver into your jar/Docker image (for example, [Knitro](https://ampl.com/products/solvers/nonlinear-solvers/knitro/)).

---

## Built on gridsuite-computation


The following capabilities are provided by the gridsuite-computation shared library:

 - asynchronous run/cancel pipeline,
 - transactional result notifications,
 - network equipment filtering,
 - report integration,
 - S3 debug file support,
 - Micrometer observability.
---

The voltage-init-server focuses on OpenReac-specific logic (parameters, result model, network modifications) and delegates the common computation infrastructure to this lib.

## Development Scripts

Build Docker image

```shell
mvn install -DskipTests -Dpowsybl.docker.install
```

Please read [liquibase usage](https://github.com/powsybl/powsybl-parent/#liquibase-usage) for instructions to automatically generate changesets. After you generated a changeset do not forget to add it to git and in `src/main/resources/db/changelog/db.changelog-master.yml`.

---

## Interactions with Other Microservices

```
┌────────────────────────┐
│  voltage-init-server   │──► network-store-server          (load network topology and data)
│                        │──► network-modification-server   (create modifications group from result)
│                        │──► filter-server                 (resolve equipment filter UUIDs to IDs)
│                        │──► report-server                 (post computation functional logs)
└────────────────────────┘
         ▲  ▼
      RabbitMQ (voltageinit.run / voltageinit.cancel / voltageinit.result / voltageinit.stopped / voltageinit.cancelfailed / voltageinit.debug)
```

---

## Asynchronous Execution Flow

1. The controller publishes a message on the `voltageinit.run` queue and immediately returns the `resultUuid` to the caller.
2. Two parallel consumers (`consumeRun1` and `consumeRun2`) on the same queue group process messages concurrently, enabling two simultaneous computations per instance.
3. The worker loads the network from network-store, resolves filter references via filter-server to build `OpenReacParameters`, and runs the OpenReac optimizer.
4. On success, the result (bus voltages, reactive slacks, indicators) is saved to the database, and a modifications group is created in network-modification-server.
5. The result notification is published on `voltageinit.result` with additional headers (`REACTIVE_SLACKS_OVER_THRESHOLD`, `VOLTAGE_LEVEL_LIMITS_OUT_OF_NOMINAL_VOLTAGE_RANGE`).
6. Cancellation goes through the `voltageinit.cancel` queue, which interrupts the running computation and publishes on `voltageinit.stopped`.
7. Dead-letter queues (`voltageinit.run.dlx`) and quorum queues (`delivery-limit: 2`) ensure reliability.

---


## Result Data

A voltage initialization result is composed of several datasets:

| Dataset | Description |
|---|---|
| **Bus voltages** | Computed voltage magnitude (kV) and angle (degrees) per bus, indexed by `(voltageLevelId, busId)`. |
| **Reactive slacks** | Residual reactive power imbalance per bus. A `REACTIVE_SLACKS_OVER_THRESHOLD` flag is set when any slack exceeds the configured threshold (default 500 Mvar). |
| **Optimizer indicators** | Key-value map of internal OpenReac optimizer metrics. |
| **Modifications group UUID** | UUID of the modifications group created in network-modification-server, containing the actionable network changes (setpoints, tap positions, etc.). |
| **Debug file location** | S3 path of the zipped AMPL debug files (populated only when `debug=true`). |

Result data can be filtered on retrieval by voltage level using `globalFilters` (resolved via filter-server).

---


## Parameters

Voltage initialization parameters control which equipment OpenReac is allowed to adjust and what voltage limits to enforce:

| Parameter | Description |
|---|---|
| **Voltage limit overrides** | Per-filter voltage limit adjustments, in two phases: `DEFAULT` (applied first to all matching voltage levels) and `MODIFICATION` (applied on top as targeted overrides). Priority order determines which override wins when multiple filters match the same voltage level. |
| **Generator selection** (`variableQGenerators`) | Filters defining generators whose reactive setpoint OpenReac may vary. Mode: `ALL_EXCEPT` or `NONE_EXCEPT`. |
| **Transformer selection** (`variableTwoWindingsTransformers`) | Filters defining two-winding transformers whose tap position OpenReac may adjust. Mode: `ALL_EXCEPT` or `NONE_EXCEPT`. |
| **Shunt compensator selection** (`variableShuntCompensators`) | Filters defining shunt compensators OpenReac may switch. Mode: `ALL_EXCEPT` or `NONE_EXCEPT`. |
| **Reactive slacks threshold** | Threshold (Mvar, default 500) above which a reactive slack is flagged as abnormal and triggers a warning in the result. |
| **Shunt compensator activation threshold** | Threshold (Mvar) for flagging shunt compensator activation. |
| **Update bus voltage** | Whether computed bus voltage targets are included as network modifications. |

Filter references are resolved at **computation time** against the live network via filter-server, allowing parameter sets to be reused across different network states.

---

## Micrometer observability

All major computation steps (network loading, computation execution, result saving, network flushing) are wrapped in named Micrometer observations via `VoltageInitObserver`, enabling distributed tracing and metric collection without cluttering business logic.

---

## Useful Links

- [PowSyBL OpenReac documentation](https://powsybl.readthedocs.io/projects/powsybl-optimizer/en/latest/)
