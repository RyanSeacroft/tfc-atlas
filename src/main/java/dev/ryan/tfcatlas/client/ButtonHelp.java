package dev.ryan.tfcatlas.client;

/** Short action descriptions for menu buttons that don't already supply context-specific help. */
final class ButtonHelp {
    static String forLabel(String label) {
        return switch (label) {
            case "Back to map" -> "Save settings and return to the map.";
            case "Edit layer colours" ->
                    "Open the colour key to change the selected layer's colours.";
            case "Reset map panel positions" ->
                    "Restore map panels to their default positions and sizes. Search filters are kept.";
            case "Reset all Atlas settings" ->
                    "Restore all Atlas settings after confirmation. Saved presets, custom groups and map files are kept.";
            case "Clear cache" ->
                    "Remove this world's Atlas prediction cache. It will regenerate; Xaero's saved maps are kept.";
            case "Export PNG" ->
                    "Export the selected layer using the PNG options above. Files are saved in screenshots/tfcatlas.";
            case "Search" ->
                    "Start a search using the selected filters and centre. Highlights appear as matches are found.";
            case "Presets" -> "Load, save or remove a named search preset.";
            case "Save current search" ->
                    "Save these search filters under the entered preset name.";
            case "Save selection", "Save group" ->
                    "Save the selected entries as a custom group with the full entered name.";
            case "Clear selection" ->
                    "Deselect every entry in this selection. Saved groups are kept.";
            case "Select / clear all" ->
                    "Select every entry in this group, or clear them if all are already selected.";
            case "Back to groups" -> "Return to groups with these individual selections kept.";
            case "Centre" -> "Centre the map on the selected result and save settings.";
            case "Waypoint" -> "Create a Xaero waypoint at the selected result.";
            case "Copy X/Z" -> "Copy the selected result's X and Z coordinates.";
            case "Cancel" ->
                    "Stop the current action. A cancelled search keeps its partial highlights.";
            case "Clear" -> "Clear current search results and their highlights.";
            case "Reset worldgen" ->
                    "Restore generator prediction settings to their defaults. Match the actual world settings for accurate predictions.";
            case "Save colour" -> "Save the entered six-digit colour for this key entry.";
            case "Restore default" -> "Restore this key entry's original colour.";
            case "Back", "Done" -> "Return to the previous menu.";
            case "Results" -> "Open " + label.toLowerCase(java.util.Locale.ROOT) + ".";
            case "Display", "Worldgen", "Cache" ->
                    "Open " + label.toLowerCase(java.util.Locale.ROOT) + " settings.";
            case "Map" -> "Adjust map layers, labels, highlighted matches and minimap results.";
            case "Panels" -> "Show or hide map panels and restore their layout or settings.";
            case "Colours" -> "Adjust highlighted regions and layer palettes.";
            case "Storage" -> "Adjust the prediction cache and view its current status.";
            case "PNG export" ->
                    "Choose exported image size, sampling, centre, transparency and information file.";
            case "Terrain" ->
                    "Choose biomes, terrain features, height, steepness and matching area size.";
            case "Climate" -> "Choose rainfall, temperature and version-specific climate filters.";
            case "Geology" -> "Choose rocks, geological layers and version-specific soil filters.";
            case "Location" ->
                    "Choose water distances, inlandness, search centre, radius and sampling.";
            default ->
                    label.startsWith("Layer:")
                            ? "Cycle the data layer shown in this key."
                            : label.startsWith("Select ")
                                    ? "Use the selected groups and individual entries for this search."
                                    : "Select " + label + ".";
        };
    }

    private ButtonHelp() {}
}
