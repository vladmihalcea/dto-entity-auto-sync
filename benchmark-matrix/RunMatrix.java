import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Runs a block of benchmark cells in randomized interleaved rounds: every round runs one JMH fork
 * of every cell, in a random order that the seed of the block fixes, so a slow drift of the machines
 * spreads over all cells instead of biasing the cells that happen to run last.
 * <p>
 * Every fork writes its own JSON result and log, so the driver can stop at any time and continue
 * where it stopped, losing at most the fork in progress:
 * <pre>
 * results/&lt;block&gt;/session.properties        seed and digest of the cell file
 * results/&lt;block&gt;/forks.csv                 start, end, duration, and status of every fork
 * results/&lt;block&gt;/window-&lt;time&gt;.txt         environment and summary of every run window
 * results/&lt;block&gt;/processes-&lt;time&gt;.csv      CPU use of every busy process during the window (on Windows)
 * results/&lt;block&gt;/round-&lt;r&gt;/&lt;cell&gt;.json    JMH result of the fork
 * results/&lt;block&gt;/round-&lt;r&gt;/&lt;cell&gt;.log     JMH output of the fork, including the warm-up iterations
 * </pre>
 * The cell file lists one cell per line, {@code <name> <module> <rounds> <benchmark regex> [JMH options]},
 * plus the optional {@code @jvm} and {@code @jmh} lines with the JVM and JMH options of all cells.
 * <p>
 * Usage, with a JDK, from the folder that holds the {@code core} and {@code versioning} benchmark bundles:
 * <pre>
 * java RunMatrix.java [run] &lt;cells file&gt; [options]
 * java RunMatrix.java status &lt;cells file&gt; [--results &lt;dir&gt;]
 * java RunMatrix.java stop &lt;cells file&gt; [--results &lt;dir&gt;]
 * java RunMatrix.java check &lt;cells file&gt; [--bundles &lt;dir&gt;]
 * </pre>
 *
 * @author Vlad Mihalcea
 */
public class RunMatrix {

    private static final String USAGE = """
        Usage:
          run-matrix [run] <cells file> [--db-host <host>] [--db-port <port>] [--agent <url>]
                     [--until <HH:mm> | --hours <hours>] [--bundles <dir>] [--results <dir>]
                     [--seed <seed>] [--ping-count <count>]
          run-matrix status <cells file> [--results <dir>]
          run-matrix stop <cells file> [--results <dir>]
          run-matrix check <cells file> [--bundles <dir>]""";

    /**
     * The database of every module, as in the PostgreSQLDataSourceProvider of each module.
     */
    private static final Map<String, String> DATABASES = Map.of(
        "core", "dto2entity",
        "versioning", "dto2entity_versioning"
    );

    private static final List<String> PROFILERS = List.of(
        "com.vladmihalcea.phd.common.telemetry.TelemetryProfiler",
        "com.vladmihalcea.phd.common.timing.LayerProfiler"
    );

    /**
     * The JMH forks inherit the JVM options of the JMH process, as with the run-benchmarks scripts:
     * a fixed heap, whose pages {@code AlwaysPreTouch} commits at startup, so no fork pays page faults
     * for its heap while it warms up.
     */
    private static final List<String> DEFAULT_JVM_OPTIONS = List.of("-Xms4g", "-Xmx4g", "-XX:+AlwaysPreTouch");

    private static final long DEFAULT_ESTIMATE_SECONDS = 600;

    private static final int MAX_CONSECUTIVE_FAILURES = 3;

    private static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().startsWith("windows");

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private static volatile Process currentFork;

    record Cell(String name, String module, int rounds, String benchmark, List<String> options) {
    }

    record Block(String name, List<String> jvmOptions, List<String> jmhOptions, List<Cell> cells, String digest) {

        int maxRounds() {
            return cells.stream().mapToInt(Cell::rounds).max().orElse(0);
        }
    }

    static final class Options {
        String command = "run";
        Path cellsFile;
        Path bundles = Path.of(".");
        Path results = Path.of("results");
        String dbHost = "localhost";
        int dbPort = 5432;
        String agent = System.getenv("TELEMETRY_AGENT_URL");
        Instant deadline;
        Long seed;
        int pingCount = 30;
    }

    public static void main(String[] args) {
        try {
            Options options = parseOptions(args);
            Block block = parseBlock(options.cellsFile);
            Path blockDir = options.results.resolve(block.name());
            switch (options.command) {
                case "stop" -> stop(blockDir, block);
                case "status" -> status(blockDir, block);
                case "check" -> {
                    verifyCells(block, options);
                    System.out.printf("All %d cells of block %s match exactly one benchmark of their module.%n", block.cells().size(), block.name());
                }
                default -> {
                    if (!run(blockDir, block, options, args)) {
                        System.exit(1);
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(USAGE);
            System.exit(2);
        } catch (IllegalStateException | IOException | UncheckedIOException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.exit(130);
        }
    }

    // -------------------------------------------------------------------------
    // Commands
    // -------------------------------------------------------------------------

    /**
     * @return {@code false} if the window stopped because of consecutive failed forks
     */
    private static boolean run(Path blockDir, Block block, Options options, String[] args) throws IOException, InterruptedException {
        Files.createDirectories(blockDir);
        // A second driver on the same block would run the same forks
        FileChannel lockChannel = FileChannel.open(blockDir.resolve("driver.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock driverLock = lockChannel.tryLock();
        if (driverLock == null) {
            throw new IllegalStateException("Another run-matrix process is running the block " + block.name());
        }
        Path stopFile = blockDir.resolve("STOP");
        Files.deleteIfExists(stopFile);

        verifyCells(block, options);
        checkAgent(options.agent);
        long seed = openSession(blockDir, block, options.seed);
        ForkLog forkLog = new ForkLog(blockDir.resolve("forks.csv"));
        if (System.getenv("JDBC_URL") != null) {
            log("Ignoring JDBC_URL: every fork connects to the database of its module on %s", options.dbHost);
        }
        Path windowFile = startWindow(blockDir, block, seed, options, args);
        Runtime.getRuntime().addShutdownHook(new Thread(RunMatrix::stopCurrentFork));

        log("Block %s: %d cells, %d rounds, seed %d, %d forks left, about %s",
            block.name(), block.cells().size(), block.maxRounds(), seed,
            remainingForks(blockDir, block), formatDuration(remainingSeconds(blockDir, block, forkLog)));

        int succeeded = 0;
        int failed = 0;
        int consecutiveFailures = 0;
        String stopReason = "all forks of the block are done";
        rounds:
        for (int round = 1; round <= block.maxRounds(); round++) {
            int currentRound = round;
            List<Cell> pending = roundOrder(block, seed, round).stream()
                .filter(cell -> !isDone(blockDir, currentRound, cell))
                .toList();
            if (pending.isEmpty()) {
                continue;
            }
            Path roundDir = roundDir(blockDir, round);
            Files.createDirectories(roundDir);
            ping(options, roundDir);
            for (Cell cell : pending) {
                if (Files.exists(stopFile)) {
                    stopReason = "a stop was requested";
                    break rounds;
                }
                long estimate = forkLog.estimate(cell);
                if (options.deadline != null && Instant.now().plusSeconds(estimate).isAfter(options.deadline)) {
                    stopReason = String.format("the next fork, %s, would end after the deadline", cell.name());
                    break rounds;
                }
                LocalDateTime start = LocalDateTime.now();
                boolean ok = runFork(block, cell, roundDir, options);
                LocalDateTime end = LocalDateTime.now();
                forkLog.record(round, cell, start, end, ok);
                if (ok) {
                    succeeded++;
                    consecutiveFailures = 0;
                } else {
                    failed++;
                    consecutiveFailures++;
                }
                log("round %d/%d  %-32s %-6s %7s   %d forks left, about %s",
                    round, block.maxRounds(), cell.name(), ok ? "ok" : "FAILED",
                    formatDuration(Duration.between(start, end).toSeconds()),
                    remainingForks(blockDir, block), formatDuration(remainingSeconds(blockDir, block, forkLog)));
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    stopReason = String.format("%d consecutive forks failed; see the *.failed-*.log files in %s", consecutiveFailures, roundDir);
                    break rounds;
                }
            }
        }

        ping(options, blockDir);
        String summary = String.format(
            "end: %s%nstopped because %s%nforks run in this window: %d ok, %d failed%nforks left in the block: %d, about %s%n",
            LocalDateTime.now().format(TIMESTAMP), stopReason, succeeded, failed,
            remainingForks(blockDir, block), formatDuration(remainingSeconds(blockDir, block, forkLog))
        );
        Files.writeString(windowFile, summary, StandardOpenOption.APPEND);
        log("Stopped because %s: %d forks ok and %d failed in this window, %d forks left",
            stopReason, succeeded, failed, remainingForks(blockDir, block));
        Files.deleteIfExists(stopFile);
        driverLock.release();
        lockChannel.close();
        return consecutiveFailures < MAX_CONSECUTIVE_FAILURES;
    }

    private static void status(Path blockDir, Block block) throws IOException {
        if (!Files.exists(blockDir.resolve("session.properties"))) {
            System.out.printf("Block %s has not started yet: %d cells, %d forks%n",
                block.name(), block.cells().size(), remainingForks(blockDir, block));
            return;
        }
        ForkLog forkLog = new ForkLog(blockDir.resolve("forks.csv"));
        System.out.printf("Block %s, seed %d, results in %s%n", block.name(), readSeed(blockDir), blockDir);
        System.out.printf("%-32s %-11s %7s %7s %9s%n", "cell", "module", "done", "failed", "duration");
        for (Cell cell : block.cells()) {
            System.out.printf("%-32s %-11s %7s %7d %9s%n",
                cell.name(), cell.module(), doneForks(blockDir, cell) + "/" + cell.rounds(),
                forkLog.failures(cell), forkLog.hasDurations(cell) ? formatDuration(forkLog.estimate(cell)) : "-");
        }
        System.out.printf("%d forks left, about %s%n", remainingForks(blockDir, block), formatDuration(remainingSeconds(blockDir, block, forkLog)));
    }

    private static void stop(Path blockDir, Block block) throws IOException {
        Files.createDirectories(blockDir);
        Files.writeString(blockDir.resolve("STOP"), LocalDateTime.now().format(TIMESTAMP) + "\n");
        System.out.printf("The run-matrix driver of block %s stops after its current fork.%n", block.name());
    }

    // -------------------------------------------------------------------------
    // Forks
    // -------------------------------------------------------------------------

    /**
     * Runs one JMH fork of the cell as a separate JMH process, since the core and versioning modules
     * have classes with the same names and cannot share a JVM.
     *
     * @return {@code true} if the fork produced a result
     */
    private static boolean runFork(Block block, Cell cell, Path roundDir, Options options) throws IOException, InterruptedException {
        Path bundle = bundle(options.bundles, cell.module());
        Path result = roundDir.resolve(cell.name() + ".json");
        Path partialResult = roundDir.resolve(cell.name() + ".json.part");
        Path log = roundDir.resolve(cell.name() + ".log");

        List<String> command = new ArrayList<>();
        command.add(javaExecutable());
        command.addAll(block.jvmOptions());
        command.add("-Djmh.separateClasspathJAR=true");
        command.add("-cp");
        command.add(classpath(bundle));
        command.add("org.openjdk.jmh.Main");
        command.add(cell.benchmark());
        PROFILERS.forEach(profiler -> command.addAll(List.of("-prof", profiler)));
        command.addAll(block.jmhOptions());
        command.addAll(cell.options());
        command.addAll(List.of("-f", "1", "-foe", "true", "-rf", "json", "-rff", partialResult.toAbsolutePath().toString()));

        ProcessBuilder builder = new ProcessBuilder(command)
            .directory(bundle.toFile())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile());
        builder.environment().put("JDBC_URL", jdbcUrl(options, cell.module()));
        if (options.agent != null && !options.agent.isBlank()) {
            builder.environment().put("TELEMETRY_AGENT_URL", options.agent);
        }

        Files.deleteIfExists(partialResult);
        Process process = builder.start();
        currentFork = process;
        int exitCode;
        try {
            exitCode = process.waitFor();
        } finally {
            currentFork = null;
        }

        boolean ok = exitCode == 0 &&
            Files.exists(partialResult) &&
            Files.readString(partialResult).contains("\"primaryMetric\"");
        if (ok) {
            // The result appears only once it is complete, which marks the fork as done
            Files.move(partialResult, result, StandardCopyOption.ATOMIC_MOVE);
        } else {
            Files.deleteIfExists(partialResult);
            Files.move(log, roundDir.resolve(cell.name() + ".failed-" + LocalDateTime.now().format(FILE_TIMESTAMP) + ".log"));
        }
        return ok;
    }

    private static void stopCurrentFork() {
        Process fork = currentFork;
        if (fork != null && fork.isAlive()) {
            System.out.println("Interrupted: the current fork runs again on the next start.");
            fork.descendants().forEach(ProcessHandle::destroy);
            fork.destroy();
        }
    }

    /**
     * Every round runs one fork of every cell that has that many rounds, in an order that only
     * the seed and the round number determine, so a restarted driver continues in the same order.
     */
    static List<Cell> roundOrder(Block block, long seed, int round) {
        List<Cell> cells = block.cells().stream()
            .filter(cell -> cell.rounds() >= round)
            .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(cells, new Random(seed * 1_000_003L + round));
        return cells;
    }

    private static Path roundDir(Path blockDir, int round) {
        return blockDir.resolve(String.format("round-%02d", round));
    }

    private static boolean isDone(Path blockDir, int round, Cell cell) {
        return Files.exists(roundDir(blockDir, round).resolve(cell.name() + ".json"));
    }

    private static int doneForks(Path blockDir, Cell cell) {
        int done = 0;
        for (int round = 1; round <= cell.rounds(); round++) {
            if (isDone(blockDir, round, cell)) {
                done++;
            }
        }
        return done;
    }

    private static int remainingForks(Path blockDir, Block block) {
        return block.cells().stream().mapToInt(cell -> cell.rounds() - doneForks(blockDir, cell)).sum();
    }

    private static long remainingSeconds(Path blockDir, Block block, ForkLog forkLog) {
        return block.cells().stream()
            .mapToLong(cell -> (cell.rounds() - doneForks(blockDir, cell)) * forkLog.estimate(cell))
            .sum();
    }

    // -------------------------------------------------------------------------
    // Cell file and session
    // -------------------------------------------------------------------------

    static Block parseBlock(Path file) throws IOException {
        List<String> jvmOptions = DEFAULT_JVM_OPTIONS;
        List<String> jmhOptions = List.of();
        List<Cell> cells = new ArrayList<>();
        Set<String> names = new HashSet<>();
        StringBuilder content = new StringBuilder();
        int lineNumber = 0;
        for (String line : Files.readAllLines(file)) {
            lineNumber++;
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            List<String> tokens = List.of(line.split("\\s+"));
            content.append(String.join(" ", tokens)).append('\n');
            switch (tokens.getFirst()) {
                case "@jvm" -> jvmOptions = tokens.subList(1, tokens.size());
                case "@jmh" -> jmhOptions = tokens.subList(1, tokens.size());
                default -> {
                    if (tokens.size() < 4) {
                        throw new IllegalArgumentException(String.format(
                            "%s:%d: expected <name> <module> <rounds> <benchmark regex> [JMH options]", file, lineNumber));
                    }
                    String name = tokens.get(0);
                    String module = tokens.get(1);
                    if (!name.matches("[A-Za-z0-9._-]+") || !names.add(name)) {
                        throw new IllegalArgumentException(String.format("%s:%d: invalid or duplicate cell name: %s", file, lineNumber, name));
                    }
                    if (!DATABASES.containsKey(module)) {
                        throw new IllegalArgumentException(String.format("%s:%d: unknown module %s, expected one of %s", file, lineNumber, module, DATABASES.keySet()));
                    }
                    cells.add(new Cell(name, module, Integer.parseInt(tokens.get(2)), tokens.get(3), tokens.subList(4, tokens.size())));
                }
            }
        }
        if (cells.isEmpty()) {
            throw new IllegalArgumentException(file + " defines no cells");
        }
        String blockName = file.getFileName().toString().replaceFirst("\\.[^.]*$", "");
        return new Block(blockName, jvmOptions, jmhOptions, cells, sha256(content.toString()));
    }

    /**
     * Checks that every cell matches exactly one benchmark of its module, since a regex that matches
     * several benchmarks would run all of them in one fork process.
     */
    private static void verifyCells(Block block, Options options) {
        Map<String, List<String>> benchmarks = new HashMap<>();
        for (Cell cell : block.cells()) {
            List<String> moduleBenchmarks = benchmarks.computeIfAbsent(cell.module(), module -> listBenchmarks(bundle(options.bundles, module)));
            Pattern pattern = Pattern.compile(cell.benchmark());
            List<String> matches = moduleBenchmarks.stream().filter(name -> pattern.matcher(name).find()).toList();
            if (matches.size() != 1) {
                throw new IllegalStateException(String.format(
                    "The cell %s must match exactly one benchmark of the %s module, but it matches %d: %s",
                    cell.name(), cell.module(), matches.size(), matches));
            }
        }
    }

    private static List<String> listBenchmarks(Path bundle) {
        try {
            Process process = new ProcessBuilder(javaExecutable(), "-Djmh.separateClasspathJAR=true", "-cp", classpath(bundle), "org.openjdk.jmh.Main", "-l")
                .directory(bundle.toFile())
                .redirectErrorStream(true)
                .start();
            List<String> lines = process.inputReader().lines().map(String::strip).toList();
            if (process.waitFor() != 0) {
                throw new IllegalStateException("Cannot list the benchmarks of " + bundle + ": " + String.join("\n", lines));
            }
            return lines.stream().filter(line -> line.matches("[\\w$]+(\\.[\\w$]+)+")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * Keeps the seed of the block, and refuses a changed cell file, which would change the order of the remaining forks.
     */
    private static long openSession(Path blockDir, Block block, Long requestedSeed) throws IOException {
        Path file = blockDir.resolve("session.properties");
        if (Files.exists(file)) {
            Properties properties = readProperties(file);
            if (!block.digest().equals(properties.getProperty("cells.sha256"))) {
                throw new IllegalStateException(String.format(
                    "The cell file of block %s changed since the block started, which would change the order of the remaining forks. " +
                    "Restore the cell file, or use another --results directory for the changed block.", block.name()));
            }
            long seed = Long.parseLong(properties.getProperty("seed"));
            if (requestedSeed != null && requestedSeed != seed) {
                throw new IllegalStateException(String.format("Block %s started with seed %d, not %d", block.name(), seed, requestedSeed));
            }
            return seed;
        }
        long seed = requestedSeed != null ? requestedSeed : ThreadLocalRandom.current().nextInt(1, 1_000_000_000);
        Properties properties = new Properties();
        properties.setProperty("seed", String.valueOf(seed));
        properties.setProperty("cells.sha256", block.digest());
        properties.setProperty("created", LocalDateTime.now().format(TIMESTAMP));
        try (var writer = Files.newBufferedWriter(file)) {
            properties.store(writer, "run-matrix session of block " + block.name());
        }
        return seed;
    }

    private static long readSeed(Path blockDir) throws IOException {
        return Long.parseLong(readProperties(blockDir.resolve("session.properties")).getProperty("seed"));
    }

    private static Properties readProperties(Path file) throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        }
        return properties;
    }

    /**
     * Records every fork, so the driver can estimate the remaining time from the measured fork durations.
     */
    static final class ForkLog {

        private static final String HEADER = "round,cell,module,start,end,seconds,status";

        private final Path file;
        private final Map<String, List<Long>> durations = new HashMap<>();
        private final Map<String, Integer> failures = new HashMap<>();

        ForkLog(Path file) throws IOException {
            this.file = file;
            if (Files.exists(file)) {
                for (String line : Files.readAllLines(file)) {
                    String[] fields = line.split(",");
                    if (line.equals(HEADER) || fields.length != 7) {
                        continue;
                    }
                    add(fields[1], Long.parseLong(fields[5]), "ok".equals(fields[6]));
                }
            }
        }

        void record(int round, Cell cell, LocalDateTime start, LocalDateTime end, boolean ok) throws IOException {
            long seconds = Duration.between(start, end).toSeconds();
            if (!Files.exists(file)) {
                Files.writeString(file, HEADER + "\n");
            }
            Files.writeString(
                file,
                String.join(",", String.valueOf(round), cell.name(), cell.module(), start.format(TIMESTAMP), end.format(TIMESTAMP),
                    String.valueOf(seconds), ok ? "ok" : "failed") + "\n",
                StandardOpenOption.APPEND
            );
            add(cell.name(), seconds, ok);
        }

        private void add(String cell, long seconds, boolean ok) {
            if (ok) {
                durations.computeIfAbsent(cell, name -> new ArrayList<>()).add(seconds);
            } else {
                failures.merge(cell, 1, Integer::sum);
            }
        }

        boolean hasDurations(Cell cell) {
            return durations.containsKey(cell.name());
        }

        int failures(Cell cell) {
            return failures.getOrDefault(cell.name(), 0);
        }

        /**
         * @return the mean duration of the cell's forks, or of all forks if the cell has not run yet
         */
        long estimate(Cell cell) {
            List<Long> cellDurations = durations.get(cell.name());
            if (cellDurations != null) {
                return average(cellDurations);
            }
            List<Long> allDurations = durations.values().stream().flatMap(List::stream).toList();
            return allDurations.isEmpty() ? DEFAULT_ESTIMATE_SECONDS : average(allDurations);
        }

        private static long average(List<Long> values) {
            return Math.round(values.stream().mapToLong(Long::longValue).average().orElse(DEFAULT_ESTIMATE_SECONDS));
        }
    }

    // -------------------------------------------------------------------------
    // Environment
    // -------------------------------------------------------------------------

    /**
     * Records the environment of the run window, since a block can span several windows.
     */
    private static Path startWindow(Path blockDir, Block block, long seed, Options options, String[] args) throws IOException, InterruptedException {
        String time = LocalDateTime.now().format(FILE_TIMESTAMP);
        Path file = blockDir.resolve("window-" + time + ".txt");
        StringBuilder info = new StringBuilder()
            .append("start: ").append(LocalDateTime.now().format(TIMESTAMP)).append('\n')
            .append("arguments: ").append(String.join(" ", args)).append('\n')
            .append("block: ").append(block.name()).append(", seed ").append(seed).append('\n')
            .append("java: ").append(System.getProperty("java.vendor")).append(' ').append(System.getProperty("java.runtime.version")).append('\n')
            .append("os: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version")).append('\n')
            .append("application host: ").append(hostName()).append('\n')
            .append("database host: ").append(options.dbHost).append(':').append(options.dbPort).append('\n')
            .append("benchmark database: ").append(benchmarkDatabase()).append('\n')
            .append("telemetry agent: ").append(options.agent != null ? options.agent : "none").append('\n')
            .append("deadline: ").append(options.deadline != null ? LocalDateTime.ofInstant(options.deadline, ZoneId.systemDefault()).format(TIMESTAMP) : "none").append('\n');
        if (WINDOWS) {
            info.append("power scheme: ").append(output("powercfg", "/getactivescheme")).append('\n');
        } else {
            info.append("power settings: ").append(linuxPowerSettings()).append('\n');
        }
        info.append("process log: ").append(startProcessLog(blockDir, time)).append('\n');
        Files.writeString(file, info);
        return file;
    }

    /**
     * @return the database of the forks, which the {@code BENCHMARK_DATABASE} environment variable or the
     * {@code database} entry of {@code ~/.dto2entity/benchmark.properties}, the default of this machine, can switch
     * from the PostgreSQL server on the database host to another one, e.g., {@code AIVEN}
     */
    private static String benchmarkDatabase() throws IOException {
        String database = System.getenv("BENCHMARK_DATABASE");
        String source = "BENCHMARK_DATABASE";
        Path machineSettings = Path.of(System.getProperty("user.home"), ".dto2entity", "benchmark.properties");
        if ((database == null || database.isBlank()) && Files.exists(machineSettings)) {
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(machineSettings)) {
                properties.load(reader);
            }
            database = properties.getProperty("database");
            source = "the default of this machine in " + machineSettings;
        }
        if (database == null || database.isBlank() || database.trim().equalsIgnoreCase("POSTGRESQL")) {
            return "POSTGRESQL, on the database host";
        }
        return database.trim().toUpperCase() + ", from " + source + ", which ignores the database host";
    }

    /**
     * @return the CPU power settings of a Linux host, which {@code setup-server.sh} sets: the frequency governor
     * and the energy performance preference of the first CPU, whether turbo is off, and the idle states
     * that the CPUs may enter
     */
    static String linuxPowerSettings() {
        Path cpu = Path.of("/sys/devices/system/cpu");
        List<String> idleStates = new ArrayList<>();
        try (Stream<Path> states = Files.list(cpu.resolve("cpu0/cpuidle"))) {
            for (Path state : states.filter(s -> s.getFileName().toString().startsWith("state"))
                .sorted(Comparator.comparingInt(s -> Integer.parseInt(s.getFileName().toString().substring("state".length()))))
                .toList()) {
                if (sysfs(state.resolve("disable")).equals("0")) {
                    idleStates.add(sysfs(state.resolve("name")));
                }
            }
        } catch (IOException | RuntimeException e) {
            idleStates.add("unknown");
        }
        return String.format("governor %s, energy performance preference %s, no_turbo %s, enabled idle states %s",
            sysfs(cpu.resolve("cpu0/cpufreq/scaling_governor")),
            sysfs(cpu.resolve("cpu0/cpufreq/energy_performance_preference")),
            sysfs(cpu.resolve("intel_pstate/no_turbo")),
            String.join(" ", idleStates));
    }

    private static String sysfs(Path file) {
        try {
            return Files.readString(file).trim();
        } catch (IOException e) {
            return "unknown";
        }
    }

    /**
     * Starts the per-process CPU log of the window, which shows whether anything else ran on the machine,
     * and stops it when the driver exits: on Windows, {@code process-log.ps1} next to this file, and on Linux,
     * {@code pidstat} of the {@code sysstat} package, both every 10 s.
     *
     * @return the name of the log file, or {@code none}
     */
    private static String startProcessLog(Path blockDir, String time) throws IOException {
        if (!WINDOWS) {
            return startPidstat(blockDir, time);
        }
        String source = System.getProperty("jdk.launcher.sourcefile");
        Path script = source != null ? Path.of(source).toAbsolutePath().resolveSibling("process-log.ps1") : null;
        if (!WINDOWS || script == null || !Files.exists(script)) {
            return "none";
        }
        Path file = blockDir.resolve("processes-" + time + ".csv");
        new ProcessBuilder(
            "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString(),
            "-OutFile", file.toAbsolutePath().toString(), "-ParentPid", String.valueOf(ProcessHandle.current().pid())
        )
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start();
        return file.getFileName().toString();
    }

    /**
     * Starts {@code pidstat}, which prints the CPU use of every process that ran during each 10 s interval,
     * with its command line, from a shell that stops it once the driver process has exited.
     */
    private static String startPidstat(Path blockDir, String time) throws IOException {
        boolean installed = Arrays.stream(System.getenv().getOrDefault("PATH", "").split(File.pathSeparator))
            .anyMatch(directory -> Files.isExecutable(Path.of(directory, "pidstat")));
        if (!installed) {
            return "none (pidstat not found, install the sysstat package)";
        }
        Path file = blockDir.resolve("processes-" + time + ".txt");
        String script = String.format(
            "pidstat -u -h -l 10 > '%s' & logger=$!; while kill -0 %d 2>/dev/null; do sleep 10; done; kill $logger",
            file.toAbsolutePath(), ProcessHandle.current().pid());
        new ProcessBuilder("sh", "-c", script)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start();
        return file.getFileName().toString();
    }

    /**
     * Records the round-trip time to the database host, before every round and at the end of the window.
     */
    private static void ping(Options options, Path dir) throws IOException, InterruptedException {
        if (options.pingCount <= 0 || isLocal(options.dbHost)) {
            return;
        }
        List<String> command = List.of("ping", WINDOWS ? "-n" : "-c", String.valueOf(options.pingCount), options.dbHost);
        new ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(dir.resolve("ping-" + LocalDateTime.now().format(FILE_TIMESTAMP) + ".txt").toFile())
            .start()
            .waitFor();
    }

    private static void checkAgent(String agent) {
        if (agent == null || agent.isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(agent.endsWith("/") ? agent + "now" : agent + "/now");
            HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()
                .send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP status " + response.statusCode());
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot reach the telemetry agent at " + agent + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static Path bundle(Path bundles, String module) {
        for (Path candidate : List.of(bundles.resolve(module).resolve("target").resolve("benchmark-bundle"), bundles.resolve(module))) {
            if (Files.isDirectory(candidate.resolve("test-classes")) && Files.isDirectory(candidate.resolve("lib"))) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        throw new IllegalStateException(String.format(
            "Cannot find the %s benchmark bundle in %s, which needs a %s or %s/target/benchmark-bundle folder (mvn -P benchmark-bundle package)",
            module, bundles.toAbsolutePath().normalize(), module, module));
    }

    private static String classpath(Path bundle) {
        return String.join(File.pathSeparator,
            bundle.resolve("classes").toString(),
            bundle.resolve("test-classes").toString(),
            bundle.resolve("lib") + File.separator + "*");
    }

    private static String jdbcUrl(Options options, String module) {
        return String.format("jdbc:postgresql://%s:%d/%s", options.dbHost, options.dbPort, DATABASES.get(module));
    }

    /**
     * The forks run on the same Java runtime as the driver.
     */
    private static String javaExecutable() {
        return ProcessHandle.current().info().command()
            .orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    }

    private static boolean isLocal(String host) {
        return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1");
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    private static String output(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        process.waitFor();
        return output;
    }

    // -------------------------------------------------------------------------
    // Options and formatting
    // -------------------------------------------------------------------------

    static Options parseOptions(String[] args) {
        Options options = new Options();
        List<String> positional = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--bundles" -> options.bundles = Path.of(value(args, ++i, arg));
                case "--results" -> options.results = Path.of(value(args, ++i, arg));
                case "--db-host" -> options.dbHost = value(args, ++i, arg);
                case "--db-port" -> options.dbPort = Integer.parseInt(value(args, ++i, arg));
                case "--agent" -> options.agent = value(args, ++i, arg);
                case "--until" -> options.deadline = until(value(args, ++i, arg));
                case "--hours" -> options.deadline = Instant.now().plusSeconds(Math.round(Double.parseDouble(value(args, ++i, arg)) * 3600));
                case "--seed" -> options.seed = Long.parseLong(value(args, ++i, arg));
                case "--ping-count" -> options.pingCount = Integer.parseInt(value(args, ++i, arg));
                default -> {
                    if (arg.startsWith("--")) {
                        throw new IllegalArgumentException("Unknown option: " + arg);
                    }
                    positional.add(arg);
                }
            }
        }
        if (!positional.isEmpty() && List.of("run", "status", "stop", "check").contains(positional.getFirst())) {
            options.command = positional.removeFirst();
        }
        if (positional.size() != 1) {
            throw new IllegalArgumentException("Expected one cell file, got: " + positional);
        }
        options.cellsFile = Path.of(positional.getFirst());
        return options;
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }

    /**
     * @return the next occurrence of the given time of day
     */
    private static Instant until(String time) {
        LocalDateTime deadline = LocalDateTime.now().with(LocalTime.parse(time));
        if (!deadline.isAfter(LocalDateTime.now())) {
            deadline = deadline.plusDays(1);
        }
        return deadline.atZone(ZoneId.systemDefault()).toInstant();
    }

    private static String formatDuration(long seconds) {
        return seconds >= 3600 ?
            String.format("%dh%02dm", seconds / 3600, seconds % 3600 / 60) :
            String.format("%dm%02ds", seconds / 60, seconds % 60);
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void log(String format, Object... args) {
        System.out.println(LocalDateTime.now().format(TIMESTAMP) + "  " + String.format(format, args));
    }
}
