package com.vaabhi.staff;

/** The signed-in staff member, as shown in the dashboard header. */
public record StaffProfile(long id, String name, String email, StaffRole role) {}
