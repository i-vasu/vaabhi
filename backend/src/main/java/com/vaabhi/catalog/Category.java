package com.vaabhi.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int position;

    private String description;

    /** R2 key of the category photo (site/<uuid>), shown as its circle and page banner; null for none. */
    @Column(name = "image_key")
    private String imageKey;

    protected Category() {}

    public Category(String slug, String name, int position) {
        this.slug = slug;
        this.name = name;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public int getPosition() {
        return position;
    }

    public String getDescription() {
        return description;
    }

    public String getImageKey() {
        return imageKey;
    }

    void setImageKey(String imageKey) {
        this.imageKey = imageKey;
    }
}
