package com.vaabhi.storefront;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Storefront content pages: written and published in the dashboard, served to the storefront. */
@Service
public class ContentPageService {

    private static final String SELECT = """
            SELECT id, slug, section, position, title, summary, body, published, updated_at
            FROM content_page
            """;

    private final JdbcClient jdbc;

    ContentPageService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Every page, published or not, by section and position (the dashboard list). */
    @Transactional(readOnly = true)
    public List<ContentPage> list() {
        return jdbc.sql(SELECT + " ORDER BY section, position, id")
                .query(ContentPage.class)
                .list();
    }

    /** Published pages for the storefront footer and blog, by section; blog posts newest first. */
    @Transactional(readOnly = true)
    public List<ContentPage.ContentPageLink> published() {
        return jdbc.sql("""
                        SELECT slug, section, title, summary, updated_at FROM content_page WHERE published
                        ORDER BY section, CASE WHEN section = 'BLOG' THEN -EXTRACT(EPOCH FROM created_at) END,
                                 position, id
                        """).query(ContentPage.ContentPageLink.class).list();
    }

    /** A published page by slug; 404 for drafts and unknown slugs. */
    @Transactional(readOnly = true)
    public ContentPage publishedPage(String slug) {
        return jdbc.sql(SELECT + " WHERE slug = :slug AND published")
                .param("slug", slug)
                .query(ContentPage.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found"));
    }

    private ContentPage get(long id) {
        return jdbc.sql(SELECT + " WHERE id = :id")
                .param("id", id)
                .query(ContentPage.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found"));
    }

    /** Creates (id null) or updates a page; new pages go last in their section. */
    @Transactional
    public ContentPage save(@Nullable Long id, ContentPage.ContentPageForm form) {
        if (id != null) {
            get(id);
        }
        var sql = id == null ? """
                  INSERT INTO content_page (slug, section, position, title, summary, body, published)
                  VALUES (:slug, :section, (SELECT COALESCE(MAX(position), 0) + 1 FROM content_page
                                            WHERE section = :section), :title, :summary, :body, :published)
                  RETURNING id
                  """ : """
                  UPDATE content_page SET slug = :slug, section = :section, title = :title, summary = :summary,
                      body = :body, published = :published, updated_at = now()
                  WHERE id = :id RETURNING id
                  """;
        try {
            long saved = jdbc.sql(sql)
                    .param("slug", form.slug())
                    .param("section", form.section().name())
                    .param("title", form.title().trim())
                    .param(
                            "summary",
                            form.summary() == null || form.summary().isBlank()
                                    ? null
                                    : form.summary().trim())
                    .param("body", form.body().strip())
                    .param("published", form.published())
                    .param("id", id)
                    .query(Long.class)
                    .single();
            return get(saved);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another page already uses /" + form.slug());
        }
    }

    @Transactional
    public void remove(long id) {
        get(id);
        jdbc.sql("DELETE FROM content_page WHERE id = :id").param("id", id).update();
    }
}
