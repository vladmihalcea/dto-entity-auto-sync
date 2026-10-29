package com.vladmihalcea.phd.common.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.vladmihalcea.phd.common.config.DataSetGenerator.FAN_OUT_POST_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the generated data set without a database.
 *
 * @author Vlad Mihalcea
 */
public class DataSetGeneratorTest {

    private final DataSetGenerator generator = new DataSetGenerator(1);

    @Test
    public void testRowCounts() throws IOException {
        for (Map.Entry<String, Long> rowCount : generator.rowCounts().entrySet()) {
            assertEquals(rowCount.getValue().longValue(), csvRows(rowCount.getKey()).size(), rowCount.getKey());
        }
        assertEquals(50_000, generator.rowCounts().get("users"));
        assertEquals(20_005, generator.rowCounts().get("posts"));
        assertEquals(40_140, generator.rowCounts().get("post_comments"));
        assertEquals(80_140, generator.rowCounts().get("tags"));
    }

    @Test
    public void testDeterministicRows() throws IOException {
        DataSetGenerator otherGenerator = new DataSetGenerator(1);
        for (String table : DataSetGenerator.TABLES) {
            StringBuilder rows = new StringBuilder();
            StringBuilder otherRows = new StringBuilder();
            generator.write(table, rows);
            otherGenerator.write(table, otherRows);
            assertEquals(rows.toString(), otherRows.toString(), table);
        }
    }

    @Test
    public void testHeavyTailedAuthorship() throws IOException {
        List<String[]> comments = csvRows("post_comments");

        // A fan-out post with 100 comments references many distinct users, unlike the original data set
        long fanOutAuthors = comments.stream()
            .filter(comment -> comment[5].equals(String.valueOf(FAN_OUT_POST_ID + 100)))
            .map(comment -> comment[3])
            .distinct()
            .count();
        assertTrue(fanOutAuthors >= 50, "Distinct authors of the fan-out post with 100 comments: " + fanOutAuthors);

        // With a Zipf exponent of 1 over 50,000 users, the most active user authors about 1 / H(50,000), or 9%, of the comments
        Map<String, Long> commentsByAuthor = comments.stream()
            .collect(Collectors.groupingBy(comment -> comment[3], Collectors.counting()));
        double topAuthorShare = commentsByAuthor.values().stream().mapToLong(Long::longValue).max().orElseThrow() / (double) comments.size();
        assertTrue(topAuthorShare > 0.07 && topAuthorShare < 0.11, "Share of the most active author: " + topAuthorShare);
        assertTrue(commentsByAuthor.size() > 10_000, "Distinct comment authors: " + commentsByAuthor.size());
    }

    @Test
    public void testNamedUsers() throws IOException {
        Map<String, String[]> usersByEmail = csvRows("users").stream()
            .collect(Collectors.toMap(user -> user[1], Function.identity()));
        assertEquals("1", usersByEmail.get("vlad@acme.com")[0]);
        assertEquals("2", usersByEmail.get("john@acme.com")[0]);
        assertEquals("3", usersByEmail.get("jane@acme.com")[0]);
    }

    /**
     * Splits the CSV rows of the table, whose quoted fields can contain commas and line breaks.
     */
    private List<String[]> csvRows(String table) throws IOException {
        StringBuilder csv = new StringBuilder();
        generator.write(table, csv);
        List<String[]> rows = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                fields.add(field.toString());
                field.setLength(0);
                rows.add(fields.toArray(String[]::new));
                fields.clear();
            } else {
                field.append(c);
            }
        }
        assertTrue(fields.isEmpty() && field.isEmpty(), "The CSV of " + table + " must end with a line break");
        return rows;
    }
}
