-- Blog posts are content pages too: section BLOG, served at /blog/<slug> and listed on /blog (not in the footer).
ALTER TABLE content_page DROP CONSTRAINT content_page_section_check;
ALTER TABLE content_page ADD CONSTRAINT content_page_section_check
    CHECK (section IN ('HELP', 'POLICY', 'ABOUT', 'BLOG'));
