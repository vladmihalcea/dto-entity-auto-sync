package com.vladmihalcea.phd.common.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates the benchmark data set deterministically, so every load produces the same rows:
 * <ul>
 *     <li>{@code 20,000 × scale} posts, each with a comment, a reply to that comment, and four tags,
 *     which the write benchmarks edit;</li>
 *     <li>five fan-out posts, with 0, 5, 10, 25, and 100 comments and tags, which the read benchmarks load;</li>
 *     <li>{@code 50,000 × scale} users, who author the posts and comments according to a Zipf distribution,
 *     since the activity of forum users is heavy-tailed, so a fan-out post with N comments references
 *     up to N distinct users.</li>
 * </ul>
 * The rows reproduce the contents and the physical order of the original data set, which three users authored.
 * The IDs follow from formulas and the author of every post and comment from a hash of its ID, so every table
 * can be streamed into its own {@code COPY} command, without intermediate files.
 *
 * @author Vlad Mihalcea
 */
public class DataSetGenerator {

    /**
     * The fan-out post with N comments and N tags has the ID {@code FAN_OUT_POST_ID + N}.
     */
    public static final long FAN_OUT_POST_ID = 10_000_000L;

    public static final List<Integer> FAN_OUT_SIZES = List.of(0, 5, 10, 25, 100);

    public static final int POSTS_PER_SCALE = 20_000;

    public static final int USERS_PER_SCALE = 50_000;

    public static final List<String> TABLES = List.of(
        "users",
        "tags",
        "posts",
        "post_tags",
        "post_comments"
    );

    /**
     * Increment it whenever the generated rows change, so the read benchmarks reload the data set.
     */
    private static final int VERSION = 1;

    private static final long SEED = 20_261_029L;

    private static final double ZIPF_EXPONENT = 1.0;

    /**
     * As in the original data set, the replies to the comments of every batch of posts follow the batch.
     */
    private static final int BATCH_SIZE = 1_000;

    private static final long POST_AUTHORS = 1;
    private static final long COMMENT_AUTHORS = 2;

    private static final String BULK_POST_CONTENT = """
        Spring and Hibernate can help you do the o-sync
        between DTOs and JPA entities
        """;

    private static final String FAN_OUT_POST_CONTENT = """
        Spring and Hibernate can help you do the auto-sync
        between DTOs and JPA entities
        """;

    private static final String POST_TITLE = "DTO to Entity Auto-Sync";
    private static final String COMMENT_CONTENT = "Interesting topic, but I would like to see more examples";
    private static final String REPLY_CONTENT = "Can you apply this pattern to other platforms?";
    private static final List<String> BULK_TAG_NAMES = List.of("Spring", "JPA", "Hibernate", "MapStruct");

    /**
     * The users of the original data set, which the REST tests look up by email.
     */
    private static final List<String[]> NAMED_USERS = List.of(
        new String[] {"vlad@acme.com", "Vlad", "Mihalcea"},
        new String[] {"john@acme.com", "John", "Doe"},
        new String[] {"jane@acme.com", "Jane", "Doe"}
    );

    private static final List<String> FIRST_NAMES = List.of(
        "Alice", "Bogdan", "Carmen", "Daniel", "Elena", "Florin", "Gabriela", "Horia", "Ioana", "Mihai",
        "Andrei", "Cristina", "Diana", "Emil", "Irina", "Lucian", "Maria", "Radu", "Sorina", "Vlad"
    );

    private static final List<String> LAST_NAMES = List.of(
        "Popescu", "Ionescu", "Popa", "Pop", "Radu", "Dumitru", "Stan", "Stoica", "Gheorghe", "Matei",
        "Ciobanu", "Rusu", "Munteanu", "Florea", "Lazar", "Ene", "Moldovan", "Barbu", "Toma", "Serban"
    );

    private final int scale;
    private final int postCount;
    private final int userCount;
    private final long fanOutCount;
    private final double[] cumulativeProbabilities;
    private final int[] userIdsByRank;

    public DataSetGenerator(int scale) {
        if (scale < 1) {
            throw new IllegalArgumentException("The scale must be at least 1: " + scale);
        }
        this.scale = scale;
        postCount = POSTS_PER_SCALE * scale;
        userCount = USERS_PER_SCALE * scale;
        fanOutCount = FAN_OUT_SIZES.stream().mapToLong(Integer::longValue).sum();

        cumulativeProbabilities = new double[userCount];
        double sum = 0;
        for (int rank = 1; rank <= userCount; rank++) {
            sum += 1 / Math.pow(rank, ZIPF_EXPONENT);
            cumulativeProbabilities[rank - 1] = sum;
        }
        for (int i = 0; i < userCount; i++) {
            cumulativeProbabilities[i] /= sum;
        }

        // The most active users are spread over the users table instead of filling its first pages
        userIdsByRank = new int[userCount];
        Arrays.setAll(userIdsByRank, i -> i + 1);
        Random random = new Random(SEED);
        for (int i = userCount - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int userId = userIdsByRank[i];
            userIdsByRank[i] = userIdsByRank[j];
            userIdsByRank[j] = userId;
        }
    }

    /**
     * @return the parameters of the data set, which identify it in the {@code benchmark_dataset} marker
     */
    public String description() {
        return String.format(
            "generator v%d, scale %d, %d posts, %d users, Zipf exponent %.2f, seed %d",
            VERSION, scale, postCount, userCount, ZIPF_EXPONENT, SEED
        );
    }

    public int scale() {
        return scale;
    }

    /**
     * @return the number of posts that the write benchmarks can edit, with the IDs from 1 to this number
     */
    public int editablePostCount() {
        return postCount;
    }

    public Map<String, Long> rowCounts() {
        Map<String, Long> rowCounts = new LinkedHashMap<>();
        rowCounts.put("users", (long) userCount);
        rowCounts.put("tags", 4L * postCount + fanOutCount);
        rowCounts.put("posts", postCount + (long) FAN_OUT_SIZES.size());
        rowCounts.put("post_tags", 4L * postCount + fanOutCount);
        rowCounts.put("post_comments", 2L * postCount + fanOutCount);
        return rowCounts;
    }

    /**
     * @return the columns of the CSV rows that {@link #write(String, Appendable)} produces for the table
     */
    public static String columns(String table) {
        return switch (table) {
            case "users" -> "id, email, first_name, last_name";
            case "tags" -> "id, description, name";
            case "posts" -> "id, content, title, votes, author_id";
            case "post_tags" -> "tag_id, post_id";
            case "post_comments" -> "id, content, votes, author_id, parent_id, post_id";
            default -> throw new IllegalArgumentException("Unknown table: " + table);
        };
    }

    /**
     * Writes the rows of the table as CSV lines, in the order of the original data set.
     */
    public void write(String table, Appendable out) throws IOException {
        switch (table) {
            case "users" -> writeUsers(out);
            case "tags" -> writeTags(out);
            case "posts" -> writePosts(out);
            case "post_tags" -> writePostTags(out);
            case "post_comments" -> writePostComments(out);
            default -> throw new IllegalArgumentException("Unknown table: " + table);
        }
    }

    private void writeUsers(Appendable out) throws IOException {
        for (int id = 1; id <= userCount; id++) {
            if (id <= NAMED_USERS.size()) {
                String[] user = NAMED_USERS.get(id - 1);
                row(out, id, user[0], user[1], user[2]);
            } else {
                String firstName = FIRST_NAMES.get(id % FIRST_NAMES.size());
                String lastName = LAST_NAMES.get(id / FIRST_NAMES.size() % LAST_NAMES.size());
                String email = String.format("%s.%s.%d@acme.com", firstName.toLowerCase(), lastName.toLowerCase(), id);
                row(out, id, email, firstName, lastName);
            }
        }
    }

    /**
     * Writes the rows of the editable posts from {@code firstPostId} to {@code lastPostId} as the data set holds them,
     * so the posts that a write benchmark edited can be restored without reloading the data set.
     */
    public void writeEditablePosts(String table, long firstPostId, long lastPostId, Appendable out) throws IOException {
        if (firstPostId < 1 || lastPostId > postCount) {
            throw new IllegalArgumentException(String.format("The editable posts range from 1 to %d: %d-%d", postCount, firstPostId, lastPostId));
        }
        switch (table) {
            case "tags" -> writeBulkTags(out, firstPostId, lastPostId);
            case "posts" -> writeBulkPosts(out, firstPostId, lastPostId);
            case "post_tags" -> writeBulkPostTags(out, firstPostId, lastPostId);
            case "post_comments" -> writeBulkPostComments(out, firstPostId, lastPostId);
            default -> throw new IllegalArgumentException("The editable posts have no rows in the table: " + table);
        }
    }

    /**
     * @return the IDs of the four tags that the data set assigns to the editable post
     */
    public static long[] tagIds(long postId) {
        long[] tagIds = new long[BULK_TAG_NAMES.size()];
        Arrays.setAll(tagIds, i -> bulkTagId(postId, i));
        return tagIds;
    }

    private void writePosts(Appendable out) throws IOException {
        for (int size : FAN_OUT_SIZES) {
            long postId = FAN_OUT_POST_ID + size;
            row(out, postId, FAN_OUT_POST_CONTENT, POST_TITLE, 0, author(POST_AUTHORS, postId));
        }
        writeBulkPosts(out, 1, postCount);
    }

    private void writeBulkPosts(Appendable out, long firstPostId, long lastPostId) throws IOException {
        for (long postId = firstPostId; postId <= lastPostId; postId++) {
            row(out, postId, BULK_POST_CONTENT, POST_TITLE, 0, author(POST_AUTHORS, postId));
        }
    }

    private void writePostComments(Appendable out) throws IOException {
        long offset = 0;
        for (int size : FAN_OUT_SIZES) {
            long postId = FAN_OUT_POST_ID + size;
            for (int i = 1; i <= size; i++) {
                long commentId = fanOutCommentId(offset + i);
                row(out, commentId, "Comment nr. " + commentId, 0, author(COMMENT_AUTHORS, commentId), null, postId);
            }
            offset += size;
        }
        writeBulkPostComments(out, 1, postCount);
    }

    private void writeBulkPostComments(Appendable out, long firstPostId, long lastPostId) throws IOException {
        for (long batchFirstPostId = firstPostId; batchFirstPostId <= lastPostId; batchFirstPostId += BATCH_SIZE) {
            long batchLastPostId = Math.min(batchFirstPostId + BATCH_SIZE - 1, lastPostId);
            for (long postId = batchFirstPostId; postId <= batchLastPostId; postId++) {
                long commentId = commentId(postId);
                row(out, commentId, COMMENT_CONTENT, 0, author(COMMENT_AUTHORS, commentId), null, postId);
            }
            for (long postId = batchFirstPostId; postId <= batchLastPostId; postId++) {
                long replyId = replyId(postId);
                row(out, replyId, REPLY_CONTENT, 0, author(COMMENT_AUTHORS, replyId), commentId(postId), postId);
            }
        }
    }

    private void writeTags(Appendable out) throws IOException {
        long offset = 0;
        for (int size : FAN_OUT_SIZES) {
            for (int i = 1; i <= size; i++) {
                long tagId = fanOutTagId(offset + i);
                row(out, tagId, null, "Tag nr. " + tagId);
            }
            offset += size;
        }
        writeBulkTags(out, 1, postCount);
    }

    private void writeBulkTags(Appendable out, long firstPostId, long lastPostId) throws IOException {
        for (long postId = firstPostId; postId <= lastPostId; postId++) {
            for (int i = 0; i < BULK_TAG_NAMES.size(); i++) {
                row(out, bulkTagId(postId, i), null, BULK_TAG_NAMES.get(i) + " nr. " + postId);
            }
        }
    }

    private void writePostTags(Appendable out) throws IOException {
        long offset = 0;
        for (int size : FAN_OUT_SIZES) {
            long postId = FAN_OUT_POST_ID + size;
            for (int i = 1; i <= size; i++) {
                row(out, fanOutTagId(offset + i), postId);
            }
            offset += size;
        }
        writeBulkPostTags(out, 1, postCount);
    }

    private void writeBulkPostTags(Appendable out, long firstPostId, long lastPostId) throws IOException {
        for (long postId = firstPostId; postId <= lastPostId; postId++) {
            for (int i = 0; i < BULK_TAG_NAMES.size(); i++) {
                row(out, bulkTagId(postId, i), postId);
            }
        }
    }

    private static long commentId(long postId) {
        return 2 * postId - 1;
    }

    private static long replyId(long postId) {
        return 2 * postId;
    }

    private long fanOutCommentId(long index) {
        return 2L * postCount + index;
    }

    private static long bulkTagId(long postId, int index) {
        return 4 * (postId - 1) + index + 1;
    }

    private long fanOutTagId(long index) {
        return 4L * postCount + index;
    }

    /**
     * @return the ID of the user who authored the row, drawn from the Zipf distribution by a hash of the row ID
     */
    private long author(long stream, long rowId) {
        int rank = Arrays.binarySearch(cumulativeProbabilities, uniform(stream, rowId));
        if (rank < 0) {
            rank = -rank - 1;
        }
        return userIdsByRank[Math.min(rank, userCount - 1)];
    }

    /**
     * @return a number in [0, 1) that depends only on the seed, the stream, and the row ID (SplitMix64 finalizer)
     */
    private static double uniform(long stream, long rowId) {
        long z = SEED + stream * 0x9E3779B97F4A7C15L + rowId * 0xD1B54A32D192ED03L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (z >>> 11) * 0x1.0p-53;
    }

    private static void row(Appendable out, Object... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            Object value = values[i];
            // An empty unquoted field is NULL in PostgreSQL's CSV format
            if (value != null) {
                out.append(csv(value.toString()));
            }
        }
        out.append('\n');
    }

    private static String csv(String value) {
        return value.isEmpty() || value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0 ?
            '"' + value.replace("\"", "\"\"") + '"' :
            value;
    }
}
