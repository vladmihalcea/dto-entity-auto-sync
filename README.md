# DTO to Entity Auto-Sync

A demo project that shows how to **automatically synchronize changes from DTOs to the DB** using various patterns. 

---

## What the project does

Managing the gap between a DTO received from the outside world and the records that live inside the database is one of the most repetitive tasks in enterprise applications.

This project provides several patterns that are implemented using JDBC, jOOQ, Spring Data JPA and MapStruct. 

### JDBC and jOOQ

For JDBC and jOOQ, there are two patterns that are implemented:

1. Multi-Op Sync: After users load a hierarchical DTO structure, they can modify it by issuing individual operations for every change (e.g. "add comment", "remove tag", "upvote comment"). 
2. **Diff-Merge** : With the previously loaded hierarchical DTO structure , users can modify it in-memory and then call a service method to persist the changes. The service method accepts the modified DTO and the original one and applies the changes by comparing the two structures. This is a more efficient approach since all changes are done in the context of a single database transaction.

### Spring Data JPA with MapStruct

For Spring Data JPA, the project provides an auto-sync pattern based on a **Diff-Merge** approach that works as follows:

1. The user loads a graph of entities and use MapStruct to transform it to a hierarchical DTO structure and sends it to the client.
2. The client makes in-memory modifications to the DTO (e.g. changing a post title, upvoting a comment, adding/removing tags), then sends the modified DTO back to the server.
3. The server receives the modified DTO and uses MapStruct to copy the simple fields from the DTO onto the managed entity. For collections, a custom `EntityMerger` component diffs the DTO collection against the entity collection using `equals()`/`hashCode()`, then:
   - removes items that are no longer present,
   - updates existing items (copying non-association attributes via `BeanUtils.copyProperties` guided by the Hibernate metamodel),
   - adds new items through domain helper methods that maintain JPA bidirectional consistency.

The domain is a simple forum: **Post**, **PostComment**, **Tag**, and **User**.

## Modules

The project is split into two self-contained Maven modules, each with its own tests and JMH benchmarks:

| Module | What it contains |
|---|---|
| `core` | The base Multi-Ops and Diff-Merge patterns. On JPA, Multi-Ops is not idiomatic, since the persistence context merges a whole aggregate in one unit of work, and serves as a baseline only: it uses the per-change endpoints of JDBC and jOOQ, except that the post's own fields are updated with `PATCH /post`, since `PUT /post` is the JPA Diff-Merge. Concurrent writes follow the last-write-wins rule, as documented by the `*LastWriteWinsTest` classes. |
| `versioning` | The same patterns extended with root-level optimistic locking, which prevents lost updates. Any change to the Post aggregate increments the `posts.version` column, every write requires the version in the `If-Match` header, and a stale write returns `412 Precondition Failed`. On JPA, the `RootAware` event listeners increment the post version when only a child entity changes. |

Comparing the two modules (`diff -r core/src versioning/src`) shows what the optimistic locking extension adds, and nothing else: both modules have the same patterns, including the JPA Multi-Ops baseline, the same endpoints, and the same responses, except that every `versioning` write takes the `If-Match` header and returns the `ETag` header. Both modules load the same generated data set, and the `versioning` block of the run-matrix driver (`benchmark-matrix/cells/versioning.txt`) runs the write benchmarks of both modules in interleaved rounds.

Each module uses its own database, `dto2entity` for `core` and `dto2entity_versioning` for `versioning`, since the Hibernate schema update would otherwise add the `posts.version` column of the `versioning` module to the tables that the `core` module measures.

## The benchmark data set

`DataSetGenerator` generates the data set deterministically, from a fixed seed, and `BenchmarkTestData` streams every table into a `COPY FROM STDIN` command, without intermediate files. At scale 1, the default, the data set holds:

| Table | Rows | Contents |
|---|---|---|
| `posts` | 20,005 | 20,000 posts, with the IDs 1 to 20,000, which the write benchmarks edit, and five fan-out posts, with the IDs 10,000,000 + N, which have N = 0, 5, 10, 25, or 100 comments and tags and which the read benchmarks load |
| `post_comments` | 40,140 | a comment and a reply to it for every edited post, and the comments of the fan-out posts |
| `tags`, `post_tags` | 80,140 | four tags for every edited post, and the tags of the fan-out posts |
| `users` | 50,000 | the authors of the posts and comments |

The authors follow a Zipf distribution with exponent 1 over the users, since the activity of forum users is heavy-tailed: the most active user writes about 8.5% of the comments, the top 1% of the users write about 60%, and the fan-out post with 100 comments has 79 distinct comment authors. The first three users, `vlad@acme.com`, `john@acme.com`, and `jane@acme.com`, are the users of the original data set, which the REST tests use. Apart from the authors, the rows keep the contents and the physical order of the original data set, which three users authored.

The `benchmark.scale` system property or the `BENCHMARK_SCALE` environment variable multiplies the edited posts and the users, e.g., scale 10 generates 200,005 posts and 500,000 users. With the run-matrix driver, add `-jvmArgsAppend -Dbenchmark.scale=10` to the `@jmh` line of a block. The `benchmark_dataset` marker records the generator version and the scale, so switching the scale reloads the data set. To export the data set as CSV files, e.g., for the replication package, enable `CreateDataSetTest`, which writes them to `core/target/data-set-sf<scale>`.

### Loading and restoring the data set

The write benchmarks need the loaded rows at the start of every iteration, since their edits cannot be applied twice to the same post. Instead of reloading the whole data set before every iteration, a write fork loads it once, at its first iteration, so every fork starts from the same physical state, and `BenchmarkTestData.resetTestData()` then restores only the posts that the previous iteration edited:

1. it finds the edited posts as those with post, comment, or tag link rows newer than the transaction recorded in the `benchmark_dataset` marker;
2. it deletes their rows and inserts them again from the generator, which produces the rows of any range of posts;
3. it runs `VACUUM` on the edited tables and moves the marker to the restore transaction.

The load turns autovacuum off for the four edited tables (`autovacuum_enabled = false`), since a fast server edits hundreds of posts per iteration, enough to start an autovacuum or an automatic analyze in the middle of a measurement; the `VACUUM` of every restore reclaims the deleted rows instead, and the statistics of the load stay valid, since every restore reproduces the loaded rows. After its `VACUUM ANALYZE`, the load also vacuums `pg_catalog.pg_statistic`, whose rows the analyze rewrites, since autovacuum would otherwise vacuum it during the next measurement.

Any change outside the editable posts makes it reload the data set, and the read benchmarks reload a restored data set, since its physical layout differs from a fresh load. The load skips the foreign key triggers, since the generated rows are consistent (this requires a superuser), builds the secondary indexes after the `COPY`, and keeps an unchanged `users` table, which the benchmarks never write. On a 2015 laptop, the restore takes about 0.2 s per iteration at scale 1 and 1.2 s at scale 10, while a full load takes about 8 s and 28 s.

## Running the benchmarks with the database on another machine

The benchmarks can run on one machine (the application host, which only needs a Java runtime) against a PostgreSQL server on another machine (the database host).

1. On the database host, allow connections from the application host and restart PostgreSQL:
   - `postgresql.conf`: `listen_addresses = '*'`
   - `pg_hba.conf`: `host  dto2entity,dto2entity_versioning  postgres  <application-host-ip>/32  scram-sha-256`
   - Windows firewall: `New-NetFirewallRule -DisplayName "PostgreSQL" -Direction Inbound -Protocol TCP -LocalPort 5432 -RemoteAddress <application-host-ip> -Action Allow`
   - create the `dto2entity` and `dto2entity_versioning` databases; the benchmarks create the schema and load the data set.
2. Build the benchmark bundles on any machine with Maven:
   ```bash
   mvn -P benchmark-bundle package
   ```
   This creates `core/target/benchmark-bundle` and `versioning/target/benchmark-bundle`, which contain the classes, the dependencies, the data set generator, and the `run-benchmarks` scripts. Every build empties the bundle folder first, so no jar of an older dependency version stays in `lib`, where it would compete with the current one on the class path.
3. Copy a bundle to the application host and run it with the database host settings:
   ```bat
   set JDBC_URL=jdbc:postgresql://<database-host-ip>:5432/dto2entity
   set JDBC_USER=postgres
   set JDBC_PASSWORD=admin
   run-benchmarks.bat "Benchmark.benchmark(MultiOps|DiffMerge)Sync" -f 5 -rf json -rff results.json
   ```
   For the `versioning` bundle, point `JDBC_URL` to the `dto2entity_versioning` database. The `jdbc.url`, `jdbc.user`, and `jdbc.password` system properties work as well. When the database URL is configured explicitly, a connection failure stops the run instead of falling back to Testcontainers.

Connect the two machines through wired Ethernet, since Wi-Fi latency jitter exceeds the differences being measured, and record the round-trip time (`ping -n 100 <database-host-ip>`) together with the benchmark results.

### On two Linux servers

`benchmark-matrix/setup-server.sh` prepares a fresh Ubuntu 26.04 LTS server, as root, and running it again is harmless:

```bash
ROLE=db  PEER_IP=<application-server-ip> PG_PASSWORD=<password> NTP_SERVER=<ntp-server> sh benchmark-matrix/setup-server.sh   # the database server
ROLE=app PEER_IP=<database-server-ip>    PG_PASSWORD=<password> NTP_SERVER=<ntp-server> sh benchmark-matrix/setup-server.sh   # the application server
```

It stops the background jobs that would run during a window (automatic updates, maintenance timers, snapd, and thermald); installs PostgreSQL 18.6 from the Ubuntu archive and holds its version; sizes its memory for the 32 GB servers (`shared_buffers` 8 GB, `effective_cache_size` 24 GB, `work_mem` 16 MB, `maintenance_work_mem` 1 GB, `random_page_cost` 1.1, `max_wal_size` 16 GB, `checkpoint_timeout` 30 min, and the durable default `synchronous_commit = on`); installs Temurin 21.0.4+7; sets the `performance` governor, limits the CPU idle states to C1, and loads the `msr` module, at every boot; synchronizes the clock with `chrony`, from `NTP_SERVER` if set (`ntp.hostner.com` on the Hostner network, which lets neither NTP nor NTS through to the public servers; with `authselectmode ignore`, since `chrony` would otherwise not select an unauthenticated server while Ubuntu's NTS servers are configured), and waits for the synchronization, so that no large correction happens during the campaign, while a synchronized `chrony` adjusts the rate of the monotonic clock that JMH reads by a few parts per million, far below the run-to-run variation; when no NTP source is reachable, it sets the clock once from the `Date` header of an HTTPS server and stops `chrony`, so that the clock runs free during the campaign and no late correction changes that rate; enables the firewall, with SSH from anywhere and PostgreSQL and the telemetry agent from `PEER_IP` only; and writes a server record, `/root/server-record-<host>-<time>.txt`, so that the records of the two servers can be compared with `diff`. The database server runs PostgreSQL, and the application server stops it; running the script with the other role swaps them. `PG_PASSWORD` is required with `PEER_IP`, since the servers are on the public internet; the application server passes it to the benchmarks as `JDBC_PASSWORD` and to `dump-pg-settings.sh` as `PGPASSWORD`. Then start the telemetry agent on the database server and the campaign on the application server:

```bash
nohup nice -n 19 sh core/run-telemetry-agent.sh > agent.log 2>&1 &                     # the database server, as root
DB_HOST=<database-server-ip> TELEMETRY_AGENT_URL=http://<database-server-ip>:9500 \
JDBC_PASSWORD=<password> PGPASSWORD=<password> sh benchmark-matrix/run-campaign.sh      # the application server
```

## Run isolation

The write benchmarks truncate and reload the tables before every iteration, so two processes must never use the same database at the same time. The harness keeps the runs apart in three ways:

1. **One process per database.** The `benchmark_run` table records every JVM that used the database, whether a JMH fork or a test run, with its module, host, PID, process start time, start and end times, and status. A JVM registers its run as `started` before it touches the tables and marks it `completed` when it exits, whether its benchmarks or tests passed or not, so a second process fails fast while a run is started, with a message that names that run. A killed JVM cannot mark its run, so the next run on the same host marks it `interrupted` once no process with its PID and start time runs. Only a run on another host cannot tell whether the process is gone: after a host crash, mark the run as interrupted with the `UPDATE` statement that the error message shows. The table keeps no connection open, and the JVM writes to it only when it starts and when it exits, outside the measurements.
2. **One module per database.** The `benchmark_module` table records the module that owns the database, and the other module's benchmarks and tests refuse to use it before `DatabaseSchema` creates the tables and Hibernate validates them.
3. **Reloading a modified data set.** The `benchmark_dataset` table stores the ID of the transaction that loaded the data set. The read benchmarks reuse the data set only if no row is newer than that transaction and every table has its original row count, so any modification, by a write benchmark, a test, or a manual session, makes them reload it.

Do not run benchmarks in parallel even against separate databases, since parallel processes compete for the CPUs of both hosts and for the network link.

## Running a benchmark campaign with the run-matrix driver

The `benchmark-matrix` folder holds a driver that runs a block of benchmark cells unattended, in run windows of any length, and continues where it stopped. A cell is one benchmark of one module, and a cell file lists the cells of a block, one per line:

```
@jmh -bm sample -wi 15 -w 3s -i 30 -r 3s
# <name>              <module>  <rounds>  <benchmark regex>  [JMH options]
jdbc-mqdto-10         core      10        JdbcLoadBenchmark\.benchmarkLoadDtoUsingRest_10_collection_size$
jdbc-msjson-10        core      10        JdbcLoadBenchmark\.benchmarkLoadJsonStringRest_10_collection_size$
```

The `@jmh` line sets the JMH options of every cell, and an optional `@jvm` line replaces the default JVM options, `-Xms4g -Xmx4g -XX:+AlwaysPreTouch`, which the JMH forks inherit. Every block runs in the `SampleTime` mode (`-bm sample`), which overrides the `AverageTime` mode of the benchmark annotations, so every result holds the mean and the percentiles up to p99.9, and the JSON keeps a histogram of every iteration. The `cells` folder holds the blocks of the current benchmarks: `smoke`, `rq1-rest`, `typed-msjson`, `rq2-writes`, `versioning`, `w7-concurrency` (4, 8, and 16 threads through per-cell `-t` options), `w7-sf10` (the scale-10 data set), `role-swap` (the headline cells with the roles of the two servers swapped), and `layer-overhead` (three cells with and without the layer profiler). Cells that are compared with each other belong in the same block, so that both sides of every comparison run in the same windows.

The driver runs the block in **randomized interleaved rounds**: every round runs one JMH fork of every cell, in an order that the seed of the block fixes, so a slow drift of the machines spreads over all cells instead of biasing the cells that run last. Every fork runs as a separate JMH process, with `-f 1`, the telemetry and the layer profilers, and the `JDBC_URL` of its module's database. A cell with the option `--without-layer-profiler`, which the driver consumes instead of passing it to JMH, runs without the layer profiler: its probes stay installed but do not record, so twin cells with and without the option measure the overhead of the profiler.

Run it from the folder that holds the `core` and `versioning` bundles, or from the repository root after `mvn -P benchmark-bundle package`. It requires a JDK, since the Java source launcher compiles `RunMatrix.java` on the fly:

```bat
rem Check that every cell matches exactly one benchmark of its module
benchmark-matrix\run-matrix.bat check benchmark-matrix\cells\rq1-rest.txt

rem Run the block until 07:30, against the database host and its telemetry agent
benchmark-matrix\run-matrix.bat benchmark-matrix\cells\rq1-rest.txt --db-host 192.168.1.20 --agent http://192.168.1.20:9500 --until 07:30

rem Show the progress and the estimated remaining time, or stop after the current fork
benchmark-matrix\run-matrix.bat status benchmark-matrix\cells\rq1-rest.txt
benchmark-matrix\run-matrix.bat stop benchmark-matrix\cells\rq1-rest.txt
```

- **Resuming:** every fork writes `results/<block>/round-<r>/<cell>.json` and `.log`, and the result file appears only when the fork succeeded. Running the same command again skips the finished forks, so stopping at any time loses at most the fork in progress. The seed and a digest of the cell file are kept in `session.properties`, and the driver refuses a cell file that changed since the block started, since that would change the order of the remaining forks.
- **Run windows:** `--until <HH:mm>` or `--hours <hours>` starts a fork only if it ends before the deadline, based on the measured durations of the earlier forks of the cell, and `stop` ends the window after the current fork. `Ctrl+C` stops immediately, and the interrupted fork runs again on the next start.
- **Records:** `forks.csv` holds the start, end, duration, and status of every fork, and `window-<time>.txt` holds the environment of every run window, including the Java version and the power settings: the power scheme on Windows, and on Linux the frequency governor, the energy performance preference, the turbo setting, and the enabled CPU idle states. With a remote `--db-host`, the driver records `ping` to the database host before every round and at the end of the window.
- **Failures:** the log of a failed fork is kept as `<cell>.failed-<time>.log`, the fork runs again on the next start, and the window stops after three consecutive failures.
- **Aiven:** `BENCHMARK_DATABASE=AIVEN` (or `-Dbenchmark.database=AIVEN`) switches the tests and the benchmark forks to a PostgreSQL service on Aiven, and `database=AIVEN` in `~/.dto2entity/benchmark.properties` makes it the default of a machine, which `BENCHMARK_DATABASE=POSTGRESQL` overrides to use the local server, through `AivenDataSourceProvider`. It reads `host`, `port`, `user`, and `password` from the `AIVEN_HOST`, `AIVEN_PORT`, `AIVEN_USER`, and `AIVEN_PASSWORD` environment variables or from `~/.dto2entity/aiven.properties`, which keeps the credentials out of the repository, connects with SSL, and limits the pool to 8 connections, since the small Aiven plans allow 15. The service needs the `dto2entity` and `dto2entity_versioning` databases (`CREATE DATABASE`), and the window file records the database. It suits checking the client-server path, not measurements: over the internet, every statement and commit pays a round trip of about 27 ms from Romania, which dominates the response time.
- **Other configurations:** to run the same blocks in another configuration, e.g., with the database on a separate host, pass `--results <dir>` with a folder of its own, so that the results of the two configurations never mix.
- **Campaigns:** `run-campaign.ps1` runs several blocks one after another, by default `rq2-writes`, `rq1-rest`, `typed-msjson`, `w7-sf10`, and `w7-concurrency`, and continues where it stopped when started again: `powershell -NoProfile -ExecutionPolicy Bypass -File benchmark-matrix\run-campaign.ps1`. Its results go to `results\<computer>-<database>` unless `-Results` names another folder, and every results folder records its database and refuses another one, so a resumed campaign never mixes two databases. At every start it dumps the server settings (of the Aiven service when `BENCHMARK_DATABASE=AIVEN`) and checks the pending blocks; a block that stops with forks left ends the window, so the next block never starts early. Ctrl+C, `run-matrix stop`, or closing the window are all safe: a fork without a result runs again. A fork during which the machine slept, e.g., with the laptop lid closed, is discarded and runs again, since Windows logs every sleep and wake. On Linux, `run-campaign.sh` does the same, apart from the sleep check, with the database server in `DB_HOST`, the blocks in `BLOCKS`, and the results folder in `RESULTS` (by default `results/<host>-postgresql`).
- **Server record:** every fork writes the database URL, its `benchmark_run` ID, the PostgreSQL version, and the settings that the paper names to its log (`Database of the core module: ...`), so that the log of every fork shows which server it measured.
- **Process log:** on Windows, the driver also starts `process-log.ps1`, which writes the CPU use of every busy process every 10 s, services included, to `processes-<time>.csv`, so the results show whether anything else ran during the window. It stops on its own when the driver exits. On Linux, the driver starts `pidstat -u -h -l 10` of the `sysstat` package instead, which writes to `processes-<time>.txt`, and the telemetry agent logs the database server the same way. Before a window, run it alone for a minute (`powershell -NoProfile -ExecutionPolicy Bypass -File benchmark-matrix\process-log.ps1 -OutFile idle.csv`, then Ctrl+C) to check that the machine is idle.
- **Screenshots:** before starting the driver and after it stops, `resmon-snapshot.ps1 <png file>`, run from an administrator PowerShell, opens Resource Monitor, lets its graphs fill for 30 s, saves a screenshot of its window, and closes it, so that Resource Monitor never runs during a fork.
- **PostgreSQL settings:** at the start of every run window, `dump-pg-settings.bat <db host> results\<block>\pg-settings-<date>.txt` (or `dump-pg-settings.sh`) records the PostgreSQL version and every server setting, such as `shared_buffers`, with `psql`, which the database host has in the `bin` folder of PostgreSQL.

## Recording the CPU telemetry

The `TelemetryProfiler` JMH profiler records the CPU telemetry of every iteration, so the results show whether the CPU held its clocks or got throttled. The `run-benchmarks` scripts and the Maven benchmark tests enable it by default. The metrics of the application host are prefixed by `app.`, and those of the database host by `db.`:

| Metric | Unit | Aggregation | Windows source | Linux source |
|---|---|---|---|---|
| `cpu.mhz` | MHz | average | nominal frequency × `% Processor Performance`, as in Task Manager | the clock of the running cores: the base frequency × the APERF increments over the MPERF increments of all CPUs, as the `Bzy_MHz` of `turbostat` (root and the `msr` module); without them, the mean `cpufreq/scaling_cur_freq` of all CPUs |
| `cpu.util` | % | average | `% Processor Time` | `/proc/stat` |
| `cpu.limit` | % | minimum | `% Performance Limit`, below 100 when the firmware limits the CPU for power or thermal reasons | not available |
| `thermal.limit` | % | minimum | `% Passive Limit` of the thermal zones, below 100 when Windows throttles the CPU to cool it | not available |
| `temp` | °C | maximum | `Temperature` of the ACPI thermal zones | `k10temp`, `zenpower`, or `coretemp` sensors |
| `thermal.throttles` | events | sum | not available, the two limits take its place | `thermal_throttle/core_throttle_count` and `package_throttle_count` of Intel CPUs, counting every core and package once |
| `cpu.power` | W | average | not available | the package energy counters of Intel RAPL, `/sys/class/powercap/intel-rapl:*/energy_uj` (root) |
| `cpu.power.limit` | W | minimum | not available | the long-term power limit (PL1) of the packages, `constraint_0_power_limit_uw` |

The profiler samples the counters once per second through `typeperf` on Windows and through `/proc` and `/sys` on Linux. Many notebooks expose only a motherboard or ambient sensor through the ACPI thermal zones, which shows up as a constant `temp` value, so `cpu.mhz` and the two limits are the throttling evidence on such machines. Linux has no counterpart of the two limits, so on Intel CPUs the kernel's thermal throttling counters take their place: `thermal.throttles` is the number of throttling events during the iteration, and 0 means that no core and no package got throttled. AMD CPUs and virtual machines have no such counters, so the metric is missing on them, not 0.

On Linux, the `cpufreq` files are no measure of the clock: on the Xeon E-2276G servers, `scaling_cur_freq` and `cpuinfo_avg_freq` read either 800 MHz or about 4.7 GHz on every CPU, busy or idle, so their mean says nothing about the cores that run the benchmark. The profiler therefore reads the APERF and MPERF counters of every CPU, which advance only while the CPU runs, at its actual and at its base frequency, so `cpu.mhz` is the clock of the running cores, weighted by their running time: with one core busy, the servers report its 4.9 GHz turbo clock, however many CPUs idle. The package power shows how far the CPU stays from its long-term power limit, `cpu.power.limit` (80 W on these servers), at which the CPU lowers its clock without a thermal throttling event: with the application and PostgreSQL on one server, 16 benchmark threads drew 77 to 79 W, and the clock fell from about 4.4 to 4.25 GHz. Both need root; without it, `cpu.mhz` falls back to the `cpufreq` mean and `cpu.power` is missing. The fork log names the sources in its `Telemetry:` line.

To record the database host telemetry as well:

1. Copy the benchmark bundle to the database host and start the telemetry agent, which listens on port 9500 and prints the metrics that the host provides:
   ```bat
   run-telemetry-agent.bat
   ```
   On Linux, `sh run-telemetry-agent.sh`, or `nice -n 19 sh run-telemetry-agent.sh` so that PostgreSQL wins any contention for the CPU, as root. On an Intel CPU, the first samples must report `thermal.throttles` and `cpu.power`. While it runs, `pidstat` logs the CPU use of every process of the host to `processes-<host>-<time>.txt` next to the script.
2. Allow the application host to reach the agent (PowerShell, as administrator):
   ```powershell
   New-NetFirewallRule -DisplayName "Telemetry agent" -Direction Inbound -Protocol TCP -LocalPort 9500 -RemoteAddress <application-host-ip> -Action Allow
   ```
   On Linux with `ufw`: `sudo ufw allow from <application-host-ip> to any port 9500 proto tcp`.
3. On the application host, point the benchmarks to the agent:
   ```bat
   set TELEMETRY_AGENT_URL=http://<database-host-ip>:9500
   ```
   On Linux: `export TELEMETRY_AGENT_URL=http://<database-host-ip>:9500`.

The profiler reads the agent between iterations, so the network calls are not measured, and the iteration windows come from the agent clock, so the two hosts do not need synchronized clocks. When `TELEMETRY_AGENT_URL` is set but the agent cannot be reached, the run stops before the first fork.

JMH prints the telemetry next to the score of every iteration and in the final table. In the `-rf json` results, the `secondaryMetrics` of each benchmark hold the per-iteration telemetry in `rawData`, aligned with the per-iteration scores of the `primaryMetric`.

## Decomposing the operations by layer

The `LayerProfiler` JMH profiler, which the `run-benchmarks` scripts, the run-matrix driver, and the Maven benchmark tests enable by default, reports where every benchmark operation spends its time, as secondary results of every iteration:

| Metric | Unit | Probe |
|---|---|---|
| `layer.http.client.ms` | ms/op | the benchmark's `RestTemplate`, from creating a request to closing the response, after its body is read |
| `layer.http.server.ms` | ms/op | the first servlet filter, from reading the request to writing the response |
| `layer.service.ms` | ms/op | an advice on the `ForumService` beans, including their transactions |
| `layer.sql.ms`, `layer.commit.ms` | ms/op | datasource-proxy listeners on every statement execution and every commit |
| `layer.http.calls`, `layer.sql.statements`, `layer.commits` | #/op | the same probes; a JDBC batch counts as one statement, i.e., one round trip |
| `layer.http.<method>.client.ms`, `layer.http.<method>.server.ms` | ms/call | the client and server time of one call per HTTP method |
| `layer.throughput` | ops/s | the operations of all threads per second of the measurement window, which the threads divided by the mean response time overstate when a benchmark prepares every invocation in an untimed fixture, as the write benchmarks do |

The client and the server run in the same JVM, so the client time minus the server time is the HTTP transport time. The probes aggregate in memory, record only while the profiler measures an iteration, and pause while the benchmark fixtures reset the data set or load the next post, so the metrics cover only the measured operations. Recording costs too little to measure: in the `layer-overhead` block (`results/hostner-overhead`, ten forks per cell, the servers in the roles of the campaign), the mean response times with and without the profiler were 0.902 and 0.901 ms/op for JDBC MQ-DTO REST at fan-out 0, 2.123 and 2.125 for JDBC Diff-Merge REST, and 6.292 and 6.280 for JPA Multi-Ops REST, ratios whose 95% two-level bootstrap CIs lie between 0.994 and 1.007 (`metrics/hostner/data/layer-overhead.csv`).

### The HTTP client of the REST benchmarks

The REST benchmarks use the JDK `HttpClient` (`BenchmarkRestTemplate`). The default client of `RestTemplate`, which uses `HttpURLConnection`, checks a pooled connection before every streaming `POST` with a read whose socket timeout is 1 ms, and Windows rounds that timeout up to its 15.6 ms timer tick. The layer profiler showed that this added about 9 ms of transport to every `POST`, but about 1 ms to a `PUT` or a `DELETE`, which distorted every comparison between write patterns that use different HTTP methods. With the JDK client, every method costs about 1 to 1.5 ms of transport. To reproduce the original measurements, run with `-jvmArgsAppend -Dbenchmark.http.client=simple`.

## Analyzing a campaign

`analysis/Stats.java` computes the statistics of the article from the results of the run-matrix driver. It needs only a JDK, since the Java source launcher compiles it on the fly, and it reads every `results/<block>/round-<r>/<cell>.json`, one fork per file in the `SampleTime` mode:

```bat
rem Analyze every block of a results folder; the output goes to <results>\analysis
java analysis\Stats.java results\vlad-dell-xps-aiven

rem Run the self-test: the statistics against hand-computed values, and the whole analysis on a synthetic block
java analysis\Stats.java --test
```

- **Comparisons:** two cells of a block are compared when their names differ only in the pattern, `mqdto` against `msjson`, `multiops` against `diffmerge`, or `msjson-string` against `msjson-dto`, or only in the module, `core` against `versioning`, which gives the cost of optimistic locking in the `versioning` block. For every comparison, `comparisons.csv` (and `comparisons.tex`, as LaTeX table rows) gives the change of the mean; the ratio of the means with a 95% confidence interval from a two-level bootstrap, which resamples the forks and then the iterations within each fork; the two-sided Mann-Whitney U test on the iteration means and on the fork means, with Holm-adjusted p-values within each block; and Cliff's delta with a two-level bootstrap 95% confidence interval, A12, and the magnitude by the thresholds of Vargha and Delaney (|delta| of 0.12, 0.28, and 0.42). The `w7` blocks are reported without hypothesis tests.
- **Cells:** `cells.csv` gives the mean of the iteration means with its bootstrap confidence interval, and the p50, p90, p99, and p99.9 of all samples (nearest rank).
- **Layers and telemetry:** `layers.csv` gives the mean of every `layer.*` metric per cell, and the client time minus the server time; `telemetry.csv` summarizes, per block and host (`app`, and `db` if a telemetry agent ran on the database host), the clock, the utilization, the two limits, the thermal throttling events, the maximum temperature, the package power, and the correlations of the clock with the round index and with the scores; `flags.csv` lists the forks whose performance or thermal limit fell below 100%, that got thermally throttled, whose package power averaged at least 95% of its long-term limit in an iteration, whose median clock fell below 90% of the median of the forks of the same cell, or whose CPU utilization was more than twice that median, on either host (the cells of a block load the CPUs differently by design, e.g., REST and service-level writes, or 4 and 16 threads), the reasons of the database host starting with `database host`; `sql-per-statement.csv` gives the median SQL time per statement of every fork, which approximates the round-trip time for single-statement operations.
- **Checks:** `checks.txt` reports unreadable files, missing rounds, units that differ within a cell, unpaired cells, and any fork whose merged sample mean differs from the score JMH reported; `environment.txt` lists the JVM and JMH settings found in the results. The bootstrap uses a fixed seed, so a rerun gives the same numbers.

Run it when no benchmark is running on the same machine, since it competes for the CPU.

### The charts of the article

`metrics/hostner` regenerates every chart of the article from the raw results of the Hostner campaign (`results/hostner-pair`), the role swap (`results/hostner-swap`), and the pilot (`results/hostner-pilot`), and the numbers of the layer-profiler overhead check from `results/hostner-overhead`. It needs Python 3, a JDK, and Gnuplot 5.4 or later with the `pdfcairo` terminal:

```sh
# Builds the chart data (metrics/hostner/data) with Stats.java and renders every chart to metrics/hostner/*.pdf;
# the optional argument is a folder to copy the PDF files to, as hostner-<chart>.pdf
sh metrics/hostner/make-charts.sh
```

- `make-chart-data.py` runs `Stats.java` on each results folder, compares the same cells across two runs (the pilot against the campaign, SF10 against SF1, the role swap against the campaign, each cell of the overhead check with the layer profiler against its twin without it and against the campaign) with `Stats.java` as well, and writes one CSV file per chart, plus `layer-overhead.csv`, which no chart plots; every error bar is the 95% two-level bootstrap confidence interval of the mean or of the ratio of the means.
- One Gnuplot script per chart, all sharing `style.gp`: `rq1-response-time`, `rq1-layers`, `rq2-writes`, `rq5-threads`, `typed-msjson`, `versioning`, `robustness`, and `telemetry`.

## Code-quality metrics

The static code-quality metrics of the article (RQ3 and RQ4) come from the [MetricsTree](https://github.com/b333vv/metricstree) IntelliJ IDEA plugin, version 2026.0.0, applied to the 11 modules of `quality-metrics`. Each module holds the code of one variant only, `read_<stack>_<mq_dto|ms_json>` or `write_<stack>_<multi_ops|diff_merge>`, so the package metrics of a module measure that variant. `metrics/CodeQuality/ReadPath` and `metrics/CodeQuality/WritePath` hold the package metrics of every module, and the `-package-agr.csv` files hold the metrics that the article reports, one row per metric and one column per package.

`quality-metrics/run-metricstree.bat` (or `run-metricstree.sh`) regenerates them without the IDE user interface and compares them with these files:

```bat
rem Every module, or only the modules given as arguments
quality-metrics\run-metricstree.bat
quality-metrics\run-metricstree.bat write_jpa_diff_merge

rem Use an installed IntelliJ IDEA 2026.2.4 instead of downloading it
set "IDEA_HOME=C:\Program Files\JetBrains\IntelliJ IDEA"
quality-metrics\run-metricstree.bat
```

- **Runner:** for each module, the script copies the compile-scope dependencies with Maven, and the Gradle project `quality-metrics/metricstree-runner` opens the module sources (`src/main/java`) with these jars and the JDK of `JAVA_HOME` in a headless IntelliJ IDEA 2026.2.4, where it calls the code of the MetricsTree action "Export Package Metrics to CSV". It writes `quality-metrics/target/metricstree/<module>-package.csv`, plus `<module>-package-cc.csv` with the sum of the cyclomatic complexities of each package, which the export omits.
- **Comparison:** `compare-metricstree.py` compares every value of the `-package-agr.csv` files with the export, within the four decimals that MetricsTree prints, and exits with status 1 if one differs. It also writes `<module>-package-mi.csv`, which sets the package Maintainability Index of MetricsTree, which takes the logarithm of the cyclomatic complexity, next to the one of Coleman et al., which does not.
- **Requirements:** JDK 21 in `JAVA_HOME`, Maven, and Python 3; the runner itself runs on JDK 25, which Gradle takes from the installed JDKs or downloads. The first run downloads Gradle, the IntelliJ Platform test framework, MetricsTree, and IntelliJ IDEA (about 1.6 GB) unless `IDEA_HOME` is set; a module then takes about 20 seconds.

MetricsTree counts the lines of code of a package (PLOC) as the non-blank lines of its methods and constructors, from the Javadoc comment of a method to its closing brace, so the comments of a method would count as code. No method of the `quality-metrics` modules carries a comment (the `EntityMerger` of `core` keeps its Javadoc, the copy in `write_jpa_diff_merge` does not), so their PLOC counts lines of code only.

## Requirements

| Tool | Version |
|---|---|
| Java | 21 (preview features enabled) |
| Maven | 3.9+ |
| Docker | required for Testcontainers (PostgreSQL) |

---

## Build

```bash
# Compile and package (skips tests)
mvn clean package -DskipTests

# Full build including all tests
mvn clean package
```

MapStruct generates sources during compilation under `target/generated-sources/annotations`. Make sure annotation processing is enabled in your IDE.

---

## Running tests

```bash
# Run all tests
mvn clean test

# Run only the JPA integration test
mvn -Dtest=JpaTest test
```

### What the tests verify

`SpringDataJPADTO2EntityTest` covers two scenarios:

1. **`testDtoToEntitySync`** — loads a `PostDTO`, modifies its title, upvotes comments, removes the `"JPA"` tag, adds a `"Jakarta Persistence"` tag, adds a new child comment, then calls `ForumService.updatePost()`. Afterwards it reloads the DTO and asserts it equals the in-memory version — proving the full round-trip works.

2. **`testRemoveParentPostComment`** — removes a parent comment from the DTO and verifies that after the update its child comments are also removed (cascade-by-diff behaviour).

---

## Testcontainers integration

The test suite does **not** require a pre-installed PostgreSQL instance. Instead it uses [Testcontainers](https://testcontainers.com/) to spin up a real database automatically.

### How it works

The `Database` enum (`src/test/.../util/database/Database.java`) defines the available database back-ends. The only currently configured back-end is `POSTGRESQL`, which starts a `PostgreSQLContainer` using the `postgres:15.3` Docker image:

```java
protected JdbcDatabaseContainer newJdbcDatabaseContainer() {
    return new PostgreSQLContainer("postgres:15.3");
}
```

`AbstractContainerDataSourceProvider` attempts to connect to a locally running PostgreSQL first. If the connection fails (e.g. no local server is running), it calls `Database.initContainer()`, which:

1. Creates and starts the container with `.withReuse(true)` (so the same container is reused across test runs in the same Docker session).
2. Mounts `/testtmpfs` as a tmpfs volume for faster I/O.
3. Sets the database name to the module's database, `dto2entity` or `dto2entity_versioning`.

The JDBC URL used by the Spring `DataSource` is then taken directly from the running container:

```java
public String url() {
    JdbcDatabaseContainer container = database().getContainer();
    return container != null ? container.getJdbcUrl() : defaultJdbcUrl();
}
```

This means tests are fully self-contained — just have Docker running and execute `mvn test`.

---

## Tech stack

- **Spring Boot 4 and Spring Framework 7** / Spring Data JPA
- **Hibernate 7.2.12 / Jakarta Persistence**
- **MapStruct 1.6** — compile-time DTO ↔ entity mapping
- **PostgreSQL 15** (via Testcontainers)
- **Testcontainers 2** — zero-config database provisioning
- **Hypersistence Utils** — TSID-based entity identifiers
- **JMH** — micro-benchmark support