/**
 * Activity: a log of what happened in the shop (orders, payments, alterations, settings changes), written from the
 * other modules' events, and each person's notifications: the recent activity they have not seen plus live alerts
 * (overdue and due orders, low stock, waiting requests). Nothing depends on this module.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Activity")
package com.vaabhi.activity;
