package com.vaabhi.tailoring;

import com.vaabhi.staff.StaffRole;
import java.util.List;

/**
 * A staff member in the workshop.
 *
 * @param stageIds the stages they may be given; empty means every stage
 * @param workingOn items assigned to them now
 */
public record WorkshopStaff(long id, String name, StaffRole role, List<Long> stageIds, int workingOn) {}
