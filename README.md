# File Management System

A single-page Java application for Design Patterns Assignment 3. Alice can create, search, read, rename, move, share, and delete text files. Bob can read files shared with him. The assignment demonstrates two patterns in the same request flow. A Protection Proxy controls access, and a Bridge connects file operations to two persistent storage backends.

**Stack** · Java 21, Spring Boot 4.1.2, Spring MVC, Spring Data JPA, Jakarta Validation, PostgreSQL, Maven, HTML, CSS, vanilla JavaScript, JUnit 5, Spring Boot Test, and Mockito.

## Run locally

Start your local PostgreSQL server and confirm it responds with `pg_isready -h 127.0.0.1`. On a fresh installation, open `psql -d postgres` as a PostgreSQL administrator and run the following. The `\password` command prompts for a password without adding it to SQL history.

```sql
CREATE ROLE file_management_app LOGIN;
\password file_management_app
CREATE DATABASE file_management_db OWNER file_management_app;
```

If you already have the database, use a PostgreSQL login that owns it or has access to the application tables and permission to create objects in the target schema. For a local setup that accepts your macOS login over TCP, `export DB_USERNAME="$(id -un)"` is sufficient when that role owns the database.

Copy `.env.example` to `.env` and fill in your own database settings. For shell loading, quote values containing shell metacharacters.

| Variable | Purpose | Default |
| --- | --- | --- |
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://127.0.0.1:5432/file_management_db` |
| `DB_USERNAME` | PostgreSQL login | Required |
| `DB_PASSWORD` | PostgreSQL password | Empty for local setups that permit it |
| `UPLOAD_DIR` | Directory for local file content | `uploads` relative to the working directory |

```sh
cp .env.example .env
# Edit .env before loading it.
set -a
source .env
set +a
./mvnw spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080). Keep the same database and upload directory when restarting. From IntelliJ, set the same environment variables in the run configuration and run `FileManagementSystemApplication`. The packaged JAR uses the same environment variables.

```sh
./mvnw package
java -jar target/file-management-system-0.0.1-SNAPSHOT.jar
```

Spring Boot runs [schema.sql](src/main/resources/schema.sql) on startup to create the five tables and indexes as needed. Hibernate validates the entity mappings. [data.sql](src/main/resources/data.sql) inserts Alice and Bob once by username. PostgreSQL stores users, file metadata, folders, shares, and database-backed file content. Keep credentials and uploads local. Git ignores `.env` and the default `uploads/` directory.

## Use the page

- Select **Alice (Owner)** to create a file. Enter a name, a short file type such as `txt` or `md`, and nonblank text. Pick **Local Storage** or **Database Storage**. The size is the number of UTF-8 bytes, and the creation date persists across restarts.
- Use **Search by name** for a case-insensitive substring search. Click a name to read its content and see its type, size, creation date, owner, folder, storage, and ID. **Open by file ID** lets you request a known ID directly.
- Use **New folder** in the sidebar and **Move to** in file details to organize files. Selecting a folder shows its accessible files. To delete a folder, first move its files to **Unfiled** or another folder, or delete its files. Deleting a nonempty folder returns HTTP 409.
- Alice can share only her own files. Choose **Bob** in **Share with** and click **Share**. Sharing the same file twice leaves one share. Bob sees only his shared files and folders that contain a file shared with him. He can try the owner actions in file details and see the server deny them.
- Names are unique per owner and per kind, ignoring case. Names start with a letter or digit and allow letters, digits, spaces, dots, underscores, parentheses, and hyphens. The server rejects path separators and trailing dots or spaces. File types are 1–16 ASCII letters or digits and are stored in lowercase.

The user dropdown sends an `X-User` header. The backend loads that user's persisted role and enforces access on each request. Anyone who can reach this local demo can impersonate Alice by sending her username. This is simulated identity for the assignment.

## Patterns

```text
Browser → FileController → ProtectionProxy → RealFileService → FileManager → FileStorage
                                                                              ├─ LocalFileStorage
                                                                              └─ DatabaseFileStorage
```

**Bridge.** `FileManager` is the abstraction and `FileStorage` is the implementor interface. Spring injects the registered implementations into `FileManager`, which holds them by `StorageType` and delegates save, read, and delete. `LocalFileStorage` uses Java NIO and stores content under a generated UUID in the configured directory. The displayed filename never becomes a disk path. `DatabaseFileStorage` writes bytes to PostgreSQL `file_contents.content` (`BYTEA`), keyed by file UUID. The file's persisted `storageType` selects the backend every time. Both implementations use the same interface, so selecting or substituting an implementation leaves the file management abstraction unchanged.

**Protection Proxy.** `FileAccessService` is the shared subject interface. `RealFileService` executes operations, and `ProtectionProxy` is the primary implementation injected into `FileController`. The proxy checks persisted role, ownership, and shares before delegating. Listing and searching use a visibility-filtered metadata query, and direct reads check permission before loading the content. Owner-only mutations check both role and actual ownership. The proxy decides **who may perform an operation**. The Bridge decides **where the file's bytes are stored**.

Database content changes and metadata changes share a PostgreSQL transaction. A rolled-back local create removes the disk object. A local delete removes the disk object after the metadata commits, so a rolled-back delete leaves the file readable. If disk cleanup fails or the process stops before cleanup, an unreferenced UUID file can remain in the upload directory.

## Project structure

| Path | Purpose |
| --- | --- |
| `src/main/java/com/example/filemanagementsystem/api` | Simulated users, input conventions, and HTTP errors |
| `src/main/java/com/example/filemanagementsystem/file` | File REST API, subject, proxy, real service, and Bridge abstraction |
| `src/main/java/com/example/filemanagementsystem/folder` | Folder REST API and service |
| `src/main/java/com/example/filemanagementsystem/model` and `repository` | JPA entities and persistence queries |
| `src/main/java/com/example/filemanagementsystem/storage` | Storage interface and persistent implementations |
| `src/main/resources/static` | Single-page website |
| `src/test/java` | Unit tests and live PostgreSQL HTTP workflow tests |
| `docs` | [UML source](docs/class-diagram.mmd), [rendered UML](docs/class-diagram.svg), and [test results](docs/TESTING.md) |

## Run tests

`./mvnw test` runs the Mockito unit tests. Without `TEST_DB_URL`, the PostgreSQL tests are skipped. For full verification, create a dedicated `file_management_test` database and use a role with permission to create schemas there. The tests create and remove their own unique schemas and temporary upload directories.

```sh
createdb file_management_test
export TEST_DB_URL=jdbc:postgresql://127.0.0.1:5432/file_management_test
export TEST_DB_USERNAME="$(id -un)"
# Set TEST_DB_PASSWORD if the test login requires it.
./mvnw clean verify
```

The integration suite boots Spring Boot on loopback with a random port and sends real HTTP requests against PostgreSQL. The restart test shuts down one application instance, starts another against the same schema and upload directory, and checks both storage backends and permission records.

## Demonstration

1. Select **Alice**. Click **New file**, create `assignment.txt` with `Hello, Сәлем 👋` as the content and `txt` as the type, and choose **Local Storage**.
2. Switch to **Bob**. The file disappears from the list. Click **Open** beside the populated **Open by file ID** field. The server returns **403**.
3. Switch to **Alice**. Open `assignment.txt`, select **Bob** in **Share with**, and click **Share**. Close file details and switch back to **Bob**.
4. Open the shared file. Check the original content and its byte size. Try **Rename**, **Delete file** (confirm), and **Share** with Alice. Each request returns **403**.
5. Switch to **Alice**. Rename the file to `assignment-renamed.txt`. Create a **Coursework** folder, open the file, and move it there. Select the folder to view its contents and try deleting the folder. The server returns **409**.
6. Create `database.txt` with **Database Storage**. Open both files to show the different backends. Share `database.txt` with Bob as well.
7. Stop the application and restart it with the same settings. Select **Bob** and open both files. Confirm their contents and the **Coursework** folder persisted.
8. Switch to **Alice**, delete `assignment-renamed.txt`, and then delete the now-empty **Coursework** folder. Delete `database.txt` to reset the demo.
