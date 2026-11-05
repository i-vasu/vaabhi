package com.vaabhi.tailoring;

import jakarta.validation.Valid;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The workshop: work board, overview, assigning and who works which stage. Requires the ADMIN role. */
@RestController
@RequestMapping("/api/v1/admin/workshop")
class WorkshopController {

    private final WorkshopService workshop;

    WorkshopController(WorkshopService workshop) {
        this.workshop = workshop;
    }

    @GetMapping("/overview")
    WorkshopOverview getWorkshopOverview() {
        return workshop.overview();
    }

    @GetMapping("/board")
    List<BoardColumn> getWorkBoard(
            @RequestParam(required = false) @Nullable Long staffId,
            @RequestParam(required = false) @Nullable String q) {
        return workshop.board(staffId, q);
    }

    @PutMapping("/items/{itemId}/assignee")
    WorkItem assignWorkItem(@PathVariable long itemId, @RequestBody AssignForm form) {
        return workshop.assign(itemId, form.staffId());
    }

    @PostMapping("/items/move")
    List<BoardColumn> moveWorkItems(@Valid @RequestBody BulkMove move, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return workshop.move(move, ClientController.actor(jwt));
    }

    @GetMapping("/staff")
    List<WorkshopStaff> listWorkshopStaff() {
        return workshop.staff();
    }

    @PutMapping("/staff/{staffId}/stages")
    WorkshopStaff saveWorkshopStaffStages(@PathVariable long staffId, @Valid @RequestBody StaffStages form) {
        return workshop.saveStages(staffId, form);
    }
}
