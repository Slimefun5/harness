package io.github.slimefun5.harness.probe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;

import io.github.slimefun5.harness.simulation.HarnessContext;
import io.github.slimefun5.harness.simulation.Scenario;
import io.github.slimefun5.harness.simulation.SimulatedPlayer;
import io.github.slimefun5.harness.simulation.Simulation;
import io.github.thebusybiscuit.slimefun5.core.services.localization.Language;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.PdcCompat;

/**
 * Opens every machine menu for one viewer per language and reads the title their window shows: never blank,
 * never a raw id, never an unfilled placeholder.
 */
final class MenuTitleScenario implements Scenario {

    private static final String CHECK = "menu-title";
    private static final Pattern RAW_ID = Pattern.compile("[A-Z0-9]+(_[A-Z0-9]+)+");
    private static final Pattern PLACEHOLDER = Pattern.compile("%[A-Za-z_-]+%|\\{\\d+}");

    private final Logger logger;

    MenuTitleScenario(Logger logger) {
        this.logger = logger;
    }

    @Override
    public String name() {
        return CHECK;
    }

    @Override
    public void run(Simulation simulation) throws InterruptedException {
        Findings findings = new Findings(logger);
        List<MachinePlacement.PlacedMenu> menus = MachinePlacement.all(simulation, findings);
        Map<String, SimulatedPlayer> viewers = viewerPerLanguage(simulation);
        for (MachinePlacement.PlacedMenu placed : menus) {
            Map<String, List<String>> languagesByProblem = new LinkedHashMap<>();
            for (Map.Entry<String, SimulatedPlayer> viewer : viewers.entrySet()) {
                HarnessContext.set(CHECK + " " + placed.preset.getID() + " for " + viewer.getKey());
                String problem = problemWith(placed, viewer.getValue());
                if (problem != null) {
                    languagesByProblem.computeIfAbsent(problem, ignored -> new ArrayList<>()).add(viewer.getKey());
                }
            }
            for (Map.Entry<String, List<String>> problem : languagesByProblem.entrySet()) {
                findings.report(CHECK, placed.preset.getID(), problem.getKey() + " in " + describe(problem.getValue(), viewers.size()));
            }
        }
        for (SimulatedPlayer viewer : viewers.values()) {
            simulation.quit(viewer);
        }
        logger.info("Menu titles checked: " + menus.size() + " menus x " + viewers.size() + " languages, " + findings.count() + " problem(s)");
    }

    private static Map<String, SimulatedPlayer> viewerPerLanguage(Simulation simulation) {
        Map<String, SimulatedPlayer> viewers = new LinkedHashMap<>();
        for (Language language : Slimefun.getLocalization().getLanguages()) {
            SimulatedPlayer viewer = simulation.join("title_" + language.getId(), language.getId());
            PdcCompat.set(viewer.player(), Slimefun.getLocalization().getKey(), "STRING", language.getId());
            viewers.put(language.getId(), viewer);
        }
        return viewers;
    }

    private static String problemWith(MachinePlacement.PlacedMenu placed, SimulatedPlayer viewer) {
        try {
            placed.menu.open(viewer.player());
            if (!viewer.hasWindowOpen()) {
                return "does not open";
            }
            String title = viewer.player().getOpenInventory().getTitle();
            String shown = title == null ? "" : ChatColor.stripColor(title).trim();
            if (shown.isEmpty()) {
                return "shows an empty title";
            }
            if (RAW_ID.matcher(shown).matches()) {
                return "shows the raw id \"" + shown + "\" as its title";
            }
            if (PLACEHOLDER.matcher(shown).find()) {
                return "shows the unfilled placeholder in \"" + shown + "\"";
            }
            return null;
        } catch (RuntimeException | LinkageError crash) {
            return "threw " + crash + " when opened";
        } finally {
            viewer.player().closeInventory();
        }
    }

    private static String describe(List<String> languages, int total) {
        return languages.size() == total ? "every language" : String.join(", ", languages);
    }
}
