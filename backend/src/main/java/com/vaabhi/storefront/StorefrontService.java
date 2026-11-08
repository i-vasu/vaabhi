package com.vaabhi.storefront;

import com.vaabhi.catalog.CategoryResponse;
import com.vaabhi.catalog.CategoryService;
import com.vaabhi.shared.storage.ObjectStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Home-page banners and category photos. Photos upload straight from the browser to R2 in three widths, like
 * product photos; a key is accepted only once every width is there.
 */
@Service
public class StorefrontService {

    static final List<Integer> WIDTHS = List.of(400, 800, 1600);
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String PREFIX = "site/";
    /** Most banners a slot can hold. */
    static final int MAX_PER_SLOT = 12;

    private static final String SELECT = """
            SELECT id, slot, position, eyebrow, title, subtitle, cta_label, link, image_key, alt_text, active,
                   starts_on, ends_on,
                   active AND (starts_on IS NULL OR starts_on <= :today) AND (ends_on IS NULL OR ends_on >= :today)
                       AS live
            FROM site_banner
            """;

    private final JdbcClient jdbc;
    private final ObjectStorage storage;
    private final CategoryService categories;
    private final Clock clock;

    StorefrontService(JdbcClient jdbc, ObjectStorage storage, CategoryService categories, Clock clock) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.categories = categories;
        this.clock = clock;
    }

    // --- photos -----------------------------------------------------------------------------------------------------

    Banner.SiteImageUpload startUpload() {
        if (!storage.configured()) {
            throw new ErrorResponseException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ProblemDetail.forStatusAndDetail(
                            HttpStatus.SERVICE_UNAVAILABLE, "Photo storage is not configured on this server"),
                    null);
        }
        var key = PREFIX + UUID.randomUUID();
        var renditions = WIDTHS.stream()
                .map(w -> new Banner.SiteImageUpload.Rendition(
                        w, storage.presignPut(file(key, w), WINDOW).toString()))
                .toList();
        return new Banner.SiteImageUpload(key, renditions, Instant.now().plus(WINDOW));
    }

    private static String file(String key, int width) {
        return key + "/" + width + ".webp";
    }

    /** Rejects keys this service did not hand out or whose files never all arrived. */
    void requireUploaded(String key) {
        if (!key.matches("^" + PREFIX + "[0-9a-f-]{36}$")
                || WIDTHS.stream().anyMatch(w -> !storage.exists(file(key, w)))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload the photo again");
        }
    }

    // --- banners ----------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<Banner> list(Banner.@Nullable Slot slot) {
        return jdbc.sql(SELECT + """
                        WHERE (CAST(:slot AS varchar) IS NULL OR slot = :slot)
                        ORDER BY slot, position, id
                        """)
                .param("today", LocalDate.now(clock))
                .param("slot", slot == null ? null : slot.name())
                .query(Banner.class)
                .list();
    }

    private Banner get(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id")
                .param("today", LocalDate.now(clock))
                .param("id", id)
                .query(Banner.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Banner not found"));
    }

    @Transactional
    public Banner save(@Nullable Long id, Banner.BannerForm form) {
        if (form.startsOn() != null && form.endsOn() != null && form.endsOn().isBefore(form.startsOn())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The banner ends before it starts");
        }
        if (id == null || !get(id).imageKey().equals(form.imageKey())) {
            requireUploaded(form.imageKey());
        }
        if (id == null) {
            long inSlot = jdbc.sql("SELECT COUNT(*) FROM site_banner WHERE slot = :slot")
                    .param("slot", form.slot().name())
                    .query(Long.class)
                    .single();
            if (inSlot >= MAX_PER_SLOT) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A slot holds at most " + MAX_PER_SLOT);
            }
        }
        var sql = id == null ? """
                  INSERT INTO site_banner (slot, position, eyebrow, title, subtitle, cta_label, link, image_key,
                      alt_text, active, starts_on, ends_on)
                  VALUES (:slot, (SELECT COALESCE(MAX(position), 0) + 1 FROM site_banner WHERE slot = :slot), :eyebrow,
                      :title, :subtitle, :cta, :link, :image, :alt, :active, :starts, :ends)
                  RETURNING id
                  """ : """
                  UPDATE site_banner SET slot = :slot, eyebrow = :eyebrow, title = :title, subtitle = :subtitle,
                      cta_label = :cta, link = :link, image_key = :image, alt_text = :alt, active = :active,
                      starts_on = :starts, ends_on = :ends, updated_at = now()
                  WHERE id = :id RETURNING id
                  """;
        long saved = jdbc.sql(sql)
                .param("slot", form.slot().name())
                .param("eyebrow", blank(form.eyebrow()))
                .param("title", form.title().trim())
                .param("subtitle", blank(form.subtitle()))
                .param("cta", blank(form.ctaLabel()))
                .param("link", blank(form.link()))
                .param("image", form.imageKey())
                .param("alt", blank(form.altText()))
                .param("active", form.active())
                .param("starts", form.startsOn())
                .param("ends", form.endsOn())
                .param("id", id)
                .query(Long.class)
                .single();
        return get(saved);
    }

    /** Reorders a slot's banners; every banner of the slot must be listed once. */
    @Transactional
    public List<Banner> reorder(Banner.BannerOrder order) {
        var current = list(order.slot()).stream().map(Banner::id).sorted().toList();
        if (!current.equals(order.ids().stream().sorted().toList())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "List every banner of the slot once");
        }
        for (int i = 0; i < order.ids().size(); i++) {
            jdbc.sql("UPDATE site_banner SET position = :position WHERE id = :id")
                    .param("position", i + 1)
                    .param("id", order.ids().get(i))
                    .update();
        }
        return list(order.slot());
    }

    /** Deletes the banner; its photo files are removed unless a category still uses the same key. */
    @Transactional
    public void remove(long id) {
        var banner = get(id);
        jdbc.sql("DELETE FROM site_banner WHERE id = :id").param("id", id).update();
        deleteFilesIfUnused(banner.imageKey());
    }

    private void deleteFilesIfUnused(String key) {
        long uses = jdbc.sql("""
                        SELECT (SELECT COUNT(*) FROM site_banner WHERE image_key = :key)
                             + (SELECT COUNT(*) FROM category WHERE image_key = :key)
                        """).param("key", key).query(Long.class).single();
        if (uses == 0) {
            WIDTHS.forEach(w -> storage.delete(file(key, w)));
        }
    }

    /** Live banners for the storefront home page. */
    @Transactional(readOnly = true)
    public Banner.HomeContent home() {
        var live = list(null).stream().filter(Banner::live).toList();
        return new Banner.HomeContent(
                live.stream().filter(b -> b.slot() == Banner.Slot.HERO).toList(),
                live.stream().filter(b -> b.slot() == Banner.Slot.OCCASION).toList(),
                live.stream()
                        .filter(b -> b.slot() == Banner.Slot.CRAFT)
                        .findFirst()
                        .orElse(null));
    }

    // --- category photos --------------------------------------------------------------------------------------------

    @Transactional
    public CategoryResponse setCategoryImage(long categoryId, @Nullable String imageKey) {
        var before = categories.listCategories().stream()
                .filter(c -> c.id() == categoryId)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        if (imageKey != null) {
            requireUploaded(imageKey);
        }
        var saved = categories.setImage(categoryId, imageKey);
        if (before.imageKey() != null && !before.imageKey().equals(imageKey)) {
            deleteFilesIfUnused(before.imageKey());
        }
        return saved;
    }

    private static @Nullable String blank(@Nullable String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
