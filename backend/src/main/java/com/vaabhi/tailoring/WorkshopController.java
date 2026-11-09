package com.vaabhi.tailoring;

import jakarta.validation.Valid;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The workshop: work board, overview, assigning, who works which stage, and pieces cut from stock ("Make
 * products"). Needs the WORKSHOP permission (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/admin/workshop")
class WorkshopController {

    private final WorkshopService workshop;
    private final PieceService pieces;

    WorkshopController(WorkshopService workshop, PieceService pieces) {
        this.workshop = workshop;
        this.pieces = pieces;
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

    // --- pieces -------------------------------------------------------------------------------------------------

    @PostMapping("/pieces")
    @ResponseStatus(HttpStatus.CREATED)
    List<MakePieces.Made> makePieces(@Valid @RequestBody MakePieces form, @AuthenticationPrincipal @Nullable Jwt jwt) {
        return pieces.make(form, ClientController.actor(jwt));
    }

    @GetMapping("/pieces/by-product/{productId}")
    WorkPiece getPieceByProduct(@PathVariable long productId) {
        return pieces.byProduct(productId);
    }

    @PutMapping("/pieces/{pieceId}/assignee")
    WorkPiece assignWorkPiece(@PathVariable long pieceId, @RequestBody AssignForm form) {
        return pieces.assign(pieceId, form.staffId());
    }

    @PutMapping("/pieces/{pieceId}/note")
    WorkPiece setWorkPieceNote(@PathVariable long pieceId, @Valid @RequestBody MakePieces.PieceNote form) {
        return pieces.setNote(pieceId, form.note());
    }

    @PostMapping("/pieces/{pieceId}/photos/upload")
    TailoringImageUpload startWorkPiecePhotoUpload(@PathVariable long pieceId) {
        return pieces.startPhotoUpload(pieceId);
    }

    @PostMapping("/pieces/{pieceId}/photos")
    @ResponseStatus(HttpStatus.CREATED)
    WorkPiece addWorkPiecePhoto(@PathVariable long pieceId, @Valid @RequestBody MakePieces.PhotoForm form) {
        return pieces.addPhoto(pieceId, form.key());
    }

    @DeleteMapping("/pieces/{pieceId}/photos/{photoId}")
    WorkPiece removeWorkPiecePhoto(@PathVariable long pieceId, @PathVariable long photoId) {
        return pieces.removePhoto(pieceId, photoId);
    }
}
