package com.vaabhi.tailoring;

import com.vaabhi.settings.ProductionStage;
import java.util.List;

/**
 * One stage of the work board with the items in it, most urgent first.
 *
 * @param removed the stage was taken out of the settings but still holds items
 */
public record BoardColumn(ProductionStage stage, boolean removed, List<WorkItem> items) {}
