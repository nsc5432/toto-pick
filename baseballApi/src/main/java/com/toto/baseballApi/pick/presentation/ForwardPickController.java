package com.toto.baseballApi.pick.presentation;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.toto.baseballApi.matchschedule.domain.MatchScheduleRepository;
import com.toto.baseballApi.pick.application.ForwardPickService;
import com.toto.baseballApi.pick.application.ForwardPickSettlementService;
import com.toto.baseballApi.pick.presentation.dto.ForwardPickResponse;
import com.toto.baseballApi.pick.presentation.dto.ForwardSettlementResponse;
import com.toto.baseballApi.pick.presentation.dto.GenerateForwardPicksRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The forward test's HTTP surface: record picks before the games, settle them after.
 *
 * <p>Kept apart from {@code /api/picks/simulate}, which re-picks history and can be re-run at will.
 * These picks are append-only on purpose — re-running generation for a date that already has picks
 * skips them rather than replacing them, because a record that can be rewritten proves nothing about
 * when it was written.
 */
@RestController
@RequestMapping("/api/forward-picks")
@RequiredArgsConstructor
public class ForwardPickController {

    private final ForwardPickService forwardPickService;
    private final ForwardPickSettlementService settlementService;
    private final MatchScheduleRepository matchScheduleRepository;

    /** Dates currently staged in {@code match_schedule} — how far ahead ingestion has run. */
    @GetMapping("/schedule-dates")
    public List<String> scheduleDates() {
        return matchScheduleRepository.findDistinctYmds();
    }

    @PostMapping("/generate")
    public ForwardPickResponse generate(@Valid @RequestBody GenerateForwardPicksRequest request) {
        return ForwardPickResponse.from(forwardPickService.generate(request.toCommand()));
    }

    @PostMapping("/settle")
    public ForwardSettlementResponse settle(
            @RequestParam String algorithmCode,
            @RequestParam(defaultValue = "NSC") String userName) {
        return ForwardSettlementResponse.from(settlementService.settle(algorithmCode, userName));
    }
}
