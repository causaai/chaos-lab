# perf-impact

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/perf-impact-1.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Related Guides

- Micrometer Registry Prometheus ([guide](https://quarkus.io/guides/micrometer)): Enable Prometheus support for Micrometer
- Scheduler ([guide](https://quarkus.io/guides/scheduler)): Schedule jobs and tasks
- RESTEasy Classic ([guide](https://quarkus.io/guides/resteasy)): REST endpoint framework implementing Jakarta REST and more
- Micrometer metrics ([guide](https://quarkus.io/guides/micrometer)): Instrument the runtime and your application with dimensional metrics using Micrometer.

## Provided Code

### RESTEasy JAX-RS

Easily start your RESTful Web Services

[Related guide section...](https://quarkus.io/guides/getting-started#the-jax-rs-resources)

### Code structure

```text
ai.causa
│
├── bootstrap
│   ├── AppLifecycleManager.java
│   └── EnvConfigLoader.java
│
├── config
│   ├── SimulatorConfig.java
│   ├── PhaseTimingConfig.java
│   └── ScenarioConfig.java
│
├── engine
│   ├── PhaseEngine.java
│   ├── Phase.java
│   ├── ScenarioScheduler.java
│   └── WorkloadCoordinator.java
│
├── scenario
│   ├── api
│   │     ├── GcScenario.java
│   │     ├── ScenarioContext.java
│   │     └── ScenarioPhaseHandler.java
│   │
│   ├── impl
│   │     ├── AllocationStormScenario.java
│   │     ├── PromotionPressureScenario.java
│   │     ├── LiveSetBloatScenario.java
│   │     ├── HumongousObjectsScenario.java
│   │     ├── ReferenceStormScenario.java
│   │     ├── RSetPressureScenario.java
│   │     ├── SurvivorOverflowScenario.java
│   │     └── AllocationStallScenario.java
│   │
│   └── registry
│         └── ScenarioRegistry.java
│
├── memory
│   ├── allocator
│   │     ├── ObjectAllocator.java
│   │     ├── AllocationPattern.java
│   │     └── AllocationController.java
│   │
│   ├── retention
│   │     ├── RetainedHeap.java
│   │     └── RetentionController.java
│   │
│   ├── graph
│   │     ├── ObjectGraphBuilder.java
│   │     └── CrossReferenceGraph.java
│   │
│   └── reference
│         ├── ReferenceFactory.java
│         └── ReferenceStore.java
│
├── workload
│   ├── WorkloadService.java
│   ├── WorkRequestSimulator.java
│   └── CpuWorkSimulator.java
│
├── metrics
│   ├── MetricsBinder.java
│   ├── ScenarioMetrics.java
│   └── ThroughputTracker.java
│
├── api
│   ├── WorkResource.java
│   ├── StatusResource.java
│   └── ControlResource.java
│
├── util
│   ├── RandomUtil.java
│   ├── SizeUtil.java
│   └── TimeUtil.java
│
└── constants
    ├── EnvKeys.java
    └── SimulatorDefaults.java
```

##### Allocation storm
```text
GC_SCENARIO=ALLOCATION_STORM java -Xms512m -Xmx512m -Xlog:gc*,gc+heap=info,gc+age=trace      -jar target/quarkus-app/quarkus-run.jar
```
