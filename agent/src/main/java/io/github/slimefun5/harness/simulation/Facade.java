package io.github.slimefun5.harness.simulation;

import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isEquals;
import static net.bytebuddy.matcher.ElementMatchers.isHashCode;
import static net.bytebuddy.matcher.ElementMatchers.not;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Implements a Bukkit interface or abstract class, as the running version declares it, by routing each call
 * to a plain handler object's method of the same name and compatible parameters. A call no handler method
 * answers is a {@link HarnessGap}, never a silent default.
 *
 * @implNote Generated at run time against the version's own types because their shape differs per version
 *           ({@code InventoryView} is a class up to 1.20.6 and an interface after), so no compiled
 *           implementation could cover all of them.
 */
public final class Facade {

    private static final String DISPATCHER_FIELD = "harness$dispatcher";

    /** Calls whose only effect is on a client the harness does not have. */
    private static final Set<String> CLIENT_ONLY = new HashSet<>(Arrays.asList(
        "playSound", "stopSound", "playEffect", "spawnParticle", "playNote", "sendTitle", "resetTitle", "showTitle",
        "sendActionBar", "sendBlockChange", "sendSignChange", "updateInventory", "setPlayerListName",
        "sendPluginMessage", "showDemoScreen", "sendEquipmentChange"));

    private static final Map<Class<?>, Class<?>> GENERATED = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<Method, Optional<Method>>> HANDLER_METHODS = new ConcurrentHashMap<>();

    private Facade() {}

    /** An object a handler hands calls on to when it does not answer them itself. */
    public interface Delegating {

        /** The object that answers {@code method}, which must declare or inherit it, or {@code null}. */
        Object delegateFor(Method method);
    }

    /** A handler that answers whole families of calls itself, such as an entity's properties. */
    public interface Answering {

        /** The call's result, or {@link Facade#UNANSWERED} when this handler cannot answer it. */
        Object answer(Method method, Object[] arguments);
    }

    public static final Object UNANSWERED = new Object();

    public static <T> T create(Class<T> type, Object handler) {
        Class<?> generated = GENERATED.computeIfAbsent(type, ignored -> generate(type, handler.getClass()));
        try {
            Object instance = generated.getDeclaredConstructor().newInstance();
            generated.getField(DISPATCHER_FIELD).set(instance, new Dispatcher(type, handler));
            return type.cast(instance);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not create a " + type.getName() + " facade", failure);
        }
    }

    /**
     * @implNote The generated class is per type, so the methods it overrides are fixed by the first handler
     *           class: abstract methods always, concrete ones only when that handler declares them.
     */
    private static Class<?> generate(Class<?> type, Class<?> handlerClass) {
        ElementMatcher.Junction<MethodDescription> overridden = isAbstract()
            .or(declaredByHandler(handlerClass))
            .and(not(isEquals().or(isHashCode())));
        return new ByteBuddy()
            .subclass(type)
            .name(Facade.class.getPackage().getName() + ".generated." + type.getSimpleName() + "Facade")
            .defineField(DISPATCHER_FIELD, Dispatcher.class, Visibility.PUBLIC)
            .method(overridden)
            .intercept(MethodDelegation.toField(DISPATCHER_FIELD))
            .make()
            .load(type.getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
            .getLoaded();
    }

    private static ElementMatcher.Junction<MethodDescription> declaredByHandler(Class<?> handlerClass) {
        Set<String> signatures = new HashSet<>();
        for (Method method : handlerClass.getDeclaredMethods()) {
            signatures.add(method.getName() + "/" + method.getParameterCount());
        }
        return new ElementMatcher.Junction.AbstractBase<MethodDescription>() {
            @Override
            public boolean matches(MethodDescription target) {
                return signatures.contains(target.getName() + "/" + target.getParameters().size());
            }
        };
    }

    /** Routes one facade's calls to its handler. */
    public static final class Dispatcher {

        private final Class<?> type;
        private final Object handler;

        Dispatcher(Class<?> type, Object handler) {
            this.type = type;
            this.handler = handler;
        }

        @RuntimeType
        public Object dispatch(@Origin Method method, @AllArguments Object[] arguments) throws Throwable {
            Optional<Method> answer = HANDLER_METHODS
                .computeIfAbsent(handler.getClass(), ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(method, target -> answerFor(handler.getClass(), target));
            try {
                if (answer.isPresent()) {
                    return answer.get().invoke(handler, arguments);
                }
                Object delegate = handler instanceof Delegating ? ((Delegating) handler).delegateFor(method) : null;
                if (delegate != null) {
                    method.setAccessible(true);
                    return method.invoke(delegate, arguments);
                }
                if (handler instanceof Answering) {
                    Object result = ((Answering) handler).answer(method, arguments);
                    if (result != UNANSWERED) {
                        return result;
                    }
                }
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
            if (method.getReturnType() == void.class && CLIENT_ONLY.contains(method.getName())) {
                return null;
            }
            throw HarnessGap.of(type, method);
        }
    }

    /** The handler method answering {@code target}: same name and arity, every parameter accepting the target's. */
    static Optional<Method> answerFor(Class<?> handlerClass, Method target) {
        Method assignable = null;
        for (Method candidate : handlerClass.getDeclaredMethods()) {
            if (!candidate.getName().equals(target.getName()) || candidate.getParameterCount() != target.getParameterCount()
                    || Modifier.isStatic(candidate.getModifiers()) || candidate.isSynthetic()) {
                continue;
            }
            if (Arrays.equals(candidate.getParameterTypes(), target.getParameterTypes())) {
                candidate.setAccessible(true);
                return Optional.of(candidate);
            }
            if (assignable == null && accepts(candidate.getParameterTypes(), target.getParameterTypes())) {
                assignable = candidate;
            }
        }
        if (assignable != null) {
            assignable.setAccessible(true);
        }
        return Optional.ofNullable(assignable);
    }

    private static boolean accepts(Class<?>[] declared, Class<?>[] actual) {
        for (int i = 0; i < declared.length; i++) {
            if (!wrap(declared[i]).isAssignableFrom(wrap(actual[i]))) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        return type == char.class ? Character.class : Void.class;
    }
}
