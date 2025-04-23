#### Automated tests

These cover most functionality, except note editing (CKEditor text input is not easily automated).

Check CI status, or run tests locally:

- Run `./app/test/setup-test-server.sh`
- Run `while true; do adb reverse tcp:8080 tcp:8080; sleep 1; done`
- Run Gradle task `:app:pixel9api35DebugAndroidTest`
- Run `./app/test/compare-test-images.sh`

#### Manual tests

##### (if database version is incremented)

Check that the migration works correctly.

##### (always)

- Check that sync pull works.
- Check that synced changes show up correctly.
- Make a trivial change: text note / canvas note.
- Check that sync push works.
- Check that synced changes show up correctly.
