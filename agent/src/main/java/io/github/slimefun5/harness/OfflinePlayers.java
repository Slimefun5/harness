package io.github.slimefun5.harness;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.OfflinePlayer;

/**
 * Players who never joined, as an offline-mode server sees them: a UUID and, when known, a name.
 *
 * @implNote The real {@code CraftOfflinePlayer} reads the first world's player data (1.8) or the player list's
 *           storage (later), neither of which a harness has. A call this facade cannot answer logs a
 *           {@code [gap]} line, which fails the run, rather than inventing an answer.
 */
final class OfflinePlayers {

    private static final Logger LOG = Logger.getLogger("Minecraft");
    private static final Map<UUID, OfflinePlayer> KNOWN = new ConcurrentHashMap<>();

    private OfflinePlayers() {}

    static OfflinePlayer byId(UUID uniqueId) {
        return KNOWN.computeIfAbsent(uniqueId, id -> facade(id, null));
    }

    /** Offline mode derives the UUID from the name, so this makes no profile lookup. */
    static OfflinePlayer byName(String name) {
        UUID uniqueId = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        return KNOWN.computeIfAbsent(uniqueId, id -> facade(id, name));
    }

    private static OfflinePlayer facade(UUID uniqueId, String name) {
        InvocationHandler handler = new Answers(uniqueId, name);
        return (OfflinePlayer) Proxy.newProxyInstance(OfflinePlayer.class.getClassLoader(), new Class<?>[] { OfflinePlayer.class }, handler);
    }

    private static final class Answers implements InvocationHandler {

        private final UUID uniqueId;
        private final String name;

        Answers(UUID uniqueId, String name) {
            this.uniqueId = uniqueId;
            this.name = name;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            switch (method.getName()) {
                case "getUniqueId":
                    return uniqueId;
                case "getName":
                    return name;
                case "isOnline":
                case "isConnected":
                case "hasPlayedBefore":
                case "isOp":
                case "isBanned":
                case "isWhitelisted":
                    return false;
                case "getPlayer":
                    return null;
                case "getFirstPlayed":
                case "getLastPlayed":
                case "getLastLogin":
                case "getLastSeen":
                    return 0L;
                case "equals":
                    return proxy == arguments[0];
                case "hashCode":
                    return uniqueId.hashCode();
                case "toString":
                    return "HarnessOfflinePlayer{" + uniqueId + "}";
                default:
                    return unanswered(method, arguments);
            }
        }

        private static Object unanswered(Method method, Object[] arguments) {
            String parameters = arguments == null ? "" : Arrays.toString(method.getParameterTypes());
            LOG.log(Level.SEVERE, "[gap] OfflinePlayer.{0}({1})", new Object[] { method.getName(), parameters });
            return defaultValue(method.getReturnType());
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive() || type == void.class) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == char.class) {
                return '\0';
            }
            if (type == long.class) {
                return 0L;
            }
            if (type == float.class) {
                return 0F;
            }
            if (type == double.class) {
                return 0D;
            }
            if (type == byte.class) {
                return (byte) 0;
            }
            if (type == short.class) {
                return (short) 0;
            }
            return 0;
        }
    }
}
