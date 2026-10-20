# Spring Boot Method Feature Gates

Requires JDK 17+ and Maven. Run from the repository root to use the SDK changes in this checkout:

```sh
mvn -Dgpg.skip=true install
mvn -f examples/spring-boot/pom.xml spring-boot:run
```

The example uses the real SDK with local `flags.json` data in offline mode. No FeatBit server is required.

```sh
curl http://localhost:8080/name
curl -H "X-User-Key: formatted" http://localhost:8080/name
curl -H "X-User-Key: first-only" http://localhost:8080/name
curl -H "X-User-Key: last-only" http://localhost:8080/name
```

| X-User-Key | firstName | lastName |
| --- | --- | --- |
| Omitted or unknown | albert | einstein |
| formatted | Albert | E. |
| first-only | Albert | einstein |
| last-only | albert | E. |

`Application.userService` registers the `FeatGateProxy` as a Spring bean. `NameController` injects the service interface; `DefaultUserService` contains the gates. `UserContextFilter` sets the current user and clears it in `finally`. In your application, obtain the user key from the authenticated user. Calls within the service bypass the proxy. Asynchronous work requires explicit user-context propagation and cleanup.

To use a running FeatBit instance, set `FEATBIT_OFFLINE=false`, `FEATBIT_ENV_SECRET`, `FEATBIT_STREAMING_URL`, and `FEATBIT_EVENT_URL`. Create string flags `getFirstName` and `getLastName` with values `v1` and `v2`; `v1` uses the original methods, and `v2` uses the formatted methods.

Run the example tests with `mvn -f examples/spring-boot/pom.xml test`.
