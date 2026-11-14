package com.vaabhi.accounts;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** The first rows of an uploaded file and the column mapping the last import used. */
public record FilePreview(List<List<String>> rows, @Nullable ColumnMapping saved) {}
