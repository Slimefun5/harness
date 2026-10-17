package io.github.slimefun5.harness.probe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import io.github.slimefun5.harness.simulation.HarnessContext;
import io.github.slimefun5.harness.simulation.Scenario;
import io.github.slimefun5.harness.simulation.SimulatedPlayer;
import io.github.slimefun5.harness.simulation.Simulation;
import io.github.thebusybiscuit.slimefun5.core.services.localization.Language;
import io.github.thebusybiscuit.slimefun5.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun5.utils.compatibility.PdcCompat;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;

/**
 * Opens every machine menu for one viewer per language and reads the title their window shows: never blank,
 * never a raw id, never an unfilled placeholder, and always the title core resolves for that viewer.
 *
 * @implNote The harness has no network channel, so a title core writes into the open-window packet is not
 *           seen here; only {@code InventoryView#setTitle} reaches the simulated window, which exists from
 *           1.19.4. Below that a correct server still fails this check until the harness carries real packets.
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
            String intended = strip(intendedTitle(placed, viewer.player()));
            placed.menu.open(viewer.player());
            if (!viewer.hasWindowOpen()) {
                return "does not open";
            }
            String shown = strip(viewer.player().getOpenInventory().getTitle());
            if (shown.isEmpty()) {
                return "shows an empty title";
            }
            if (RAW_ID.matcher(shown).matches()) {
                return "shows the raw id \"" + shown + "\" as its title";
            }
            if (PLACEHOLDER.matcher(shown).find()) {
                return "shows the unfilled placeholder in \"" + shown + "\"";
            }
            if (!shown.equals(intended)) {
                return "shows \"" + shown + "\" instead of the title core resolves for the viewer";
            }
            return null;
        } catch (RuntimeException | LinkageError crash) {
            return "threw " + crash + " when opened";
        } finally {
            viewer.player().closeInventory();
        }
    }

    /** What core resolves this viewer's title to; the shared inventory's own title when it needs no change. */
    private static String intendedTitle(MachinePlacement.PlacedMenu placed, Player viewer) {
        String perViewer = placed.menu instanceof BlockMenu
            ? Slimefun.getMenuTranslationService().getTitleFor((BlockMenu) placed.menu, viewer)
            : null;
        return perViewer != null ? perViewer : ChatColor.translateAlternateColorCodes('&', placed.preset.getTitle());
    }

    private static String strip(String title) {
        return title == null ? "" : ChatColor.stripColor(title).trim();
    }

    private static String describe(List<String> languages, int total) {
        return languages.size() == total ? "every language" : String.join(", ", languages);
    }
}
