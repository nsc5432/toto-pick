package com.toto.baseballApi.research.presentation;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.toto.baseballApi.research.application.AlgorithmSearchService;
import com.toto.baseballApi.research.application.ForwardReportService;
import com.toto.baseballApi.research.presentation.dto.AlgorithmSpaceResponse;
import com.toto.baseballApi.research.presentation.dto.BacktestRequest;
import com.toto.baseballApi.research.presentation.dto.ExperimentResponse;
import com.toto.baseballApi.research.presentation.dto.ForwardReportResponse;
import com.toto.baseballApi.research.presentation.dto.GoalResponse;
import com.toto.baseballApi.research.presentation.dto.SearchRequest;
import com.toto.baseballApi.research.presentation.dto.SearchResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * The optimization pipeline's HTTP surface, one endpoint per stage:
 * {@code GET /algorithms} and {@code GET /goal} to see what can be searched and what counts as
 * success, {@code POST /search} to sweep, {@code POST /backtest} to score a single configuration,
 * and {@code GET /leaderboard} / {@code GET /experiments} to read the accumulated history.
 *
 * <p>Nothing here writes to {@code pick_mstr}. Committing a winner to the simulation tables stays a
 * separate, deliberate call to {@code POST /api/picks/simulate} — searching is exploration, and
 * exploration should not leave hundreds of discarded runs in the operational data.
 */
@RestController
@RequestMapping("/api/research")
@RequiredArgsConstructor
public class ResearchController {

    private final AlgorithmSearchService searchService;
    private final ForwardReportService forwardReportService;

    @GetMapping("/goal")
    public GoalResponse goal() {
        return GoalResponse.from(searchService.goal());
    }

    @GetMapping("/algorithms")
    public List<AlgorithmSpaceResponse> algorithms() {
        return searchService.algorithmSpaces().stream()
                .map(AlgorithmSpaceResponse::from)
                .toList();
    }

    @PostMapping("/search")
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        return SearchResponse.from(searchService.search(request.toCommand()));
    }

    @PostMapping("/backtest")
    public ExperimentResponse backtest(@Valid @RequestBody BacktestRequest request) {
        return ExperimentResponse.from(searchService.backtestOne(
                request.algorithmCode(), request.toParams(), request.toCommand()));
    }

    @GetMapping("/leaderboard")
    public List<ExperimentResponse> leaderboard(@RequestParam(defaultValue = "20") int limit) {
        return searchService.leaderboard(limit).stream()
                .map(ExperimentResponse::from)
                .toList();
    }

    /**
     * The forward test's standing — cumulative, and judged by the same {@code research.goal} a sweep
     * is. One entry per frozen candidate on record.
     *
     * <p>This is the only measurement here that is not in-sample, so it is the only one whose verdict
     * means what it says. Expect it to fail the sample gates for weeks: about ten legs accumulate a
     * day, and that is the price of a number that cannot be a window effect.
     */
    @GetMapping("/forward-report")
    public List<ForwardReportResponse> forwardReport() {
        return forwardReportService.reports().stream()
                .map(ForwardReportResponse::from)
                .toList();
    }

    @GetMapping("/experiments")
    public List<ExperimentResponse> experiments() {
        return searchService.history().stream()
                .map(ExperimentResponse::from)
                .toList();
    }
}
