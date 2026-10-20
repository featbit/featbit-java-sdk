package co.featbit.server.integrations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Repeatable(FeatGate.Gates.class)
public @interface FeatGate {
    String flag();

    String value() default "";

    Variant[] variants() default {};

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Gates {
        FeatGate[] value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({})
    @interface Variant {
        String value();

        String method();
    }
}
