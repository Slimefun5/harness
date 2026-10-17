package io.github.slimefun5.harness;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Reflection over a server jar the agent is not compiled against. */
final class Reflect {

    private static final Object UNSAFE = loadUnsafe();

    private Reflect() {}

    /**
     * The first name that resolves, without initialising it.
     *
     * @implNote Initialising is what makes a class like {@code CraftServer} fail without a live server, so a
     *           presence check must never run static initialisers.
     */
    static Class<?> firstClass(String... names) {
        for (String name : names) {
            try {
                return Class.forName(name, false, Reflect.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError absent) {
                // try the next spelling
            }
        }
        return null;
    }

    static boolean exists(String name) {
        return firstClass(name) != null;
    }

    /** An instance whose constructor never ran, so none of a live server's start-up side effects happen. */
    static Object allocate(Class<?> type) throws ReflectiveOperationException {
        Method allocateInstance = UNSAFE.getClass().getMethod("allocateInstance", Class.class);
        return allocateInstance.invoke(UNSAFE, type);
    }

    static Object invokeStatic(String className, String method) throws ReflectiveOperationException {
        Method m = Class.forName(className).getMethod(method);
        m.setAccessible(true);
        return m.invoke(null);
    }

    /** Invokes the first of the named no-argument static methods that the class declares. */
    static Object invokeFirstStatic(String className, String... methods) throws ReflectiveOperationException {
        Class<?> type = Class.forName(className);
        for (String method : methods) {
            try {
                Method m = type.getMethod(method);
                m.setAccessible(true);
                return m.invoke(null);
            } catch (NoSuchMethodException absent) {
                // try the next name
            }
        }
        throw new NoSuchMethodException(className + " has none of " + String.join(", ", methods));
    }

    static Field field(Class<?> owner, String name) {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            try {
                Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException absent) {
                // keep walking up
            }
        }
        return null;
    }

    /** Sets the named field when it exists on this version; returns whether it did. */
    static boolean set(Object target, String name, Object value) throws IllegalAccessException {
        Field f = field(target.getClass(), name);
        if (f == null) {
            return false;
        }
        f.set(target, value);
        return true;
    }

    /**
     * Sets the first static field of {@code owner} whose type is {@code type}.
     *
     * @implNote Through {@code Unsafe} because a {@code static final} field cannot be set by reflection on
     *           any JDK, and the singleton fields this is for are final on some versions.
     */
    static void setStaticOfType(Class<?> owner, Class<?> type, Object value) throws ReflectiveOperationException {
        for (Field f : owner.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType() == type) {
                Class<?> unsafeClass = UNSAFE.getClass();
                Object base = unsafeClass.getMethod("staticFieldBase", Field.class).invoke(UNSAFE, f);
                long offset = (Long) unsafeClass.getMethod("staticFieldOffset", Field.class).invoke(UNSAFE, f);
                unsafeClass.getMethod("putObject", Object.class, long.class, Object.class).invoke(UNSAFE, base, offset, value);
                return;
            }
        }
    }

    static Object get(Object target, String name) throws IllegalAccessException {
        Field f = field(target.getClass(), name);
        return f == null ? null : f.get(target);
    }

    private static Object loadUnsafe() {
        try {
            Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("sun.misc.Unsafe is required to allocate the server", e);
        }
    }
}
