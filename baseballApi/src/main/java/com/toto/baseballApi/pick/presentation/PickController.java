package com.toto.baseballApi.pick.presentation;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.toto.baseballApi.pick.application.CreatePickCommand;
import com.toto.baseballApi.pick.application.PickService;
import com.toto.baseballApi.pick.domain.PickSelection;
import com.toto.baseballApi.pick.presentation.dto.CreatePickRequest;
import com.toto.baseballApi.pick.presentation.dto.PickResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/picks")
@RequiredArgsConstructor
public class PickController {

    private final PickService pickService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PickResponse createPick(@Valid @RequestBody CreatePickRequest request) {
        List<PickSelection> manualPicks = request.manualPicks() == null
                ? null
                : request.manualPicks().stream()
                        .map(p -> new PickSelection(p.resultId(), p.totalResult()))
                        .toList();

        CreatePickCommand command = new CreatePickCommand(
                request.year(),
                request.round(),
                request.userName(),
                request.inputMoney(),
                request.algorithmName(),
                manualPicks);

        return PickResponse.from(pickService.createPick(command));
    }

    @PostMapping("/{id}/settle")
    public PickResponse settle(@PathVariable Integer id) {
        return PickResponse.from(pickService.settlePick(id));
    }

    @PostMapping("/settle-pending")
    public List<PickResponse> settlePending() {
        return pickService.settleAllPending().stream().map(PickResponse::from).toList();
    }

    @GetMapping
    public Page<PickResponse> list(
            @PageableDefault(size = 20, sort = { "id" }, direction = Sort.Direction.DESC) Pageable pageable) {
        return pickService.listPicks(pageable).map(PickResponse::from);
    }
}
