package com.vaabhi.catalog;

/** A product, its variants or its photos changed in the admin; the Meta catalogue sync reacts once committed. */
record CatalogChanged(long productId) {}
