# Spring Boot Same-Name Feature Gates

Requires JDK 17+ and Maven. This unreleased example requires the SDK and the optional compiler plugin from this checkout. Run from the repository root:

```sh
mvn "-Dgpg.skip=true" "-Dmaven.javadoc.skip=true" install
mvn -f tools/featgate-compiler/pom.xml install
mvn -f examples/spring-boot-compiler/pom.xml spring-boot:run
```

`DefaultUserService` declares private methods with identical names and parameters. The opt-in `FeatGate` javac plugin transforms them before Java checks duplicate declarations. The Maven configuration supplies the plugin and the required compiler exports. IDE source analysis may still report duplicate methods.

`Application.userService` binds the SDK client directly to the service with `FeatGateSupport.bind`. Both interface calls and calls within `fullName()` select the current user's variant. `UserContextFilter` sets and clears the user for each request; use an authenticated user key in your application. Asynchronous work requires explicit context propagation and cleanup.

The example uses the real SDK with local flags in offline mode:

```sh
curl http://localhost:8080/name
curl -H "X-User-Key: formatted" http://localhost:8080/name
curl -H "X-User-Key: first-only" http://localhost:8080/name
curl -H "X-User-Key: last-only" http://localhost:8080/name
```

| X-User-Key | firstName | lastName | fullName |
| --- | --- | --- | --- |
| Omitted or unknown | albert | einstein | albert einstein |
| formatted | Albert | E. | Albert E. |
| first-only | Albert | einstein | Albert einstein |
| last-only | albert | E. | albert E. |

After the rollout, remove the old method and the surviving method's annotation. The new method keeps its name, body, and callers:

```diff
-    @FeatGate(flag = "getFirstName", value = "v1")
-    @FeatGate(flag = "getFirstName", value = "default")
-    private String getFirstName() {
-        return "albert";
-    }
-
-    @FeatGate(flag = "getFirstName", value = "v2")
     private String getFirstName() {
         return "Albert";
     }
```

Keep the compiler plugin enabled: it exposes the private implementation of the interface method even after its gate is removed. The existing [proxy example](../spring-boot) remains available without compiler transformation.

For a live FeatBit instance, set `FEATBIT_OFFLINE=false`, `FEATBIT_ENV_SECRET`, `FEATBIT_STREAMING_URL`, and `FEATBIT_EVENT_URL`. Create string flags `getFirstName` and `getLastName` with values `v1` and `v2`.

Run tests with `mvn -f examples/spring-boot-compiler/pom.xml test`.
