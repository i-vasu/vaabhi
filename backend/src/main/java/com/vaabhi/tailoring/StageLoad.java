package com.vaabhi.tailoring;

import com.vaabhi.settings.ProductionStage;

/** How many open items sit in a stage, and how many of those are on overdue orders. */
public record StageLoad(ProductionStage stage, int items, int overdue) {}
