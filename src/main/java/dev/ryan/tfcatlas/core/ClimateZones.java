package dev.ryan.tfcatlas.core;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Stable TFC classification identifiers; values are obtained from TFC's classifier. */
public final class ClimateZones {
    public static final List<String> CODES =
            List.of(
                    "AF", "AM", "AW", "AS", "BWH", "BWK", "BSH", "BSK", "CSA", "CSB", "CSC", "CWA",
                    "CWB", "CWC", "CFA", "CFB", "CFC", "DSA", "DSB", "DSC", "DSD", "DWA", "DWB",
                    "DWC", "DWD", "DFA", "DFB", "DFC", "DFD", "ET", "EF");

    public static String label(String code) {
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "AF" -> "Humid Tropical";
            case "AM" -> "Tropical Monsoon";
            case "AW" -> "Tropical Wet/Dry";
            case "AS" -> "Tropical Dry/Wet";
            case "BWH" -> "Hot Desert";
            case "BWK" -> "Cold Desert";
            case "BSH" -> "Hot Semi-Arid";
            case "BSK" -> "Cold Semi-Arid";
            case "CSA" -> "Coastal Subtropical";
            case "CSB" -> "Coastal";
            case "CSC" -> "Cold Coastal";
            case "CWA" -> "Monsoonal Subtropical";
            case "CWB" -> "Monsoonal Temperate";
            case "CWC" -> "Cold Monsoonal Temperate";
            case "CFA" -> "Oceanic Subtropical";
            case "CFB" -> "Oceanic";
            case "CFC" -> "Cold Oceanic";
            case "DSA" -> "Coastal Continental";
            case "DSB" -> "Cold Coastal Continental";
            case "DSC" -> "Coastal Subarctic";
            case "DSD" -> "Coastal Cold Subarctic";
            case "DWA" -> "Monsoonal Continental";
            case "DWB" -> "Cold Monsoonal Continental";
            case "DWC" -> "Monsoonal Subarctic";
            case "DWD" -> "Cold Monsoonal Subarctic";
            case "DFA" -> "Continental";
            case "DFB" -> "Cold Continental";
            case "DFC" -> "Subarctic";
            case "DFD" -> "Cold Subarctic";
            case "ET" -> "Tundra";
            case "EF" -> "Polar";
            default -> code;
        };
    }

    public static final List<String> NAMES = CODES.stream().map(ClimateZones::label).toList();

    private static String normalized(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** Accept old saved codes, but all new input and display uses readable names. */
    public static String code(String value) {
        String match = normalized(value);
        for (String code : CODES) {
            if (code.equalsIgnoreCase(match) || normalized(label(code)).equals(match)) {
                return code.toLowerCase(Locale.ROOT);
            }
        }
        throw new IllegalArgumentException("Unknown climate zone: " + value.replace('_', ' '));
    }

    public static String displayList(String value) {
        return Arrays.stream(value.split(",", -1))
                .map(
                        v -> {
                            if (v.isBlank()) {
                                return v;
                            }
                            try {
                                return label(code(v));
                            } catch (IllegalArgumentException ignored) {
                                return v.trim();
                            }
                        })
                .collect(java.util.stream.Collectors.joining(", "));
    }

    public static String summary(Collection<String> codes) {
        return codes.stream()
                .map(ClimateZones::label)
                .sorted()
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private ClimateZones() {}
}
