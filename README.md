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

# Run only the main DTO-to-entity integration test
mvn -Dtest=SpringDataJPADTO2EntityTest test
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
3. Sets the database name to `dto2entity`.

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