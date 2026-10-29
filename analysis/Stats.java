import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Statistical analysis of a benchmark campaign, as Section 4.7 of the article describes it.
 * <p>
 * The program reads the JMH result of every fork, as the run-matrix driver writes it
 * ({@code <results>/<block>/round-<r>/<cell>.json}, one fork per file, in the sample-time mode),
 * merges the forks of every cell, and writes these files to the output folder:
 * <ul>
 *     <li>{@code cells.csv}: per cell, the mean of the iteration means with a two-level bootstrap 95% CI,
 *     and the p50, p90, p99, and p99.9 of all samples;</li>
 *     <li>{@code comparisons.csv} and {@code comparisons.tex}: per pattern comparison, the change of the mean,
 *     the ratio of the means with a two-level bootstrap 95% CI, the two-sided Mann-Whitney U test on the
 *     iteration means and on the fork means with Holm-adjusted p-values per block, Cliff's delta with a
 *     two-level bootstrap 95% CI, A12, and the magnitude by the thresholds of Vargha and Delaney;</li>
 *     <li>{@code layers.csv}: per cell, the mean HTTP calls, SQL statements, commits, and time per layer;</li>
 *     <li>{@code telemetry.csv}: per block and host, the clock, utilization, limit, thermal throttling, and drift
 *     summary of the application host and, if a telemetry agent ran there, of the database host;</li>
 *     <li>{@code flags.csv}: the forks whose telemetry on either host suggests interference or throttling;</li>
 *     <li>{@code sql-per-statement.csv}: per fork, the median SQL time per statement, a round-trip proxy;</li>
 *     <li>{@code environment.txt}: the JVM and JMH settings found in the results;</li>
 *     <li>{@code checks.txt}: missing forks, unreadable files, and the check of every fork's merged mean
 *     against the score that JMH reported.</li>
 * </ul>
 * Two cells are compared when their names differ only in the pattern token: {@code mqdto} against
 * {@code msjson}, {@code multiops} against {@code diffmerge}, and {@code msjson-string} against
 * {@code msjson-dto}. The blocks whose name starts with {@code w7} are reported without hypothesis tests.
 * <p>
 * Usage: {@code java analysis/Stats.java <results folder> [output folder]}, or
 * {@code java analysis/Stats.java --test} to run the self-test. It needs only a JDK.
 */
public class Stats {

    static final int BOOTSTRAP_RESAMPLES = 2_000;

    static final long SEED = 20_261_030L;

    /** Cliff's delta magnitude thresholds: Vargha and Delaney's A12 of 0.56, 0.64, and 0.71. */
    static final double[] MAGNITUDE_THRESHOLDS = {0.12, 0.28, 0.42};

    /** The largest group for which the Mann-Whitney U test uses the exact distribution (without ties). */
    static final int EXACT_LIMIT = 25;

    static final List<String[]> PAIRING_TOKENS = List.of(
        new String[]{"-mqdto-", "-msjson-"},
        new String[]{"-multiops-", "-diffmerge-"},
        new String[]{"-msjson-string-", "-msjson-dto-"},
        // The versioning block: the cost of optimistic locking, every core cell against its versioning twin
        new String[]{"-core-", "-versioning-"}
    );

    static final List<String> ENVIRONMENT_KEYS = List.of(
        "jmhVersion", "mode", "threads", "jvm", "jdkVersion", "vmName", "vmVersion", "jvmArgs",
        "warmupIterations", "warmupTime", "measurementIterations", "measurementTime"
    );

    static final Pattern ROUND = Pattern.compile("round-(\\d+)");

    /**
     * A host whose telemetry the {@code TelemetryProfiler} reports under the prefix of its metrics, e.g.,
     * {@code app.cpu.mhz}: the application host, which runs the benchmarks, and the database host,
     * if a telemetry agent ran there.
     *
     * @param reasonPrefix the prefix of the flag reasons of the host
     */
    record Host(String name, String reasonPrefix) {

        String prefix() {
            return name + ".";
        }

        double[] metric(Fork fork, String metric) {
            return fork.secondary().get(prefix() + metric);
        }

        boolean recorded(Fork fork) {
            return fork.secondary().keySet().stream().anyMatch(key -> key.startsWith(prefix()));
        }
    }

    static final Host APPLICATION_HOST = new Host("app", "");

    static final Host DATABASE_HOST = new Host("db", "database host ");

    static final List<Host> HOSTS = List.of(APPLICATION_HOST, DATABASE_HOST);

    public static void main(String[] args) throws IOException {
        if (args.length == 1 && args[0].equals("--test")) {
            System.exit(SelfTest.run());
        }
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: java analysis/Stats.java <results folder> [output folder]");
            System.err.println("       java analysis/Stats.java --test");
            System.exit(2);
        }
        Path results = Path.of(args[0]);
        Path output = args.length == 2 ? Path.of(args[1]) : results.resolve("analysis");
        Analysis analysis = Analysis.load(results);
        analysis.write(output);
        System.out.printf(Locale.ROOT, "Analyzed %d forks of %d cells in %d blocks; results in %s%n",
            analysis.forkCount(), analysis.cells.size(), analysis.blocks().size(), output);
        if (!analysis.checks.isEmpty()) {
            System.out.printf(Locale.ROOT, "%d check messages, see %s%n", analysis.checks.size(), output.resolve("checks.txt"));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Data model
    // ---------------------------------------------------------------------------------------------

    /** One JMH fork: the iteration means, the samples, and the secondary metrics per iteration. */
    record Fork(String block, String cell, int round, double[] iterationMeans, List<double[]> samples,
                double jmhScore, String unit, Map<String, double[]> secondary, Map<String, String> environment) {

        double mean() {
            return Stats.mean(iterationMeans);
        }

        /** The mean of all samples, weighted by their counts, which is the score that JMH reports in sample mode. */
        double sampleMean() {
            double sum = 0, count = 0;
            for (double[] sample : samples) {
                sum += sample[0] * sample[1];
                count += sample[1];
            }
            return sum / count;
        }
    }

    /** The forks of one cell of one block, in round order. */
    record Cell(String block, String name, List<Fork> forks) {

        double[][] iterationMeansPerFork() {
            return forks.stream().map(Fork::iterationMeans).toArray(double[][]::new);
        }

        double[] allIterationMeans() {
            return concat(iterationMeansPerFork());
        }

        double[] forkMeans() {
            return forks.stream().mapToDouble(Fork::mean).toArray();
        }

        double[] secondary(String metric) {
            return concat(forks.stream().map(f -> f.secondary().get(metric)).filter(v -> v != null).toArray(double[][]::new));
        }
    }

    record Comparison(String block, boolean tested, Cell baseline, Cell alternative,
                      double meanBaseline, double meanAlternative, double ratio, double ratioLow, double ratioHigh,
                      double pIteration, double pFork, double cliffsDelta, double deltaLow, double deltaHigh) {

        double deltaPercent() {
            return (meanAlternative / meanBaseline - 1) * 100;
        }

        double a12() {
            return (cliffsDelta + 1) / 2;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Loading and analysis
    // ---------------------------------------------------------------------------------------------

    static final class Analysis {

        final Map<String, Cell> cells = new TreeMap<>();

        final List<String> checks = new ArrayList<>();

        final Map<String, Integer> roundsPerBlock = new TreeMap<>();

        static Analysis load(Path results) throws IOException {
            Analysis analysis = new Analysis();
            List<Path> blockFolders = new ArrayList<>();
            if (hasRounds(results)) {
                blockFolders.add(results);
            } else {
                try (Stream<Path> children = Files.list(results)) {
                    children.filter(Files::isDirectory).filter(Stats::hasRounds).sorted().forEach(blockFolders::add);
                }
            }
            if (blockFolders.isEmpty()) {
                throw new IllegalArgumentException(results + " holds no round-<r> folders");
            }
            Map<String, List<Fork>> forksByCell = new TreeMap<>();
            for (Path blockFolder : blockFolders) {
                String block = blockFolder.getFileName().toString();
                List<Path> files;
                try (Stream<Path> walk = Files.walk(blockFolder, 2)) {
                    files = walk.filter(p -> p.getFileName().toString().endsWith(".json"))
                        .filter(p -> p.getParent() != null && ROUND.matcher(p.getParent().getFileName().toString()).matches())
                        .sorted().toList();
                }
                for (Path file : files) {
                    Matcher matcher = ROUND.matcher(file.getParent().getFileName().toString());
                    matcher.matches();
                    int round = Integer.parseInt(matcher.group(1));
                    String name = file.getFileName().toString();
                    String cell = name.substring(0, name.length() - ".json".length());
                    try {
                        Fork fork = readFork(block, cell, round, file);
                        forksByCell.computeIfAbsent(block + "/" + cell, k -> new ArrayList<>()).add(fork);
                        analysis.roundsPerBlock.merge(block, round, Math::max);
                    } catch (RuntimeException e) {
                        analysis.checks.add("UNREADABLE " + file + ": " + e.getMessage());
                    }
                }
            }
            for (Map.Entry<String, List<Fork>> entry : forksByCell.entrySet()) {
                List<Fork> forks = entry.getValue();
                forks.sort(Comparator.comparingInt(Fork::round));
                Fork first = forks.get(0);
                analysis.cells.put(entry.getKey(), new Cell(first.block(), first.cell(), List.copyOf(forks)));
            }
            analysis.check();
            return analysis;
        }

        int forkCount() {
            return cells.values().stream().mapToInt(c -> c.forks().size()).sum();
        }

        TreeSet<String> blocks() {
            TreeSet<String> blocks = new TreeSet<>();
            cells.values().forEach(c -> blocks.add(c.block()));
            return blocks;
        }

        List<Cell> cellsOf(String block) {
            return cells.values().stream().filter(c -> c.block().equals(block)).toList();
        }

        private void check() {
            for (Cell cell : cells.values()) {
                int rounds = roundsPerBlock.get(cell.block());
                TreeSet<Integer> missing = new TreeSet<>();
                for (int r = 1; r <= rounds; r++) {
                    missing.add(r);
                }
                double worst = 0;
                TreeSet<String> units = new TreeSet<>();
                for (Fork fork : cell.forks()) {
                    missing.remove(fork.round());
                    units.add(fork.unit());
                    worst = Math.max(worst, Math.abs(fork.sampleMean() / fork.jmhScore() - 1));
                }
                if (!missing.isEmpty()) {
                    checks.add("MISSING " + cell.block() + "/" + cell.name() + ": rounds " + missing);
                }
                if (units.size() > 1) {
                    checks.add("UNITS " + cell.block() + "/" + cell.name() + ": " + units);
                }
                if (worst > 1e-6) {
                    checks.add(String.format(Locale.ROOT, "MISMATCH %s/%s: the merged sample mean differs from the JMH score by up to %.3g",
                        cell.block(), cell.name(), worst));
                }
            }
            for (String block : blocks()) {
                for (Cell cell : cellsOf(block)) {
                    for (String[] tokens : PAIRING_TOKENS) {
                        String name = "-" + cell.name() + "-";
                        if (name.contains(tokens[0])) {
                            String partner = name.replace(tokens[0], tokens[1]);
                            partner = partner.substring(1, partner.length() - 1);
                            if (!cells.containsKey(block + "/" + partner)) {
                                checks.add("UNPAIRED " + block + "/" + cell.name() + ": no cell " + partner);
                            }
                        }
                    }
                }
            }
        }

        List<Comparison> comparisons() {
            List<Comparison> comparisons = new ArrayList<>();
            for (String block : blocks()) {
                boolean tested = !block.startsWith("w7");
                List<Comparison> family = new ArrayList<>();
                for (Cell baseline : cellsOf(block)) {
                    for (String[] tokens : PAIRING_TOKENS) {
                        String name = "-" + baseline.name() + "-";
                        if (!name.contains(tokens[0])) {
                            continue;
                        }
                        String partner = name.replace(tokens[0], tokens[1]);
                        Cell alternative = cells.get(block + "/" + partner.substring(1, partner.length() - 1));
                        if (alternative != null) {
                            family.add(compare(block, tested, baseline, alternative));
                        }
                    }
                }
                comparisons.addAll(family);
            }
            return comparisons;
        }

        void write(Path output) throws IOException {
            Files.createDirectories(output);
            writeCells(output.resolve("cells.csv"));
            List<Comparison> comparisons = comparisons();
            Map<Comparison, double[]> adjusted = holmPerBlock(comparisons);
            writeComparisons(output.resolve("comparisons.csv"), comparisons, adjusted);
            writeComparisonsTex(output.resolve("comparisons.tex"), comparisons, adjusted);
            writeLayers(output.resolve("layers.csv"));
            writeTelemetry(output.resolve("telemetry.csv"), output.resolve("flags.csv"));
            writeSqlPerStatement(output.resolve("sql-per-statement.csv"));
            writeEnvironment(output.resolve("environment.txt"));
            Files.write(output.resolve("checks.txt"), checks.isEmpty() ? List.of("No problems found.") : checks, StandardCharsets.UTF_8);
        }

        private static Map<Comparison, double[]> holmPerBlock(List<Comparison> comparisons) {
            Map<Comparison, double[]> adjusted = new IdentityHashMap<>();
            Map<String, List<Comparison>> byBlock = new TreeMap<>();
            for (Comparison c : comparisons) {
                byBlock.computeIfAbsent(c.block(), k -> new ArrayList<>()).add(c);
            }
            for (List<Comparison> family : byBlock.values()) {
                if (!family.get(0).tested()) {
                    family.forEach(c -> adjusted.put(c, new double[]{Double.NaN, Double.NaN}));
                    continue;
                }
                double[] iteration = holm(family.stream().mapToDouble(Comparison::pIteration).toArray());
                double[] fork = holm(family.stream().mapToDouble(Comparison::pFork).toArray());
                for (int i = 0; i < family.size(); i++) {
                    adjusted.put(family.get(i), new double[]{iteration[i], fork[i]});
                }
            }
            return adjusted;
        }

        private void writeCells(Path file) throws IOException {
            List<String> lines = new ArrayList<>();
            lines.add("block,cell,forks,iterations,unit,mean,mean_ci_low,mean_ci_high,p50,p90,p99,p99_9,samples");
            for (Cell cell : cells.values()) {
                double[][] perFork = cell.iterationMeansPerFork();
                SplittableRandom random = new SplittableRandom(SEED ^ cell.name().hashCode());
                double[] means = new double[BOOTSTRAP_RESAMPLES];
                for (int b = 0; b < BOOTSTRAP_RESAMPLES; b++) {
                    means[b] = mean(resampleTwoLevel(perFork, random));
                }
                Arrays.sort(means);
                List<double[]> samples = new ArrayList<>();
                cell.forks().forEach(f -> samples.addAll(f.samples()));
                double count = samples.stream().mapToDouble(s -> s[1]).sum();
                lines.add(csv(cell.block(), cell.name(), cell.forks().size(), cell.allIterationMeans().length,
                    cell.forks().get(0).unit(), mean(cell.allIterationMeans()),
                    quantileSorted(means, 0.025), quantileSorted(means, 0.975),
                    histogramQuantile(samples, 0.50), histogramQuantile(samples, 0.90),
                    histogramQuantile(samples, 0.99), histogramQuantile(samples, 0.999), (long) count));
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        }

        private static void writeComparisons(Path file, List<Comparison> comparisons, Map<Comparison, double[]> adjusted) throws IOException {
            List<String> lines = new ArrayList<>();
            lines.add("block,tested,baseline,alternative,forks_baseline,forks_alternative,mean_baseline,mean_alternative,"
                + "delta_pct,ratio,ratio_ci_low,ratio_ci_high,p_iteration,p_iteration_holm,p_fork,p_fork_holm,"
                + "cliffs_delta,cliffs_delta_ci_low,cliffs_delta_ci_high,a12,magnitude");
            for (Comparison c : comparisons) {
                double[] holm = adjusted.get(c);
                lines.add(csv(c.block(), c.tested(), c.baseline().name(), c.alternative().name(),
                    c.baseline().forks().size(), c.alternative().forks().size(), c.meanBaseline(), c.meanAlternative(),
                    c.deltaPercent(), c.ratio(), c.ratioLow(), c.ratioHigh(),
                    c.tested() ? c.pIteration() : Double.NaN, holm[0], c.tested() ? c.pFork() : Double.NaN, holm[1],
                    c.cliffsDelta(), c.deltaLow(), c.deltaHigh(), c.a12(), magnitude(c.cliffsDelta())));
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        }

        private static void writeComparisonsTex(Path file, List<Comparison> comparisons, Map<Comparison, double[]> adjusted) throws IOException {
            List<String> lines = new ArrayList<>();
            lines.add("% baseline & alternative & mean baseline & mean alternative & delta & p (Holm, iteration) & p (Holm, fork) & Cliff's delta [95% CI] & magnitude");
            String block = null;
            for (Comparison c : comparisons) {
                if (!c.block().equals(block)) {
                    block = c.block();
                    lines.add("% block " + block);
                }
                double[] holm = adjusted.get(c);
                lines.add(String.format(Locale.ROOT, "%s & %s & %.2f & %.2f & $%+.1f\\%%$ & %s & %s & $%+.2f$ [$%+.2f$, $%+.2f$] & %s \\\\",
                    c.baseline().name(), c.alternative().name(), c.meanBaseline(), c.meanAlternative(), c.deltaPercent(),
                    texP(holm[0]), texP(holm[1]), c.cliffsDelta(), c.deltaLow(), c.deltaHigh(), magnitude(c.cliffsDelta())));
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        }

        private void writeLayers(Path file) throws IOException {
            TreeSet<String> metrics = new TreeSet<>();
            cells.values().forEach(c -> c.forks().forEach(f -> f.secondary().keySet().stream()
                .filter(k -> k.startsWith("layer.")).forEach(metrics::add)));
            List<String> lines = new ArrayList<>();
            StringJoiner header = new StringJoiner(",");
            header.add("block").add("cell");
            metrics.forEach(header::add);
            header.add("http.client.minus.server.ms");
            lines.add(header.toString());
            for (Cell cell : cells.values()) {
                List<Object> row = new ArrayList<>(List.of(cell.block(), cell.name()));
                for (String metric : metrics) {
                    double[] values = cell.secondary(metric);
                    row.add(values.length == 0 ? Double.NaN : mean(values));
                }
                double[] client = cell.secondary("layer.http.client.ms");
                double[] server = cell.secondary("layer.http.server.ms");
                row.add(client.length == 0 || server.length == 0 ? Double.NaN : mean(client) - mean(server));
                lines.add(csv(row.toArray()));
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        }

        /**
         * Summarizes the telemetry of every block, once for the application host and, if a telemetry agent
         * ran on the database host, once for the database host, and flags the forks whose telemetry
         * on either host suggests interference or throttling.
         */
        private void writeTelemetry(Path telemetryFile, Path flagsFile) throws IOException {
            List<String> lines = new ArrayList<>();
            lines.add("block,host,forks,clock_median_mhz,clock_p5_mhz,clock_min_mhz,within_fork_cv_median,within_fork_cv_max,"
                + "cpu_limit_min,thermal_limit_min,thermal_throttles,cpu_util_mean,temp_max,cpu_power_mean_w,cpu_power_max_w,cpu_power_limit_w,"
                + "drift_rho_round_clock,drift_p,"
                + "coupling_rho_median,coupling_rho_max_abs");
            List<String> flags = new ArrayList<>();
            flags.add("block,cell,round,reasons");
            for (String block : blocks()) {
                List<Fork> forks = cellsOf(block).stream().flatMap(c -> c.forks().stream()).toList();
                List<List<String>> reasons = new ArrayList<>();
                forks.forEach(fork -> reasons.add(new ArrayList<>()));
                for (Host host : HOSTS) {
                    if (host == APPLICATION_HOST || forks.stream().anyMatch(host::recorded)) {
                        lines.add(summarizeTelemetry(block, host, forks, reasons));
                    }
                }
                for (int i = 0; i < forks.size(); i++) {
                    if (!reasons.get(i).isEmpty()) {
                        flags.add(csv(block, forks.get(i).cell(), forks.get(i).round(), String.join("; ", reasons.get(i))));
                    }
                }
            }
            Files.write(telemetryFile, lines, StandardCharsets.UTF_8);
            Files.write(flagsFile, flags, StandardCharsets.UTF_8);
        }

        /**
         * @param reasons the flag reasons of every fork of the block, to which the reasons of the host are added
         * @return the telemetry summary of the host for the block
         */
        private String summarizeTelemetry(String block, Host host, List<Fork> forks, List<List<String>> reasons) {
            List<double[]> clocks = new ArrayList<>();
            List<Double> cvs = new ArrayList<>(), forkClocks = new ArrayList<>(), rounds = new ArrayList<>(), utils = new ArrayList<>();
            double cpuLimit = Double.NaN, thermalLimit = Double.NaN, throttles = Double.NaN, temp = Double.NaN;
            double utilSum = 0, utilCount = 0, powerSum = 0, powerCount = 0, powerMax = Double.NaN, powerLimit = Double.NaN;
            for (Fork fork : forks) {
                double[] clock = host.metric(fork, "cpu.mhz");
                if (clock != null && clock.length > 0) {
                    clocks.add(clock);
                    cvs.add(sd(clock) / mean(clock));
                    forkClocks.add(median(clock));
                    rounds.add((double) fork.round());
                }
                cpuLimit = minIgnoringNaN(cpuLimit, min(host.metric(fork, "cpu.limit")));
                thermalLimit = minIgnoringNaN(thermalLimit, min(host.metric(fork, "thermal.limit")));
                throttles = sumIgnoringNaN(throttles, sum(host.metric(fork, "thermal.throttles")));
                temp = maxIgnoringNaN(temp, max(host.metric(fork, "temp")));
                double[] power = host.metric(fork, "cpu.power");
                if (power != null && power.length > 0) {
                    powerSum += Arrays.stream(power).sum();
                    powerCount += power.length;
                    powerMax = maxIgnoringNaN(powerMax, max(power));
                }
                powerLimit = minIgnoringNaN(powerLimit, min(host.metric(fork, "cpu.power.limit")));
                double[] util = host.metric(fork, "cpu.util");
                if (util != null && util.length > 0) {
                    utilSum += Arrays.stream(util).sum();
                    utilCount += util.length;
                    utils.add(mean(util));
                }
            }
            double[] allClocks = concat(clocks.toArray(double[][]::new));
            double[] sortedClocks = allClocks.clone();
            Arrays.sort(sortedClocks);
            double[] forkClockArray = forkClocks.stream().mapToDouble(Double::doubleValue).toArray();
            double drift = spearman(rounds.stream().mapToDouble(Double::doubleValue).toArray(), forkClockArray);
            List<Double> couplings = new ArrayList<>();
            for (Cell cell : cellsOf(block)) {
                double[] clock = cell.secondary(host.prefix() + "cpu.mhz");
                double[] scores = cell.allIterationMeans();
                if (clock.length == scores.length && clock.length > 2) {
                    double rho = spearman(clock, scores);
                    if (!Double.isNaN(rho)) {
                        couplings.add(rho);
                    }
                }
            }
            double[] couplingArray = couplings.stream().mapToDouble(Double::doubleValue).toArray();
            double[] cvArray = cvs.stream().mapToDouble(Double::doubleValue).toArray();
            for (int i = 0; i < forks.size(); i++) {
                Fork fork = forks.get(i);
                // A fork is compared with the forks of its own cell, since the cells of a block load the CPUs
                // differently by design, e.g., REST and service-level writes, or 4 and 16 threads
                double cellClock = cellMedian(forks, fork.cell(), f -> forkMedian(host.metric(f, "cpu.mhz")));
                double cellUtil = cellMedian(forks, fork.cell(), f -> forkMean(host.metric(f, "cpu.util")));
                List<String> forkReasons = new ArrayList<>();
                double limit = min(host.metric(fork, "cpu.limit"));
                double thermal = min(host.metric(fork, "thermal.limit"));
                double forkThrottles = sum(host.metric(fork, "thermal.throttles"));
                double[] clock = host.metric(fork, "cpu.mhz");
                double[] util = host.metric(fork, "cpu.util");
                if (limit < 100) {
                    forkReasons.add(String.format(Locale.ROOT, "performance limit %.0f%%", limit));
                }
                if (thermal < 100) {
                    forkReasons.add(String.format(Locale.ROOT, "thermal limit %.0f%%", thermal));
                }
                if (forkThrottles > 0) {
                    forkReasons.add(String.format(Locale.ROOT, "%.0f thermal throttling events", forkThrottles));
                }
                // An iteration whose package power averages within 5% of the long-term limit ran at the limit,
                // which lowers the clock without a thermal throttling event
                double forkPower = max(host.metric(fork, "cpu.power"));
                double forkPowerLimit = min(host.metric(fork, "cpu.power.limit"));
                if (forkPower >= 0.95 * forkPowerLimit) {
                    forkReasons.add(String.format(Locale.ROOT, "package power %.0f W at its %.0f W limit", forkPower, forkPowerLimit));
                }
                if (clock != null && clock.length > 0 && median(clock) < 0.9 * cellClock) {
                    forkReasons.add(String.format(Locale.ROOT, "median clock %.0f MHz, cell median %.0f MHz", median(clock), cellClock));
                }
                if (util != null && util.length > 0 && mean(util) > 2 * cellUtil) {
                    forkReasons.add(String.format(Locale.ROOT, "CPU utilization %.1f%%, cell median %.1f%%", mean(util), cellUtil));
                }
                for (String reason : forkReasons) {
                    reasons.get(i).add(host.reasonPrefix() + reason);
                }
            }
            return csv(block, host.name(), forks.size(),
                sortedClocks.length == 0 ? Double.NaN : quantileSorted(sortedClocks, 0.5),
                sortedClocks.length == 0 ? Double.NaN : quantileSorted(sortedClocks, 0.05),
                sortedClocks.length == 0 ? Double.NaN : sortedClocks[0],
                cvArray.length == 0 ? Double.NaN : median(cvArray), cvArray.length == 0 ? Double.NaN : max(cvArray),
                cpuLimit, thermalLimit, throttles, utilCount == 0 ? Double.NaN : utilSum / utilCount, temp,
                powerCount == 0 ? Double.NaN : powerSum / powerCount, powerMax, powerLimit,
                drift, spearmanP(drift, forkClockArray.length),
                couplingArray.length == 0 ? Double.NaN : median(couplingArray),
                couplingArray.length == 0 ? Double.NaN : Arrays.stream(couplingArray).map(Math::abs).max().orElse(Double.NaN));
        }

        /** The median of a per-fork value over the forks of the cell that have it. */
        private static double cellMedian(List<Fork> forks, String cell, java.util.function.ToDoubleFunction<Fork> value) {
            double[] values = forks.stream().filter(f -> f.cell().equals(cell)).mapToDouble(value).filter(v -> !Double.isNaN(v)).toArray();
            return values.length == 0 ? Double.NaN : median(values);
        }

        private static double forkMedian(double[] values) {
            return values == null || values.length == 0 ? Double.NaN : median(values);
        }

        private static double forkMean(double[] values) {
            return values == null || values.length == 0 ? Double.NaN : mean(values);
        }

        private void writeSqlPerStatement(Path file) throws IOException {
            List<String> lines = new ArrayList<>();
            lines.add("block,cell,round,sql_ms_per_statement_median");
            for (Cell cell : cells.values()) {
                for (Fork fork : cell.forks()) {
                    double[] sql = fork.secondary().get("layer.sql.ms");
                    double[] statements = fork.secondary().get("layer.sql.statements");
                    if (sql == null || statements == null || sql.length != statements.length) {
                        continue;
                    }
                    double[] perStatement = new double[sql.length];
                    int n = 0;
                    for (int i = 0; i < sql.length; i++) {
                        if (statements[i] > 0) {
                            perStatement[n++] = sql[i] / statements[i];
                        }
                    }
                    if (n > 0) {
                        lines.add(csv(cell.block(), cell.name(), fork.round(), median(Arrays.copyOf(perStatement, n))));
                    }
                }
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
        }

        private void writeEnvironment(Path file) throws IOException {
            Map<String, Map<String, Integer>> values = new TreeMap<>();
            for (Cell cell : cells.values()) {
                for (Fork fork : cell.forks()) {
                    fork.environment().forEach((k, v) -> values.computeIfAbsent(k, x -> new TreeMap<>()).merge(v, 1, Integer::sum));
                }
            }
            List<String> lines = new ArrayList<>();
            values.forEach((key, counts) -> counts.forEach((value, count) -> lines.add(key + ": " + value + " (" + count + " forks)")));
            Files.write(file, lines, StandardCharsets.UTF_8);
        }
    }

    static boolean hasRounds(Path folder) {
        try (Stream<Path> children = Files.list(folder)) {
            return children.anyMatch(p -> Files.isDirectory(p) && ROUND.matcher(p.getFileName().toString()).matches());
        } catch (IOException e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    static Fork readFork(String block, String cell, int round, Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        List<Object> root = (List<Object>) Json.parse(text);
        if (root.size() != 1) {
            throw new IllegalStateException("expected one benchmark per file, found " + root.size());
        }
        Map<String, Object> benchmark = (Map<String, Object>) root.get(0);
        Map<String, Object> primary = (Map<String, Object>) benchmark.get("primaryMetric");
        List<Object> histogramForks = (List<Object>) primary.get("rawDataHistogram");
        if (histogramForks == null) {
            throw new IllegalStateException("no rawDataHistogram, so the fork did not run in the sample-time mode");
        }
        if (histogramForks.size() != 1) {
            throw new IllegalStateException("expected one fork per file, found " + histogramForks.size());
        }
        List<Object> iterations = (List<Object>) histogramForks.get(0);
        double[] iterationMeans = new double[iterations.size()];
        List<double[]> samples = new ArrayList<>();
        for (int i = 0; i < iterations.size(); i++) {
            double sum = 0, count = 0;
            for (Object entry : (List<Object>) iterations.get(i)) {
                List<Object> pair = (List<Object>) entry;
                double value = ((Number) pair.get(0)).doubleValue();
                double n = ((Number) pair.get(1)).doubleValue();
                sum += value * n;
                count += n;
                samples.add(new double[]{value, n});
            }
            iterationMeans[i] = sum / count;
        }
        Map<String, double[]> secondary = new TreeMap<>();
        Map<String, Object> secondaryMetrics = (Map<String, Object>) benchmark.get("secondaryMetrics");
        if (secondaryMetrics != null) {
            for (Map.Entry<String, Object> metric : secondaryMetrics.entrySet()) {
                List<Object> raw = (List<Object>) ((Map<String, Object>) metric.getValue()).get("rawData");
                if (raw != null && raw.size() == 1) {
                    secondary.put(metric.getKey(), ((List<Object>) raw.get(0)).stream().mapToDouble(v -> ((Number) v).doubleValue()).toArray());
                }
            }
        }
        Map<String, String> environment = new TreeMap<>();
        for (String key : ENVIRONMENT_KEYS) {
            Object value = benchmark.get(key);
            if (value instanceof Double d && d == Math.rint(d)) {
                environment.put(key, String.valueOf(d.longValue()));
            } else if (benchmark.containsKey(key)) {
                environment.put(key, String.valueOf(value));
            }
        }
        return new Fork(block, cell, round, iterationMeans, samples, ((Number) primary.get("score")).doubleValue(),
            (String) primary.get("scoreUnit"), secondary, environment);
    }

    static Comparison compare(String block, boolean tested, Cell baseline, Cell alternative) {
        double[][] a = baseline.iterationMeansPerFork();
        double[][] b = alternative.iterationMeansPerFork();
        double[] allA = concat(a), allB = concat(b);
        SplittableRandom random = new SplittableRandom(SEED ^ (baseline.name() + "|" + alternative.name()).hashCode());
        double[] ratios = new double[BOOTSTRAP_RESAMPLES];
        double[] deltas = new double[BOOTSTRAP_RESAMPLES];
        for (int i = 0; i < BOOTSTRAP_RESAMPLES; i++) {
            double[] ra = resampleTwoLevel(a, random);
            double[] rb = resampleTwoLevel(b, random);
            ratios[i] = mean(rb) / mean(ra);
            deltas[i] = cliffsDelta(ra, rb);
        }
        Arrays.sort(ratios);
        Arrays.sort(deltas);
        double meanA = mean(allA), meanB = mean(allB);
        return new Comparison(block, tested, baseline, alternative, meanA, meanB, meanB / meanA,
            quantileSorted(ratios, 0.025), quantileSorted(ratios, 0.975),
            mannWhitney(allA, allB).p(), mannWhitney(baseline.forkMeans(), alternative.forkMeans()).p(),
            cliffsDelta(allA, allB), quantileSorted(deltas, 0.025), quantileSorted(deltas, 0.975));
    }

    // ---------------------------------------------------------------------------------------------
    // Statistics
    // ---------------------------------------------------------------------------------------------

    record MannWhitney(double u, double p) {}

    /**
     * The two-sided Mann-Whitney U test. U counts the pairs in which the value of {@code x} exceeds the value
     * of {@code y} (ties count one half). Without ties and with at most {@value #EXACT_LIMIT} values per group,
     * the p-value comes from the exact distribution; otherwise from the normal approximation with the tie and
     * continuity corrections.
     */
    static MannWhitney mannWhitney(double[] x, double[] y) {
        int n1 = x.length, n2 = y.length, n = n1 + n2;
        double[] values = new double[n];
        System.arraycopy(x, 0, values, 0, n1);
        System.arraycopy(y, 0, values, n1, n2);
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble(i -> values[i]));
        double[] ranks = new double[n];
        double tieTerm = 0;
        for (int start = 0; start < n; ) {
            int end = start;
            while (end + 1 < n && values[order[end + 1]] == values[order[start]]) {
                end++;
            }
            double rank = (start + end) / 2.0 + 1;
            for (int k = start; k <= end; k++) {
                ranks[order[k]] = rank;
            }
            double t = end - start + 1;
            tieTerm += t * t * t - t;
            start = end + 1;
        }
        double rankSum = 0;
        for (int i = 0; i < n1; i++) {
            rankSum += ranks[i];
        }
        double u = rankSum - n1 * (n1 + 1) / 2.0;
        if (tieTerm == 0 && n1 <= EXACT_LIMIT && n2 <= EXACT_LIMIT) {
            return new MannWhitney(u, exactMannWhitneyP(u, n1, n2));
        }
        double meanU = n1 * (double) n2 / 2;
        double variance = n1 * (double) n2 / 12 * ((n + 1) - tieTerm / ((double) n * (n - 1)));
        if (variance <= 0) {
            return new MannWhitney(u, 1);
        }
        double z = Math.max(0, Math.abs(u - meanU) - 0.5) / Math.sqrt(variance);
        return new MannWhitney(u, Math.min(1, erfc(z / Math.sqrt(2))));
    }

    /** The exact two-sided p-value of U, from the number of orderings of n1 + n2 distinct values that give each U. */
    static double exactMannWhitneyP(double u, int n1, int n2) {
        double[][][] counts = new double[n1 + 1][n2 + 1][];
        for (int i = 0; i <= n1; i++) {
            for (int j = 0; j <= n2; j++) {
                double[] d = new double[i * j + 1];
                if (i == 0 || j == 0) {
                    d[0] = 1;
                } else {
                    // The largest value belongs either to x, which then exceeds all j values of y, or to y.
                    double[] lastX = counts[i - 1][j];
                    double[] lastY = counts[i][j - 1];
                    for (int k = 0; k < lastX.length; k++) {
                        d[k + j] += lastX[k];
                    }
                    for (int k = 0; k < lastY.length; k++) {
                        d[k] += lastY[k];
                    }
                }
                counts[i][j] = d;
            }
        }
        double[] distribution = counts[n1][n2];
        double total = Arrays.stream(distribution).sum();
        int observed = (int) Math.round(u);
        double lower = 0, upper = 0;
        for (int k = 0; k < distribution.length; k++) {
            if (k <= observed) {
                lower += distribution[k];
            }
            if (k >= observed) {
                upper += distribution[k];
            }
        }
        return Math.min(1, 2 * Math.min(lower, upper) / total);
    }

    /** Cliff's delta of {@code b} against {@code a}: P(b > a) - P(b < a), negative when {@code b} is smaller. */
    static double cliffsDelta(double[] a, double[] b) {
        double[] sorted = a.clone();
        Arrays.sort(sorted);
        long greater = 0, less = 0;
        for (double value : b) {
            int below = lowerBound(sorted, value);
            int belowOrEqual = upperBound(sorted, value);
            greater += below;
            less += sorted.length - belowOrEqual;
        }
        return (greater - less) / ((double) a.length * b.length);
    }

    static String magnitude(double delta) {
        double size = Math.abs(delta);
        if (size < MAGNITUDE_THRESHOLDS[0]) {
            return "negligible";
        }
        if (size < MAGNITUDE_THRESHOLDS[1]) {
            return "small";
        }
        if (size < MAGNITUDE_THRESHOLDS[2]) {
            return "medium";
        }
        return "large";
    }

    /** Holm's step-down adjusted p-values, in the order of the input; NaN p-values are left out of the family. */
    static double[] holm(double[] p) {
        Integer[] order = java.util.stream.IntStream.range(0, p.length).filter(i -> !Double.isNaN(p[i])).boxed().toArray(Integer[]::new);
        Arrays.sort(order, Comparator.comparingDouble(i -> p[i]));
        double[] adjusted = new double[p.length];
        Arrays.fill(adjusted, Double.NaN);
        int m = order.length;
        double running = 0;
        for (int k = 0; k < m; k++) {
            running = Math.max(running, Math.min(1, (m - k) * p[order[k]]));
            adjusted[order[k]] = running;
        }
        return adjusted;
    }

    /** Resamples the forks with replacement, and then the iteration means within every resampled fork. */
    static double[] resampleTwoLevel(double[][] forks, SplittableRandom random) {
        int[] chosen = new int[forks.length];
        int total = 0;
        for (int f = 0; f < forks.length; f++) {
            chosen[f] = random.nextInt(forks.length);
            total += forks[chosen[f]].length;
        }
        double[] resample = new double[total];
        int position = 0;
        for (int f : chosen) {
            double[] iterations = forks[f];
            for (int i = 0; i < iterations.length; i++) {
                resample[position++] = iterations[random.nextInt(iterations.length)];
            }
        }
        return resample;
    }

    /** The nearest-rank quantile of samples given as (value, count) pairs. */
    static double histogramQuantile(List<double[]> samples, double q) {
        List<double[]> sorted = new ArrayList<>(samples);
        sorted.sort(Comparator.comparingDouble(s -> s[0]));
        double total = sorted.stream().mapToDouble(s -> s[1]).sum();
        double rank = Math.max(1, Math.ceil(q * total));
        double cumulative = 0;
        for (double[] sample : sorted) {
            cumulative += sample[1];
            if (cumulative >= rank) {
                return sample[0];
            }
        }
        return sorted.get(sorted.size() - 1)[0];
    }

    /** The nearest-rank quantile of sorted values. */
    static double quantileSorted(double[] sorted, double q) {
        int rank = (int) Math.max(1, Math.ceil(q * sorted.length));
        return sorted[Math.min(rank, sorted.length) - 1];
    }

    static double spearman(double[] x, double[] y) {
        if (x.length != y.length || x.length < 3) {
            return Double.NaN;
        }
        return pearson(averageRanks(x), averageRanks(y));
    }

    /** The two-sided p-value of a Spearman correlation, from the normal approximation z = rho * sqrt(n - 1). */
    static double spearmanP(double rho, int n) {
        return Double.isNaN(rho) || n < 3 ? Double.NaN : erfc(Math.abs(rho) * Math.sqrt(n - 1) / Math.sqrt(2));
    }

    static double[] averageRanks(double[] values) {
        int n = values.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble(i -> values[i]));
        double[] ranks = new double[n];
        for (int start = 0; start < n; ) {
            int end = start;
            while (end + 1 < n && values[order[end + 1]] == values[order[start]]) {
                end++;
            }
            for (int k = start; k <= end; k++) {
                ranks[order[k]] = (start + end) / 2.0 + 1;
            }
            start = end + 1;
        }
        return ranks;
    }

    static double pearson(double[] x, double[] y) {
        double mx = mean(x), my = mean(y), sxy = 0, sxx = 0, syy = 0;
        for (int i = 0; i < x.length; i++) {
            sxy += (x[i] - mx) * (y[i] - my);
            sxx += (x[i] - mx) * (x[i] - mx);
            syy += (y[i] - my) * (y[i] - my);
        }
        return sxx == 0 || syy == 0 ? Double.NaN : sxy / Math.sqrt(sxx * syy);
    }

    /** The complementary error function, with a fractional error below 1.2e-7 (Numerical Recipes, erfcc). */
    static double erfc(double x) {
        double z = Math.abs(x);
        double t = 1 / (1 + 0.5 * z);
        double r = t * Math.exp(-z * z - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418
            + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587
            + t * (-0.82215223 + t * 0.17087277)))))))));
        return x >= 0 ? r : 2 - r;
    }

    static double mean(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    static double sd(double[] values) {
        if (values.length < 2) {
            return Double.NaN;
        }
        double m = mean(values), sum = 0;
        for (double value : values) {
            sum += (value - m) * (value - m);
        }
        return Math.sqrt(sum / (values.length - 1));
    }

    static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
    }

    static double min(double[] values) {
        return values == null || values.length == 0 ? Double.NaN : Arrays.stream(values).min().getAsDouble();
    }

    static double max(double[] values) {
        return values == null || values.length == 0 ? Double.NaN : Arrays.stream(values).max().getAsDouble();
    }

    static double sum(double[] values) {
        return values == null || values.length == 0 ? Double.NaN : Arrays.stream(values).sum();
    }

    static double sumIgnoringNaN(double a, double b) {
        return Double.isNaN(a) ? b : Double.isNaN(b) ? a : a + b;
    }

    static double minIgnoringNaN(double a, double b) {
        return Double.isNaN(a) ? b : Double.isNaN(b) ? a : Math.min(a, b);
    }

    static double maxIgnoringNaN(double a, double b) {
        return Double.isNaN(a) ? b : Double.isNaN(b) ? a : Math.max(a, b);
    }

    static double[] concat(double[][] arrays) {
        int total = 0;
        for (double[] array : arrays) {
            total += array.length;
        }
        double[] result = new double[total];
        int position = 0;
        for (double[] array : arrays) {
            System.arraycopy(array, 0, result, position, array.length);
            position += array.length;
        }
        return result;
    }

    /** The number of values strictly below {@code key} in a sorted array. */
    static int lowerBound(double[] sorted, double key) {
        int low = 0, high = sorted.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (sorted[middle] < key) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    /** The number of values below or equal to {@code key} in a sorted array. */
    static int upperBound(double[] sorted, double key) {
        int low = 0, high = sorted.length;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (sorted[middle] <= key) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    // ---------------------------------------------------------------------------------------------
    // Output formatting
    // ---------------------------------------------------------------------------------------------

    static String csv(Object... values) {
        StringJoiner line = new StringJoiner(",");
        for (Object value : values) {
            if (value instanceof Double d) {
                line.add(d.isNaN() ? "" : String.format(Locale.ROOT, "%.6g", d));
            } else {
                String text = String.valueOf(value);
                line.add(text.contains(",") || text.contains("\"") ? "\"" + text.replace("\"", "\"\"") + "\"" : text);
            }
        }
        return line.toString();
    }

    static String texP(double p) {
        if (Double.isNaN(p)) {
            return "--";
        }
        if (p < 0.001) {
            return "$<0.001$";
        }
        return String.format(Locale.ROOT, "$%.3f$", p);
    }

    // ---------------------------------------------------------------------------------------------
    // A minimal JSON reader for the JMH result files
    // ---------------------------------------------------------------------------------------------

    static final class Json {

        private final String text;

        private int position;

        private Json(String text) {
            this.text = text;
        }

        static Object parse(String text) {
            Json json = new Json(text);
            Object value = json.value();
            json.whitespace();
            if (json.position != text.length()) {
                throw json.error("trailing content");
            }
            return value;
        }

        private Object value() {
            whitespace();
            if (position >= text.length()) {
                throw error("unexpected end");
            }
            char c = text.charAt(position);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            position++;
            Map<String, Object> object = new LinkedHashMap<>();
            whitespace();
            if (text.charAt(position) == '}') {
                position++;
                return object;
            }
            while (true) {
                whitespace();
                String key = string();
                whitespace();
                expect(':');
                object.put(key, value());
                whitespace();
                char c = text.charAt(position++);
                if (c == '}') {
                    return object;
                }
                if (c != ',') {
                    throw error("expected , or }");
                }
            }
        }

        private List<Object> array() {
            position++;
            List<Object> array = new ArrayList<>();
            whitespace();
            if (text.charAt(position) == ']') {
                position++;
                return array;
            }
            while (true) {
                array.add(value());
                whitespace();
                char c = text.charAt(position++);
                if (c == ']') {
                    return array;
                }
                if (c != ',') {
                    throw error("expected , or ]");
                }
            }
        }

        private String string() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (true) {
                char c = text.charAt(position++);
                if (c == '"') {
                    return builder.toString();
                }
                if (c != '\\') {
                    builder.append(c);
                    continue;
                }
                char escaped = text.charAt(position++);
                switch (escaped) {
                    case 'n' -> builder.append('\n');
                    case 't' -> builder.append('\t');
                    case 'r' -> builder.append('\r');
                    case 'b' -> builder.append('\b');
                    case 'f' -> builder.append('\f');
                    case 'u' -> {
                        builder.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                        position += 4;
                    }
                    default -> builder.append(escaped);
                }
            }
        }

        /** Reads a number, including the NaN and Infinity values that JMH writes without quotes. */
        private Double number() {
            for (String special : List.of("NaN", "Infinity", "-Infinity")) {
                if (text.startsWith(special, position)) {
                    position += special.length();
                    return Double.valueOf(special);
                }
            }
            int start = position;
            while (position < text.length() && "+-0123456789.eE".indexOf(text.charAt(position)) >= 0) {
                position++;
            }
            if (start == position) {
                throw error("unexpected character '" + text.charAt(position) + "'");
            }
            return Double.valueOf(text.substring(start, position));
        }

        private Object literal(String word, Object value) {
            if (!text.startsWith(word, position)) {
                throw error("expected " + word);
            }
            position += word.length();
            return value;
        }

        private void expect(char c) {
            if (position >= text.length() || text.charAt(position) != c) {
                throw error("expected " + c);
            }
            position++;
        }

        private void whitespace() {
            while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
                position++;
            }
        }

        private IllegalStateException error(String message) {
            return new IllegalStateException("JSON " + message + " at offset " + position);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Self-test: java analysis/Stats.java --test
    // ---------------------------------------------------------------------------------------------

    static final class SelfTest {

        private int passed;

        private final List<String> failures = new ArrayList<>();

        static int run() throws IOException {
            SelfTest test = new SelfTest();
            test.statistics();
            test.endToEnd();
            test.versioningBlock();
            System.out.printf(Locale.ROOT, "%d checks passed, %d failed%n", test.passed, test.failures.size());
            test.failures.forEach(f -> System.out.println("FAILED: " + f));
            return test.failures.isEmpty() ? 0 : 1;
        }

        private void statistics() {
            // Exact Mann-Whitney U: for n1 = n2 = 3, U takes the values 0..9 with frequencies 1,1,2,3,3,3,3,2,1,1 (20 orderings).
            MannWhitney separated = mannWhitney(new double[]{1, 2, 3}, new double[]{4, 5, 6});
            near("exact U, separated groups", separated.u(), 0, 0);
            near("exact p, separated groups", separated.p(), 2.0 / 20, 1e-12);
            MannWhitney interleaved = mannWhitney(new double[]{1, 3, 5}, new double[]{2, 4, 6});
            near("exact U, interleaved groups", interleaved.u(), 3, 0);
            near("exact p, interleaved groups", interleaved.p(), 2 * 7.0 / 20, 1e-12);
            near("exact p, 10 forks per group, separated", mannWhitney(range(1, 10), range(11, 20)).p(), 2 / 184_756.0, 1e-15);

            // Normal approximation: identical groups give p = 1; separated groups of 300 give a tiny p.
            near("normal p, identical groups", mannWhitney(range(1, 300), range(1, 300)).p(), 1, 1e-12);
            check("normal p, separated groups", mannWhitney(range(1, 300), range(1001, 1300)).p() < 1e-50);
            near("erfc(1.959964 / sqrt 2) = 0.05", erfc(1.959964 / Math.sqrt(2)), 0.05, 1e-6);

            // Cliff's delta, A12, and the magnitude labels.
            near("Cliff's delta with a tie", cliffsDelta(new double[]{1, 2}, new double[]{2, 3}), 0.75, 1e-12);
            near("Cliff's delta, alternative lower", cliffsDelta(new double[]{4, 5, 6}, new double[]{1, 2, 3}), -1, 0);
            equal("magnitude 0.10", magnitude(0.10), "negligible");
            equal("magnitude -0.20", magnitude(-0.20), "small");
            equal("magnitude 0.30", magnitude(0.30), "medium");
            equal("magnitude -0.50", magnitude(-0.50), "large");

            // Holm: p = (0.01, 0.04, 0.03, 0.005) gives (0.03, 0.06, 0.06, 0.02).
            double[] holm = holm(new double[]{0.01, 0.04, 0.03, 0.005});
            near("Holm 1", holm[0], 0.03, 1e-12);
            near("Holm 2", holm[1], 0.06, 1e-12);
            near("Holm 3", holm[2], 0.06, 1e-12);
            near("Holm 4", holm[3], 0.02, 1e-12);

            // Nearest-rank quantiles of a histogram with the values 1, 2, 3, 4 once each.
            List<double[]> histogram = List.of(new double[]{3, 1}, new double[]{1, 1}, new double[]{4, 1}, new double[]{2, 1});
            near("histogram p50", histogramQuantile(histogram, 0.5), 2, 0);
            near("histogram p99.9", histogramQuantile(histogram, 0.999), 4, 0);

            // Spearman correlation of a monotonic relation.
            near("Spearman, monotonic", spearman(new double[]{1, 2, 3, 4}, new double[]{10, 20, 30, 50}), 1, 1e-12);
        }

        /** Runs the analysis on a synthetic block with three rounds of two cells, one of which is twice as fast. */
        private void endToEnd() throws IOException {
            Path results = Files.createTempDirectory("stats-test");
            try {
                for (int round = 1; round <= 3; round++) {
                    Path folder = Files.createDirectories(results.resolve("rq2-writes").resolve("round-0" + round));
                    Files.writeString(folder.resolve("jdbc-multiops-rest.json"), syntheticFork(10, round, 8, round == 2 ? 2 : 0));
                    Files.writeString(folder.resolve("jdbc-diffmerge-rest.json"), syntheticFork(5, round, 8, 0));
                }
                Path output = results.resolve("analysis");
                Analysis analysis = Analysis.load(results);
                analysis.write(output);
                equal("forks loaded", String.valueOf(analysis.forkCount()), "6");
                List<String> comparisons = Files.readAllLines(output.resolve("comparisons.csv"));
                equal("one comparison", String.valueOf(comparisons.size() - 1), "1");
                Map<String, String> row = row(comparisons.get(0), comparisons.get(1));
                equal("baseline", row.get("baseline"), "jdbc-multiops-rest");
                equal("alternative", row.get("alternative"), "jdbc-diffmerge-rest");
                // Every iteration mean is base + 0.001 i + 0.01 round + 0.2 / 3, so the cell means are known exactly.
                double offset = 0.001 * 14.5 + 0.01 * 2 + 0.2 / 3;
                double expectedRatio = (5 + offset) / (10 + offset);
                near("delta %", Double.parseDouble(row.get("delta_pct")), (expectedRatio - 1) * 100, 1e-3);
                near("Cliff's delta", Double.parseDouble(row.get("cliffs_delta")), -1, 0);
                near("ratio", Double.parseDouble(row.get("ratio")), expectedRatio, 1e-5);
                check("iteration-level p", Double.parseDouble(row.get("p_iteration")) < 1e-10);
                near("fork-level p, 3 forks per group", Double.parseDouble(row.get("p_fork")), 0.1, 1e-9);
                equal("checks", Files.readAllLines(output.resolve("checks.txt")).get(0), "No problems found.");
                List<String> sql = Files.readAllLines(output.resolve("sql-per-statement.csv"));
                near("SQL time per statement", Double.parseDouble(row(sql.get(0), sql.get(1)).get("sql_ms_per_statement_median")), 27, 1e-9);
                // The application host has no throttling counters, and one fork throttled on the database host.
                List<String> telemetry = Files.readAllLines(output.resolve("telemetry.csv"));
                equal("telemetry rows, application and database host", String.valueOf(telemetry.size() - 1), "2");
                Map<String, String> app = row(telemetry.get(0), telemetry.get(1)), db = row(telemetry.get(0), telemetry.get(2));
                equal("application host row", app.get("host"), "app");
                near("application host clock", Double.parseDouble(app.get("clock_median_mhz")), 3000, 0);
                equal("no application host throttling counters", app.get("thermal_throttles"), "");
                equal("database host row", db.get("host"), "db");
                near("database host clock", Double.parseDouble(db.get("clock_median_mhz")), 3500, 0);
                near("database host throttling events", Double.parseDouble(db.get("thermal_throttles")), 2, 0);
                near("application host package power", Double.parseDouble(app.get("cpu_power_mean_w")), 25, 1e-9);
                near("application host power limit", Double.parseDouble(app.get("cpu_power_limit_w")), 80, 1e-9);
                equal("no database host package power", db.get("cpu_power_mean_w"), "");
                List<String> flags = Files.readAllLines(output.resolve("flags.csv"));
                equal("one flagged fork", String.valueOf(flags.size() - 1), "1");
                equal("flag", flags.get(1), "rq2-writes,jdbc-multiops-rest,2,database host 2 thermal throttling events");
            } finally {
                try (Stream<Path> walk = Files.walk(results)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
            }
        }

        /**
         * Runs the analysis on a synthetic versioning block: the core cell is compared with its versioning twin,
         * and the versioning Multi-Ops cell, which loads the CPU six times as much as the Diff-Merge cells,
         * is not flagged for it, while one of its forks, at more than twice the utilization of the others, is.
         */
        private void versioningBlock() throws IOException {
            Path results = Files.createTempDirectory("stats-test");
            try {
                for (int round = 1; round <= 3; round++) {
                    Path folder = Files.createDirectories(results.resolve("versioning").resolve("round-0" + round));
                    Files.writeString(folder.resolve("core-jdbc-diffmerge-rest.json"), syntheticFork(5, round, 8, 0, 5));
                    Files.writeString(folder.resolve("versioning-jdbc-diffmerge-rest.json"), syntheticFork(6, round, 9, 0, 5, round == 3 ? 77 : 40));
                    Files.writeString(folder.resolve("versioning-jdbc-multiops-rest.json"), syntheticFork(10, round, 8, 0, round == 2 ? 70 : 30));
                }
                Path output = results.resolve("analysis");
                Analysis.load(results).write(output);
                List<String> comparisons = Files.readAllLines(output.resolve("comparisons.csv"));
                equal("versioning block comparisons", String.valueOf(comparisons.size() - 1), "2");
                check("core against versioning", comparisons.stream().skip(1).map(line -> row(comparisons.get(0), line))
                    .anyMatch(r -> r.get("baseline").equals("core-jdbc-diffmerge-rest") && r.get("alternative").equals("versioning-jdbc-diffmerge-rest")));
                check("Multi-Ops against Diff-Merge", comparisons.stream().skip(1).map(line -> row(comparisons.get(0), line))
                    .anyMatch(r -> r.get("baseline").equals("versioning-jdbc-multiops-rest") && r.get("alternative").equals("versioning-jdbc-diffmerge-rest")));
                List<String> flags = Files.readAllLines(output.resolve("flags.csv"));
                equal("two flagged forks in the versioning block", String.valueOf(flags.size() - 1), "2");
                check("flag against the cell median", flags.contains(
                    "versioning,versioning-jdbc-multiops-rest,2,\"CPU utilization 70.0%, cell median 30.0%\""));
                check("flag at the power limit", flags.contains("versioning,versioning-jdbc-diffmerge-rest,3,package power 77 W at its 80 W limit"));
            } finally {
                try (Stream<Path> walk = Files.walk(results)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
            }
        }

        /**
         * A JMH result of one fork with 30 iterations around {@code base} ms, and layer and telemetry metrics,
         * of which the database host telemetry has {@code dbThrottles} thermal throttling events in the first iteration.
         */
        private static String syntheticFork(double base, int round, int statements, int dbThrottles) {
            return syntheticFork(base, round, statements, dbThrottles, 10, 25);
        }

        private static String syntheticFork(double base, int round, int statements, int dbThrottles, double appUtil) {
            return syntheticFork(base, round, statements, dbThrottles, appUtil, 25);
        }

        private static String syntheticFork(double base, int round, int statements, int dbThrottles, double appUtil, double appPower) {
            StringJoiner iterations = new StringJoiner(",\n", "[\n", "\n]");
            double sum = 0, count = 0;
            for (int i = 0; i < 30; i++) {
                double low = base + 0.001 * i + 0.01 * round, high = low + 0.2;
                iterations.add(String.format(Locale.ROOT, "[ [ %s, 2 ], [ %s, 1 ] ]", low, high));
                sum += 2 * low + high;
                count += 3;
            }
            String thirty = String.join(", ", java.util.Collections.nCopies(30, "%s"));
            return String.format(Locale.ROOT, """
                [ {
                  "jmhVersion" : "1.37", "mode" : "sample", "threads" : 1, "forks" : 1,
                  "primaryMetric" : { "score" : %s, "scoreError" : NaN, "scoreUnit" : "ms/op",
                    "rawDataHistogram" : [ %s ] },
                  "secondaryMetrics" : {
                    "layer.sql.ms" : { "score" : %s, "rawData" : [ [ %s ] ] },
                    "layer.sql.statements" : { "score" : %d, "rawData" : [ [ %s ] ] },
                    "app.cpu.mhz" : { "score" : 3000, "rawData" : [ [ %s ] ] },
                    "app.cpu.util" : { "score" : 10, "rawData" : [ [ %s ] ] },
                    "app.cpu.power" : { "score" : 25, "rawData" : [ [ %s ] ] },
                    "app.cpu.power.limit" : { "score" : 80, "rawData" : [ [ %s ] ] },
                    "db.cpu.mhz" : { "score" : 3500, "rawData" : [ [ %s ] ] },
                    "db.cpu.util" : { "score" : 20, "rawData" : [ [ %s ] ] },
                    "db.thermal.throttles" : { "score" : %d, "rawData" : [ [ %d, %s ] ] }
                  }
                } ]
                """, sum / count, iterations, 27.0 * statements,
                String.format(Locale.ROOT, thirty, java.util.Collections.nCopies(30, String.valueOf(27.0 * statements)).toArray()),
                statements, String.format(Locale.ROOT, thirty, java.util.Collections.nCopies(30, String.valueOf(statements)).toArray()),
                String.format(Locale.ROOT, thirty, java.util.Collections.nCopies(30, "3000").toArray()),
                String.format(Locale.ROOT, thirty, java.util.Collections.nCopies(30, String.valueOf(appUtil)).toArray()),
                String.format(Locale.ROOT, thirty, java.util.Collections.nCopies(30, String.valueOf(appPower)).toArray()),
                String.format(Locale.ROOT, thirty, java.util.Collections.nCopies(30, "80").toArray()),
                String.format(Locale.ROOT, thirty, java.util.Collections.nCopies(30, "3500").toArray()),
                String.format(Locale.ROOT, thirty, java.util.Collections.nCopies(30, "20").toArray()),
                dbThrottles, dbThrottles, String.join(", ", java.util.Collections.nCopies(29, "0")));
        }

        private static Map<String, String> row(String header, String line) {
            String[] keys = header.split(","), values = line.split(",", -1);
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < keys.length; i++) {
                row.put(keys[i], values[i]);
            }
            return row;
        }

        private static double[] range(int from, int to) {
            double[] values = new double[to - from + 1];
            for (int i = 0; i < values.length; i++) {
                values[i] = from + i;
            }
            return values;
        }

        private void near(String name, double actual, double expected, double tolerance) {
            check(name + " (expected " + expected + ", was " + actual + ")", Math.abs(actual - expected) <= tolerance);
        }

        private void equal(String name, String actual, String expected) {
            check(name + " (expected " + expected + ", was " + actual + ")", expected.equals(actual));
        }

        private void check(String name, boolean condition) {
            if (condition) {
                passed++;
            } else {
                failures.add(name);
            }
        }
    }
}
