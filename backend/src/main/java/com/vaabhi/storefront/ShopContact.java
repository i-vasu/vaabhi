package com.vaabhi.storefront;

import com.vaabhi.settings.ShopProfile;
import java.time.LocalTime;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * How customers reach the shop, from the dashboard's shop profile: shown on the storefront's contact page and
 * footer. Only what the shop shares publicly; nothing else from the profile leaves the server.
 *
 * @param whatsapp the phone as a wa.me number (digits, with 91 in front of a 10-digit Indian number)
 * @param whatsappChannel the shop's WhatsApp channel or community invite link, for "Join on WhatsApp"
 * @param address one line built from the address fields
 */
public record ShopContact(
        String name,
        @Nullable String phone,
        @Nullable String whatsapp,
        @Nullable String whatsappChannel,
        @Nullable String email,
        @Nullable String address,
        @Nullable String mapsLink,
        @Nullable String instagram,
        @Nullable String facebook,
        @Nullable String youtube,
        LocalTime opensAt,
        LocalTime closesAt) {

    static ShopContact of(ShopProfile profile) {
        return new ShopContact(
                profile.shopName(),
                profile.phone(),
                whatsappNumber(profile.phone()),
                profile.whatsappChannel(),
                profile.publicEmail(),
                address(profile),
                profile.mapsLink(),
                profile.instagram(),
                profile.facebook(),
                profile.youtube(),
                profile.opensAt(),
                profile.closesAt());
    }

    static @Nullable String whatsappNumber(@Nullable String phone) {
        if (phone == null) {
            return null;
        }
        var digits = phone.replaceAll("\\D", "");
        if (digits.length() == 10) {
            return "91" + digits;
        }
        return digits.length() >= 11 ? digits : null;
    }

    private static @Nullable String address(ShopProfile p) {
        if (p.address() != null && !p.address().isBlank()) {
            return p.address().trim();
        }
        var line = Stream.of(
                        p.doorNo(),
                        p.street(),
                        p.area(),
                        p.city(),
                        p.state(),
                        p.pinCode())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
        return line.isEmpty() ? null : line;
    }
}
