package io.github.slimefun5.harness;

import static net.bytebuddy.matcher.ElementMatchers.isArray;
import static net.bytebuddy.matcher.ElementMatchers.isSubTypeOf;
import static net.bytebuddy.matcher.ElementMatchers.nameMatches;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import java.lang.instrument.Instrumentation;
import java.util.Collection;
import java.util.UUID;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.implementation.MethodDelegation;

/**
 * Rewrites, as the classes load, the few server methods that read a running game. Everything else stays
 * the real code.
 */
public final class HarnessAgent {

    private HarnessAgent() {}

    public static void premain(String arguments, Instrumentation instrumentation) {
        new AgentBuilder.Default()
            .disableClassFormatChanges()
            .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
            .with(AgentBuilder.Listener.StreamWriting.toSystemError().withErrorsOnly())
            .type(nameMatches("org\\.bukkit\\.craftbukkit(\\.v[^.]+)?\\.CraftServer"))
            .transform((builder, type, classLoader, module, domain) -> builder
                .method(named("isPrimaryThread").and(takesArguments(0)))
                .intercept(MethodDelegation.to(ServerOverrides.PrimaryThread.class))
                .method(named("getOnlinePlayers").and(takesArguments(0)).and(returns(isSubTypeOf(Collection.class))))
                .intercept(MethodDelegation.to(ServerOverrides.OnlinePlayers.class))
                .method(named("getOnlinePlayers").and(takesArguments(0)).and(returns(isArray())))
                .intercept(MethodDelegation.to(ServerOverrides.OnlinePlayerArray.class))
                .method(named("getPlayer").or(named("getPlayerExact")).and(takesArguments(String.class).or(takesArguments(UUID.class))))
                .intercept(MethodDelegation.to(ServerOverrides.PlayerLookup.class))
                .method(named("getOfflinePlayer").and(takesArguments(String.class).or(takesArguments(UUID.class))))
                .intercept(MethodDelegation.to(ServerOverrides.OfflinePlayerLookup.class))
                .method(named("getConsoleSender").and(takesArguments(0)))
                .intercept(MethodDelegation.to(ServerOverrides.ConsoleSender.class))
                .method(named("getPluginsFolder").and(takesArguments(0)))
                .intercept(MethodDelegation.to(ServerOverrides.PluginsFolder.class)))
            .type(named("io.papermc.paper.util.TickThread").or(named("ca.spottedleaf.moonrise.common.util.TickThread")))
            .transform((builder, type, classLoader, module, domain) -> builder
                .method(named("isTickThread").and(takesArguments(0)))
                .intercept(MethodDelegation.to(ServerOverrides.TickThreadCheck.class)))
            .type(named("io.papermc.paper.command.brigadier.PaperCommands"))
            .transform((builder, type, classLoader, module, domain) -> builder
                .method(named("getDispatcherInternal").and(takesArguments(0)))
                .intercept(MethodDelegation.to(ServerOverrides.CommandDispatcherHolder.class)))
            .installOn(instrumentation);
    }
}
