package com.vaabhi.tailoring;

import java.util.List;

/**
 * The workshop at a glance.
 *
 * @param inProduction items in work stages
 * @param unassigned items in work stages that nobody has been given
 * @param readyItems items waiting for pickup
 * @param dueToday open orders due today
 * @param overdue open orders past their delivery date
 * @param trialsToday open orders with a trial today
 * @param stageLoad items per stage (work and ready stages), in stage order
 * @param staffLoad active staff with how many items each has
 */
public record WorkshopOverview(
        int inProduction,
        int unassigned,
        int readyItems,
        int dueToday,
        int overdue,
        int trialsToday,
        List<StageLoad> stageLoad,
        List<WorkshopStaff> staffLoad) {}
