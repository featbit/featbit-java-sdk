package co.featbit.server.integrations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FeatGate {
    String flag();

    Variant[] variants();

    @Retention(RetentionPolicy.RUNTIME)
    @Target({})
    @interface Variant {
        String value();

        String method();
    }
}
