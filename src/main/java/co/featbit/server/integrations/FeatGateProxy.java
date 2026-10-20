package co.featbit.server.integrations;

import co.featbit.commons.model.FBUser;
import co.featbit.server.exterior.FBClient;
import com.google.common.reflect.TypeToken;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class FeatGateProxy {
    private FeatGateProxy() {
    }

    public static <T> T create(Class<T> serviceType, T target, FBClient client) {
        Objects.requireNonNull(serviceType, "serviceType");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(client, "client");
        if (!serviceType.isInterface() || !serviceType.isInstance(target)) {
            throw new IllegalArgumentException("Target must implement the service interface");
        }

        Map<Method, Gate> gates = new HashMap<>();
        TypeToken<?> targetType = TypeToken.of(target.getClass());
        for (Method method : serviceType.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            try {
                Method implementation = resolveImplementation(targetType, method);
                FeatGate annotation = findAnnotation(serviceType, targetType, implementation);
                TypeToken<?> returnType = targetType.resolveType(method.getGenericReturnType());
                gates.put(method, new Gate(implementation, annotation, target.getClass(), returnType));
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Missing service method: " + method, e);
            }
        }

        Object proxy = Proxy.newProxyInstance(serviceType.getClassLoader(), new Class<?>[]{serviceType},
                (instance, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        switch (method.getName()) {
                            case "equals":
                                return instance == args[0];
                            case "hashCode":
                                return System.identityHashCode(instance);
                            default:
                                return target.toString();
                        }
                    }
                    Gate gate = gates.get(method);
                    Method selected = gate.method;
                    if (gate.flag != null) {
                        FBUser user = FBUserContextHolder.getCurrentUser();
                        if (user != null) {
                            String value = client.variation(gate.flag, user, null);
                            selected = gate.variants.getOrDefault(value, selected);
                        }
                    }
                    try {
                        return selected.invoke(target, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
        return serviceType.cast(proxy);
    }

    private static final class Gate {
        private final Method method;
        private final String flag;
        private final Map<String, Method> variants = new HashMap<>();

        private Gate(Method method, FeatGate annotation, Class<?> targetType, TypeToken<?> returnType) {
            this.method = method;
            method.setAccessible(true);
            flag = annotation == null ? null : annotation.flag();
            if (annotation == null) {
                return;
            }
            if (flag.trim().isEmpty()) {
                throw new IllegalArgumentException("Feature flag key must not be blank");
            }
            for (FeatGate.Variant variant : annotation.variants()) {
                Method implementation = findMethod(targetType, variant.method(), method.getParameterTypes());
                if (Modifier.isStatic(implementation.getModifiers()) || implementation.equals(method)
                        || !returnType.isSupertypeOf(TypeToken.of(targetType)
                                .resolveType(implementation.getGenericReturnType()))) {
                    throw new IllegalArgumentException("Incompatible variant method: " + implementation);
                }
                if (variants.put(variant.value(), implementation) != null) {
                    throw new IllegalArgumentException("Duplicate variant value: " + variant.value());
                }
                implementation.setAccessible(true);
            }
        }
    }

    private static Method resolveImplementation(TypeToken<?> targetType, Method method) throws NoSuchMethodException {
        Method implementation = targetType.getRawType().getMethod(method.getName(), method.getParameterTypes());
        if (!implementation.isBridge()) {
            return implementation;
        }
        for (TypeToken<?> ancestor : targetType.getTypes()) {
            try {
                Method declaration = ancestor.getRawType().getDeclaredMethod(method.getName(), method.getParameterTypes());
                if (declaration.isBridge() || Modifier.isPrivate(declaration.getModifiers())
                        || Modifier.isStatic(declaration.getModifiers())) {
                    continue;
                }
                Type[] parameters = declaration.getGenericParameterTypes();
                Class<?>[] resolved = new Class<?>[parameters.length];
                for (int i = 0; i < parameters.length; i++) {
                    resolved[i] = targetType.resolveType(parameters[i]).getRawType();
                }
                Method candidate = targetType.getRawType().getMethod(method.getName(), resolved);
                if (!candidate.isBridge()) {
                    return candidate;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        return implementation;
    }

    private static FeatGate findAnnotation(Class<?> serviceType, TypeToken<?> targetType, Method implementation)
            throws NoSuchMethodException {
        FeatGate annotation = implementation.getAnnotation(FeatGate.class);
        if (annotation != null) {
            return annotation;
        }
        for (Method declaration : serviceType.getMethods()) {
            FeatGate candidate = declaration.getAnnotation(FeatGate.class);
            if (candidate == null || Modifier.isStatic(declaration.getModifiers())
                    || !declaration.getName().equals(implementation.getName())
                    || !resolveImplementation(targetType, declaration).equals(implementation)) {
                continue;
            }
            if (annotation != null && !annotation.equals(candidate)) {
                throw new IllegalArgumentException("Conflicting feature gates for method: " + implementation);
            }
            annotation = candidate;
        }
        return annotation;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>[] parameterTypes) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
            }
        }
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Missing variant method: " + name, e);
        }
    }
}
