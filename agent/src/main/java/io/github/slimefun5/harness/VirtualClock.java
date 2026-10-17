package io.github.slimefun5.harness;

import java.lang.reflect.Method;

/** Advances the real {@code CraftScheduler} one tick at a time as fast as the tasks run, never by waiting. */
final class VirtualClock {

    private final Object scheduler;
    private final Method heartbeat;
    private int tick;

    VirtualClock(Object scheduler) {
        this.scheduler = scheduler;
        this.heartbeat = heartbeatMethod(scheduler.getClass());
    }

    void advance(int ticks, WarningLog log) {
        for (int i = 0; i < ticks; i++) {
            tick++;
            try {
                if (heartbeat.getParameterCount() == 0) {
                    heartbeat.invoke(scheduler);
                } else {
                    heartbeat.invoke(scheduler, tick);
                }
            } catch (ReflectiveOperationException failure) {
                log.harnessFailure("scheduler tick " + tick, failure.getCause() != null ? failure.getCause() : failure);
            }
        }
    }

    /** The int-argument form on older versions; Paper later dropped the argument. */
    private static Method heartbeatMethod(Class<?> schedulerClass) {
        for (Method method : schedulerClass.getMethods()) {
            if (method.getName().equals("mainThreadHeartbeat") && method.getParameterCount() <= 1) {
                return method;
            }
        }
        throw new IllegalStateException("No mainThreadHeartbeat on " + schedulerClass.getName());
    }
}
