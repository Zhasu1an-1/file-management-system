package com.example.filemanagementsystem;

import com.example.filemanagementsystem.file.FileRequests;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.util.FileSystemUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = "jdbc:postgresql:.+")
@DirtiesContext
class PostgresWorkflowTest {
    private static final String ALICE = "alice";
    private static final String BOB = "bob";
    private static final String CONTENT = "Hello, Сәлем 👋\nSecond line";
    private static final JsonMapper json = new JsonMapper();
    private static final HttpClient http = HttpClient.newHttpClient();
    private static TestDatabase database;

    @LocalServerPort
    int port;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) throws Exception {
        database = TestDatabase.create();
        database.properties().forEach((key, value) -> registry.add(key, () -> value));
    }

    @AfterAll
    static void cleanUp() throws Exception {
        if (database != null) database.close();
    }

    @Test
    void servesTheSinglePageAndPredefinedUsers() throws Exception {
        assertThat(request(port, "GET", "/", null, null, 200).body()).contains("File Management System");
        assertThat(request(port, "GET", "/app.js", null, null, 200).body()).contains("/files");
        assertThat(request(port, "GET", "/styles.css", null, null, 200).body()).contains(".workspace");
        assertThat(api(port, "GET", "/users", null, null, 200).size()).isEqualTo(2);
    }

    @Test
    void createsReadsSearchesRenamesAndDeletesFilesAcrossBothStorageImplementations() throws Exception {
        Instant before = Instant.now();
        JsonNode local = create(port, "Bridge notes.txt", "LOCAL");
        JsonNode stored = create(port, "Bridge database.txt", "DATABASE");
        String localId = id(local);
        String storedId = id(stored);

        assertThat(local.get("fileType").asText()).isEqualTo("txt");
        assertThat(local.get("sizeBytes").asInt()).isEqualTo(CONTENT.getBytes(StandardCharsets.UTF_8).length);
        assertThat(Instant.parse(local.get("createdAt").asText())).isBetween(before, Instant.now());
        assertThat(local.get("owner").asText()).isEqualTo(ALICE);
        assertThat(local.get("storageType").asText()).isEqualTo("LOCAL");
        assertThat(stored.get("storageType").asText()).isEqualTo("DATABASE");

        assertThat(names(api(port, "GET", "/files", ALICE, null, 200)))
                .contains("Bridge notes.txt", "Bridge database.txt");
        assertThat(names(api(port, "GET", "/files?search=BRIDGE%20NO", ALICE, null, 200)))
                .containsExactly("Bridge notes.txt");
        assertThat(names(api(port, "GET", "/files?search=%25", ALICE, null, 200))).isEmpty();
        assertThat(read(port, ALICE, localId).get("content").asText()).isEqualTo(CONTENT);
        assertThat(read(port, ALICE, storedId).get("content").asText()).isEqualTo(CONTENT);
        assertThat(Files.readString(database.uploads.resolve(localId))).isEqualTo(CONTENT);
        assertThat(Files.exists(database.uploads.resolve(storedId))).isFalse();
        assertThat(database.databaseContent(storedId)).containsExactly(CONTENT.getBytes(StandardCharsets.UTF_8));

        JsonNode renamed = api(port, "PATCH", "/files/" + localId + "/name", ALICE,
                Map.of("name", "Bridge renamed.txt"), 200);
        assertThat(renamed.get("name").asText()).isEqualTo("Bridge renamed.txt");
        assertThat(renamed.get("createdAt")).isEqualTo(local.get("createdAt"));
        assertThat(read(port, ALICE, localId).get("content").asText()).isEqualTo(CONTENT);

        api(port, "DELETE", "/files/" + localId, ALICE, null, 204);
        api(port, "DELETE", "/files/" + storedId, ALICE, null, 204);
        assertThat(Files.exists(database.uploads.resolve(localId))).isFalse();
        assertThat(database.databaseContent(storedId)).isNull();
        api(port, "GET", "/files/" + localId, ALICE, null, 404);
        api(port, "GET", "/files/" + storedId, ALICE, null, 404);
    }

    @Test
    void hidesPrivateFilesAndGrantsReadOnlyAccessAfterSharing() throws Exception {
        JsonNode file = create(port, "Permissions assignment.txt", "LOCAL");
        String path = "/files/" + id(file);
        assertThat(request(port, "GET", "/api" + path, ALICE, null, 200).headers().firstValue("Cache-Control"))
                .contains("no-store");
        assertThat(api(port, "GET", path, BOB, null, 403).get("message").asText()).contains("shared");
        assertThat(names(api(port, "GET", "/files", BOB, null, 200))).doesNotContain("Permissions assignment.txt");
        assertThat(names(api(port, "GET", "/files?search=Permissions", BOB, null, 200))).isEmpty();

        api(port, "POST", path + "/shares", ALICE, Map.of("viewer", BOB), 204);
        api(port, "POST", path + "/shares", ALICE, Map.of("viewer", BOB), 204);
        assertThat(read(port, BOB, id(file)).get("content").asText()).isEqualTo(CONTENT);
        assertThat(names(api(port, "GET", "/files?search=Permissions", BOB, null, 200)))
                .containsExactly("Permissions assignment.txt");

        api(port, "PATCH", path + "/name", BOB, Map.of("name", "blocked.txt"), 403);
        api(port, "DELETE", path, BOB, null, 403);
        api(port, "POST", path + "/shares", BOB, Map.of("viewer", ALICE), 403);
        api(port, "PUT", path + "/folder", BOB, new FileRequests.Move(null), 403);
        assertThat(read(port, ALICE, id(file)).get("file").get("name").asText())
                .isEqualTo("Permissions assignment.txt");

        api(port, "DELETE", path, ALICE, null, 204);
        assertThat(names(api(port, "GET", "/files?search=Permissions", BOB, null, 200))).isEmpty();
    }

    @Test
    void protectsFolderContentsAndRejectsDeletionUntilEmpty() throws Exception {
        JsonNode folder = api(port, "POST", "/folders", ALICE, Map.of("name", "Coursework"), 201);
        String folderId = id(folder);
        assertThat(names(api(port, "GET", "/folders", ALICE, null, 200))).contains("Coursework");
        assertThat(names(api(port, "GET", "/folders", BOB, null, 200))).doesNotContain("Coursework");
        JsonNode file = create(port, "Folder notes.txt", "DATABASE");
        String filePath = "/files/" + id(file);
        api(port, "PUT", filePath + "/folder", ALICE, Map.of("folderId", folderId), 200);

        String contents = "/files?folderId=" + folderId;
        assertThat(names(api(port, "GET", contents, ALICE, null, 200))).containsExactly("Folder notes.txt");
        assertThat(names(api(port, "GET", contents, BOB, null, 200))).isEmpty();
        api(port, "POST", filePath + "/shares", ALICE, Map.of("viewer", BOB), 204);
        assertThat(names(api(port, "GET", "/folders", BOB, null, 200))).contains("Coursework");
        assertThat(names(api(port, "GET", contents, BOB, null, 200))).containsExactly("Folder notes.txt");

        String folderPath = "/folders/" + folderId;
        assertThat(api(port, "DELETE", folderPath, ALICE, null, 409).get("message").asText()).contains("Move or delete");
        api(port, "DELETE", folderPath, BOB, null, 403);
        api(port, "POST", "/folders", BOB, Map.of("name", "Blocked"), 403);
        api(port, "PUT", filePath + "/folder", ALICE, new FileRequests.Move(null), 200);
        assertThat(names(api(port, "GET", contents, ALICE, null, 200))).isEmpty();
        api(port, "DELETE", folderPath, ALICE, null, 204);
        assertThat(names(api(port, "GET", "/folders", ALICE, null, 200))).doesNotContain("Coursework");
    }

    @Test
    void rejectsInvalidRequestsMissingRecordsAndDuplicateNames() throws Exception {
        api(port, "POST", "/files", ALICE, fileBody("../escape.txt", "TXT", CONTENT, "LOCAL"), 400);
        api(port, "POST", "/files", ALICE, fileBody("blank.txt", "TXT", "  ", "LOCAL"), 400);
        api(port, "POST", "/files", ALICE, fileBody("invalid.txt", "text/plain", CONTENT, "LOCAL"), 400);
        api(port, "POST", "/files", ALICE, fileBody("invalid.txt", "TXT", CONTENT, "CLOUD"), 400);
        api(port, "POST", "/files", ALICE,
                Map.of("name", "invalid.txt", "fileType", "TXT", "content", CONTENT, "storageType", 0), 400);
        api(port, "POST", "/files", BOB, fileBody("viewer.txt", "TXT", CONTENT, "LOCAL"), 403);
        api(port, "GET", "/files", null, null, 400);
        api(port, "GET", "/files", "unknown", null, 400);
        api(port, "GET", "/files/invalid-id", ALICE, null, 400);
        api(port, "GET", "/files/" + UUID.randomUUID(), ALICE, null, 404);
        api(port, "POST", "/folders", ALICE, Map.of("name", "   "), 400);
        create(port, "Duplicate.txt", "LOCAL");
        api(port, "POST", "/files", ALICE, fileBody("duplicate.TXT", "TXT", CONTENT, "DATABASE"), 409);

        var file = create(port, "Rename collision.txt", "DATABASE");
        api(port, "PATCH", "/files/" + id(file) + "/name", ALICE, Map.of("name", "Duplicate.txt"), 409);
        assertThat(read(port, ALICE, id(file)).get("file").get("name").asText()).isEqualTo("Rename collision.txt");
        api(port, "PUT", "/files/" + id(file) + "/folder", ALICE, Map.of(), 400);
        api(port, "PUT", "/files/" + id(file) + "/folder", ALICE, Map.of("folderId", UUID.randomUUID()), 404);
    }

    @Test
    void handlesMissingLocalContentWithAnUnderstandableError() throws Exception {
        String id = id(create(port, "Missing content.txt", "LOCAL"));
        Files.delete(database.uploads.resolve(id));

        assertThat(api(port, "GET", "/files/" + id, ALICE, null, 500).get("message").asText())
                .contains("Could not read local file content");
        api(port, "DELETE", "/files/" + id, ALICE, null, 204);
    }

    @Test
    void restartsWithBothContentsMetadataFoldersAndSharesIntact() throws Exception {
        try (var restartDatabase = TestDatabase.create()) {
            JsonNode local;
            JsonNode stored;
            JsonNode folder;
            try (var first = restartDatabase.startApplication()) {
                int firstPort = first.getWebServer().getPort();
                local = create(firstPort, "Restart local.txt", "LOCAL");
                stored = create(firstPort, "Restart database.txt", "DATABASE");
                folder = api(firstPort, "POST", "/folders", ALICE, Map.of("name", "Restart folder"), 201);
                api(firstPort, "PUT", "/files/" + id(local) + "/folder", ALICE, Map.of("folderId", id(folder)), 200);
                for (JsonNode file : List.of(local, stored)) {
                    api(firstPort, "POST", "/files/" + id(file) + "/shares", ALICE, Map.of("viewer", BOB), 204);
                }
            }
            try (var restarted = restartDatabase.startApplication()) {
                int restartedPort = restarted.getWebServer().getPort();
                JsonNode localDetails = read(restartedPort, BOB, id(local));
                JsonNode databaseDetails = read(restartedPort, BOB, id(stored));
                assertThat(localDetails.get("content").asText()).isEqualTo(CONTENT);
                assertThat(databaseDetails.get("content").asText()).isEqualTo(CONTENT);
                assertThat(databaseDetails.get("file")).isEqualTo(stored);
                assertThat(localDetails.get("file").get("createdAt")).isEqualTo(local.get("createdAt"));
                assertThat(localDetails.get("file").get("folderId").asText()).isEqualTo(id(folder));
                assertThat(names(api(restartedPort, "GET", "/folders", BOB, null, 200)))
                        .containsExactly("Restart folder");
                assertThat(names(api(restartedPort, "GET", "/files?folderId=" + id(folder), BOB, null, 200)))
                        .containsExactly("Restart local.txt");
                assertThat(api(restartedPort, "GET", "/users", null, null, 200).size()).isEqualTo(2);
            }
        }
    }

    private static JsonNode create(int port, String name, String storage) throws Exception {
        return api(port, "POST", "/files", ALICE, fileBody(name, "TXT", CONTENT, storage), 201);
    }

    private static Map<String, String> fileBody(String name, String type, String content, String storage) {
        return Map.of("name", name, "fileType", type, "content", content, "storageType", storage);
    }

    private static JsonNode read(int port, String actor, String id) throws Exception {
        return api(port, "GET", "/files/" + id, actor, null, 200);
    }

    private static String id(JsonNode record) {
        return record.get("id").asText();
    }

    private static List<String> names(JsonNode records) {
        List<String> names = new ArrayList<>();
        records.forEach(record -> names.add(record.get("name").asText()));
        return names;
    }

    private static JsonNode api(int port, String method, String path, String actor, Object body, int status)
            throws Exception {
        String response = request(port, method, "/api" + path, actor, body, status).body();
        return response.isEmpty() ? null : json.readTree(response);
    }

    private static HttpResponse<String> request(int port, String method, String path, String actor, Object body, int status)
            throws IOException, InterruptedException {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15));
        if (actor != null) builder.header("X-User", actor);
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        }
        var result = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(result.statusCode()).as("%s %s returned %s", method, path, result.body()).isEqualTo(status);
        return result;
    }

    private record TestDatabase(String url, String user, String password, String schema, Path uploads)
            implements AutoCloseable {
        static TestDatabase create() throws Exception {
            String url = System.getenv("TEST_DB_URL");
            String user = System.getenv().getOrDefault("TEST_DB_USERNAME", System.getProperty("user.name"));
            String password = System.getenv().getOrDefault("TEST_DB_PASSWORD", "");
            String schema = "fms_test_" + UUID.randomUUID().toString().replace("-", "");
            Path uploads = Files.createTempDirectory("fms-test-");
            try (var connection = DriverManager.getConnection(url, user, password);
                 var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + schema);
            } catch (Exception e) {
                Files.delete(uploads);
                throw e;
            }
            return new TestDatabase(url, user, password, schema, uploads);
        }

        Map<String, String> properties() {
            return Map.of("spring.datasource.url", url, "spring.datasource.username", user,
                    "spring.datasource.password", password, "spring.datasource.hikari.schema", schema,
                    "spring.jpa.properties.hibernate.default_schema", schema, "app.storage.directory", uploads.toString());
        }

        ServletWebServerApplicationContext startApplication() {
            String[] settings = properties().entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue()).toArray(String[]::new);
            return (ServletWebServerApplicationContext) new SpringApplicationBuilder(FileManagementSystemApplication.class)
                    .initializers(context -> TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, settings))
                    .run("--server.port=0");
        }

        byte[] databaseContent(String id) throws Exception {
            try (var connection = DriverManager.getConnection(url, user, password);
                 var statement = connection.prepareStatement("SELECT content FROM " + schema + ".file_contents WHERE file_id = ?")) {
                statement.setObject(1, UUID.fromString(id));
                try (var rows = statement.executeQuery()) {
                    return rows.next() ? rows.getBytes(1) : null;
                }
            }
        }

        @Override
        public void close() throws Exception {
            try (var connection = DriverManager.getConnection(url, user, password);
                 var statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            } finally {
                FileSystemUtils.deleteRecursively(uploads);
            }
        }
    }
}
