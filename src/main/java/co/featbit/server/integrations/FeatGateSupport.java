package co.featbit.server.integrations;

import co.featbit.commons.model.FBUser;
import co.featbit.server.exterior.FBClient;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FeatGateSupport {
    private FeatGateSupport() {
    }

    public static <T> T bind(T target, FBClient client) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(client, "client");
        List<Field> fields = new ArrayList<>();
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!field.isAnnotationPresent(Client.class)) {
                    continue;
                }
                if (field.getType() != FBClient.class || Modifier.isStatic(field.getModifiers())
                        || !Modifier.isVolatile(field.getModifiers())) {
                    throw new IllegalArgumentException("Invalid feature gate client field: " + field);
                }
                fields.add(field);
            }
        }
        for (Field field : fields) {
            field.setAccessible(true);
        }
        try {
            for (Field field : fields) {
                field.set(target, client);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not bind feature gate client", e);
        }
        return target;
    }

    public static String variation(FBClient client, String flag, String defaultValue) {
        FBUser user = FBUserContextHolder.getCurrentUser();
        return client == null || user == null ? defaultValue : client.variation(flag, user, defaultValue);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Client {
    }
}
