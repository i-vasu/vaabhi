package com.vaabhi.tailoring;

import com.vaabhi.settings.ProductionStage;
import java.util.List;

/**
 * One stage of the work board with the order items in it, most urgent first, and the pieces cut from stock.
 *
 * @param removed the stage was taken out of the settings but still holds items or pieces
 * @param pieces pieces on their way to becoming storefront products, oldest first
 */
public record BoardColumn(ProductionStage stage, boolean removed, List<WorkItem> items, List<WorkPiece> pieces) {}
