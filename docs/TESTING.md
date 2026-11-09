# Verification results

Verified on November 9, 2026 with Temurin Java 21.0.10, Maven 3.10.0, Spring Boot 4.1.2, and local PostgreSQL 16.15. The integration suite used `file_management_test` with unique schemas and temporary upload directories. No H2 database was used. The unit tests use Mockito and require no database.

```sh
TEST_DB_URL=jdbc:postgresql://127.0.0.1:5432/file_management_test \
TEST_DB_USERNAME="$(id -un)" \
./mvnw clean verify
```

Maven reported **12 passed, 0 failures, 0 errors, and 0 skipped**. The executable JAR was built successfully. Set `TEST_DB_PASSWORD` if the test login requires a password. Running without `TEST_DB_URL` skips the seven PostgreSQL tests.

| ID | Scenario | Steps or input | Expected result | Observed result | Status |
| --- | --- | --- | --- | --- | --- |
| U-01 | Proxy denies a private read | Mock Bob's viewer role and a file with no share | HTTP 403 and zero real-subject calls | Proxy raised 403 and Mockito verified zero calls | Pass |
| U-02 | Proxy delegates a shared read | Return an existing share for Bob | Real subject receives the read | Proxy returned the result of `RealFileService.read` | Pass |
| U-03 | Proxy denies viewer mutations | Try rename, delete, share, and move as Bob | Zero real-subject calls | All four calls raised access errors and never reached the subject | Pass |
| U-04 | Ownership required | Try deleting a different user's file with an Owner role | HTTP 403 and zero real-subject calls | Proxy raised 403 and Mockito verified zero calls | Pass |
| U-05 | Bridge routes all content operations | Inject two `FileStorage` test doubles in reverse order | Each file uses its recorded backend for save, read, and delete | Both mocks received the expected UUID and bytes | Pass |
| P-01 | Page, assets, seeded users | Request `/`, JS, CSS, and `/api/users` | HTTP 200 and two users | Page and assets responded with 200, and exactly two users were returned | Pass |
| P-02 | File lifecycle, metadata, and both storage backends | Create a disk file and a database file with Unicode text, then list, search, read, rename, and delete | Exact UTF-8 bytes, persisted metadata, correct storage, and removal | HTTP responses showed correct text, size, timestamp, type, and owner. The UUID object and PostgreSQL bytes matched. Deletions removed both storage objects, and later reads returned 404 | Pass |
| P-03 | Read-only sharing and private visibility | Bob opens a known private ID, lists and searches, then Alice shares twice and Bob retries operations | Private files stay hidden, shared reads succeed, and viewer mutations return 403 | Private read returned 403. List and search hid the file until sharing. The shared read returned correct text, and rename, delete, share, and move returned 403 | Pass |
| P-04 | Folder lifecycle and visibility | Create a folder, move a file, list contents as Alice and Bob before and after sharing, then delete before and after moving out | Owner sees contents, Bob sees only shared contents, and nonempty deletion returns 409 | Contents matched visibility rules. Nonempty deletion returned 409, Bob's folder mutations returned 403, and empty deletion returned 204 | Pass |
| P-05 | Validation, missing records, and duplicates | Send traversal, blank content, bad file/storage types, missing identity, invalid IDs, and duplicate names | HTTP 400, 403, 404, or 409 as appropriate | Invalid input returned 400, viewer creation returned 403, missing resources returned 404, and duplicate create and rename returned 409 | Pass |
| P-06 | Local storage read error | Remove a test disk object before reading its metadata ID | HTTP 500 with a storage error message | HTTP 500 included “Could not read local file content” | Pass |
| P-07 | Application restart | Shut down an application instance after saving and sharing both storage types. Start a second instance with the same schema and upload directory | Files, metadata, folders, and shares survive, with no duplicate users | Bob read both original contents. Metadata and folder membership were preserved, and exactly two users remained | Pass |
| B-01 | Chrome demonstration on port 8080 | Run the README workflow in Chrome with the packaged JAR and `file_management_db`. Restart the Java process, repeat shared reads, check the page at 390 px, then delete the demo data | Both storage modes work through the UI, expected 403 and 409 messages are visible, data survives restart, and the page fits the mobile viewport | Confirmed the complete workflow, both original contents after restart, 403 and 409 feedback, no page-wide overflow at 390 px, and successful cleanup | Pass |
