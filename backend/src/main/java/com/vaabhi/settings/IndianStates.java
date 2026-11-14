package com.vaabhi.settings;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The 28 states and 8 union territories with their GST state codes (the first two digits of a GSTIN). */
public final class IndianStates {

    public record State(String name, String code) {}

    private static final List<State> ALL = List.of(
            new State("Jammu and Kashmir", "01"),
            new State("Himachal Pradesh", "02"),
            new State("Punjab", "03"),
            new State("Chandigarh", "04"),
            new State("Uttarakhand", "05"),
            new State("Haryana", "06"),
            new State("Delhi", "07"),
            new State("Rajasthan", "08"),
            new State("Uttar Pradesh", "09"),
            new State("Bihar", "10"),
            new State("Sikkim", "11"),
            new State("Arunachal Pradesh", "12"),
            new State("Nagaland", "13"),
            new State("Manipur", "14"),
            new State("Mizoram", "15"),
            new State("Tripura", "16"),
            new State("Meghalaya", "17"),
            new State("Assam", "18"),
            new State("West Bengal", "19"),
            new State("Jharkhand", "20"),
            new State("Odisha", "21"),
            new State("Chhattisgarh", "22"),
            new State("Madhya Pradesh", "23"),
            new State("Gujarat", "24"),
            new State("Dadra and Nagar Haveli and Daman and Diu", "26"),
            new State("Maharashtra", "27"),
            new State("Karnataka", "29"),
            new State("Goa", "30"),
            new State("Lakshadweep", "31"),
            new State("Kerala", "32"),
            new State("Tamil Nadu", "33"),
            new State("Puducherry", "34"),
            new State("Andaman and Nicobar Islands", "35"),
            new State("Telangana", "36"),
            new State("Andhra Pradesh", "37"),
            new State("Ladakh", "38"));

    private IndianStates() {}

    public static List<State> all() {
        return ALL;
    }

    /** The two-digit GST code for a state name (any letter case), or empty when it is not an Indian state. */
    public static Optional<String> code(String state) {
        if (state == null) {
            return Optional.empty();
        }
        String wanted = state.trim().toLowerCase(Locale.ROOT);
        return ALL.stream()
                .filter(s -> s.name().toLowerCase(Locale.ROOT).equals(wanted))
                .map(State::code)
                .findFirst();
    }
}
