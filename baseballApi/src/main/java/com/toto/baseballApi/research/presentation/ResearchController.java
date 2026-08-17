package com.toto.baseballApi.research.presentation;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.toto.baseballApi.research.application.AlgorithmSearchService;
import com.toto.baseballApi.research.application.TwoWayOddsCheckService;
import com.toto.baseballApi.research.presentation.dto.AlgorithmSpaceResponse;
import com.toto.baseballApi.research.presentation.dto.BacktestRequest;
import com.toto.baseballApi.research.presentation.dto.ExperimentResponse;
import com.toto.baseballApi.research.presentation.dto.GoalResponse;
import com.toto.baseballApi.research.presentation.dto.SearchRequest;
import com.toto.baseballApi.research.presentation.dto.SearchResponse;
import com.toto.baseballApi.research.presentation.dto.TwoWayOddsCheckResponse;

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
    private final TwoWayOddsCheckService twoWayOddsCheckService;

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
     * Validates the 3-way → 2-way price estimator against realized 승패 payouts, and reports what the
     * market switch does to the hit rate. Run this before trusting any {@code FAVORITE_2WAY} result:
     * stake sizing and every leg benchmark are computed on top of that estimate.
     */
    @GetMapping("/two-way-odds-check")
    public TwoWayOddsCheckResponse twoWayOddsCheck(
            @RequestParam String bgngYmd,
            @RequestParam String endYmd,
            @RequestParam(required = false) Double oneRunHomeShare) {
        return TwoWayOddsCheckResponse.from(
                twoWayOddsCheckService.check(bgngYmd, endYmd, oneRunHomeShare));
    }

    @GetMapping("/experiments")
    public List<ExperimentResponse> experiments() {
        return searchService.history().stream()
                .map(ExperimentResponse::from)
                .toList();
    }
}
