# Same-name method feature gates

This optional `javac` plugin supports multiple implementations with the same method name and parameters in one class. The SDK's existing proxy API and build do not require it.

From the repository root:

```sh
mvn "-Dgpg.skip=true" "-Dmaven.javadoc.skip=true" install
mvn -f tools/featgate-compiler/pom.xml install
mvn -f examples/spring-boot-compiler/pom.xml spring-boot:run
```

Use `co.featbit:featbit-gate-compiler:1.4.5` on the annotation processor path and explicitly enable `-Xplugin:FeatGate`. Copy the compiler configuration from the [Spring Boot example](../../examples/spring-boot-compiler/pom.xml). On JDK 9+, fork `javac` and pass its five `-J--add-exports` options. Bind each service with `FeatGateSupport.bind(service, client)`; no runtime compiler or JVM agent is needed.

```java
@FeatGate(flag = "getFirstName", value = "default")
@FeatGate(flag = "getFirstName", value = "v1")
private String getFirstName() {
    return "albert";
}

@FeatGate(flag = "getFirstName", value = "v2")
private String getFirstName() {
    return "ALBERT";
}
```

Methods in a group must be concrete instance methods with identical modifiers, return types, type parameters, throws declarations, and non-gate annotations. Use string literals for `flag` and `value`, one flag per group, unique values, and exactly one `value = "default"` annotation. Static methods, interfaces, and records are unsupported. Names beginning with `$featbit$` are reserved.

Calls, including self-invocation, evaluate the flag for the current user. Missing bindings, missing user context, and unmatched values select the default body. A sole declaration needs no dispatch and is left unchanged.

After rollout, delete the old body and the retained body's gate annotations. Keep the plugin enabled: it also makes a retained private method public when needed to implement an interface, including inherited generic interfaces. Callers and binding code stay unchanged.

The plugin uses internal compiler APIs and has been verified on JDK 8 and 21; CI is also configured for JDK 17. Other compiler implementations and future JDK compatibility are not guaranteed. IDEs may report duplicate-method or access errors; delegate builds to Maven. See the [runnable example](../../examples/spring-boot-compiler/README.md).
