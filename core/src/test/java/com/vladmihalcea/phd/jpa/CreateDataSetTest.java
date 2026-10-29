package com.vladmihalcea.phd.jpa;

import com.vladmihalcea.phd.common.config.BenchmarkTestData;
import com.vladmihalcea.phd.common.config.DataSetGenerator;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the generated data set as CSV files, e.g., for the replication package,
 * at the scale that the {@code benchmark.scale} system property sets.
 *
 * @author Vlad Mihalcea
 */
public class CreateDataSetTest {

    @Test
    @Disabled("This test is used to export the data set of the benchmarks")
    public void writeDataSet() throws IOException {
        DataSetGenerator generator = new DataSetGenerator(BenchmarkTestData.scale());
        Path folder = Path.of("target", "data-set-sf" + generator.scale());
        Files.createDirectories(folder);
        for (String table : DataSetGenerator.TABLES) {
            try (Writer writer = Files.newBufferedWriter(folder.resolve(table + ".csv"))) {
                writer.write(DataSetGenerator.columns(table).replace(" ", "") + "\n");
                generator.write(table, writer);
            }
        }
    }
}
