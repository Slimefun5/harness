package io.github.slimefun5.harness.simulation;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A call into a simulated object that the harness does not answer yet. Each distinct method is logged once
 * at SEVERE, so the run fails until it is simulated: a check that could not really run must never pass.
 */
public final class HarnessGap extends UnsupportedOperationException {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger("Harness");
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private HarnessGap(String message) {
        super(message);
    }

    static HarnessGap of(Class<?> facadeType, Method method) {
        String signature = facadeType.getSimpleName() + "." + method.getName() + "("
            + Arrays.stream(method.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(", ")) + ")";
        HarnessGap gap = new HarnessGap("[gap] " + signature + " is not simulated");
        if (REPORTED.add(signature)) {
            LOGGER.log(Level.SEVERE, gap.getMessage() + HarnessContext.suffix(), gap);
        }
        return gap;
    }
}
