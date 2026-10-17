package io.github.slimefun5.harness;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;

/**
 * The console a plugin talks to through {@code Bukkit.getConsoleSender()}: an operator with every permission
 * whose messages go to the server log.
 *
 * @implNote A proxy, so it implements whatever the running version's {@code ConsoleCommandSender} declares,
 *           including Paper's Adventure methods, without being compiled against any of them.
 */
final class HarnessConsole {

    private static final Logger LOG = Logger.getLogger("Minecraft");
    private static ConsoleCommandSender instance;

    private HarnessConsole() {}

    static synchronized ConsoleCommandSender get() {
        if (instance == null) {
            instance = (ConsoleCommandSender) Proxy.newProxyInstance(HarnessConsole.class.getClassLoader(),
                new Class<?>[] { ConsoleCommandSender.class }, (proxy, method, args) -> answer(proxy, method, args));
        }
        return instance;
    }

    private static Object answer(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "sendMessage":
            case "sendRawMessage":
                LOG.info(text(args));
                return null;
            case "getName":
                return "CONSOLE";
            case "isOp":
            case "hasPermission":
            case "isPermissionSet":
                return true;
            case "getServer":
                return Bukkit.getServer();
            case "equals":
                return proxy == args[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return "HarnessConsole";
            default:
                return defaultValue(method.getReturnType());
        }
    }

    private static String text(Object[] args) {
        Object last = args[args.length - 1];
        return last instanceof Object[] ? String.join("\n", Arrays.asList((String[]) last)) : String.valueOf(last);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class || type == long.class || type == short.class || type == byte.class) {
            return 0;
        }
        if (type == double.class || type == float.class) {
            return 0.0;
        }
        return null;
    }
}
