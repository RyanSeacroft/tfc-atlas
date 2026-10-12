package dev.ryan.tfcatlas.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/** The five climate families use the codes defined by TFC's existing classifier. */
public final class ClimateGroups {
    private ClimateGroups() {}

    public static List<BiomeGroups.Group> groups() {
        String[] names = {"Tropical", "Arid", "Temperate", "Continental", "Polar"};
        String prefixes = "ABCDE";
        List<BiomeGroups.Group> groups = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            var members = new TreeSet<String>();
            for (String code : ClimateZones.CODES) {
                if (code.charAt(0) == prefixes.charAt(i)) {
                    members.add(
                            ClimateZones.label(code).toLowerCase(Locale.ROOT).replace(' ', '_'));
                }
            }
            if (!members.isEmpty()) {
                groups.add(
                        new BiomeGroups.Group(
                                names[i].toLowerCase(Locale.ROOT), names[i] + " zones", members));
            }
        }
        return groups.stream()
                .sorted(
                        java.util.Comparator.comparing(
                                BiomeGroups.Group::label, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
