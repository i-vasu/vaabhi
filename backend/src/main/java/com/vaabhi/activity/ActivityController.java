package com.vaabhi.activity;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The activity log (REPORTS permission) and each person's notifications ({@code /me/**}: everyone signed in).
 */
@RestController
@RequestMapping("/api/v1/admin")
class ActivityController {

    private final ActivityService activity;

    ActivityController(ActivityService activity) {
        this.activity = activity;
    }

    private static long staffId(Jwt jwt) {
        var subject = jwt.getSubject();
        if (subject != null && subject.startsWith("staff:")) {
            try {
                return Long.parseLong(subject.substring("staff:".length()));
            } catch (NumberFormatException e) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a staff sign-in", e);
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a staff sign-in");
    }

    @GetMapping("/activity")
    Activity.ActivityPage getActivityLog(
            @RequestParam(required = false) @Nullable String kind,
            @RequestParam(required = false) @Nullable String refType,
            @RequestParam(required = false) @Nullable Long refId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return activity.log(kind, refType, refId, Math.max(1, page), Math.clamp(pageSize, 1, 200));
    }

    @GetMapping("/me/notifications")
    Activity.Notifications getNotifications(@AuthenticationPrincipal Jwt jwt) {
        return activity.notifications(staffId(jwt));
    }

    @PostMapping("/me/notifications/seen")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markNotificationsSeen(@AuthenticationPrincipal Jwt jwt) {
        activity.markSeen(staffId(jwt));
    }
}
