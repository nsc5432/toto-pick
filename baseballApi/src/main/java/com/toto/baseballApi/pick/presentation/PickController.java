package com.toto.baseballApi.pick.presentation;

import java.util.List;
import java.util.Locale;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.toto.baseballApi.pick.application.CreatePickCommand;
import com.toto.baseballApi.pick.application.PickKpiQuery;
import com.toto.baseballApi.pick.application.PickKpiService;
import com.toto.baseballApi.pick.application.PickService;
import com.toto.baseballApi.pick.application.PickSimulationService;
import com.toto.baseballApi.pick.application.PickSlipQueryService;
import com.toto.baseballApi.pick.application.SimulatePicksCommand;
import com.toto.baseballApi.pick.domain.PickSelection;
import com.toto.baseballApi.pick.presentation.dto.AlgorithmResponse;
import com.toto.baseballApi.pick.presentation.dto.CreatePickRequest;
import com.toto.baseballApi.pick.presentation.dto.PickKpiResponse;
import com.toto.baseballApi.pick.presentation.dto.PickResponse;
import com.toto.baseballApi.pick.presentation.dto.PickSlipResponse;
import com.toto.baseballApi.pick.presentation.dto.SimulatePicksRequest;
import com.toto.baseballApi.pick.presentation.dto.SimulationResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/picks")
@RequiredArgsConstructor
public class PickController {

    private final PickService pickService;
    private final PickSimulationService pickSimulationService;
    private final PickKpiService pickKpiService;
    private final PickSlipQueryService pickSlipQueryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PickResponse createPick(@Valid @RequestBody CreatePickRequest request) {
        List<PickSelection> manualPicks = request.manualPicks().stream()
                .map(p -> new PickSelection(p.resultId(), p.totalResult()))
                .toList();

        CreatePickCommand command = new CreatePickCommand(
                request.year(),
                request.round(),
                request.userName(),
                request.inputMoney(),
                manualPicks);

        return PickResponse.from(pickService.createPick(command));
    }

    @GetMapping("/algorithms")
    public List<AlgorithmResponse> algorithms() {
        return pickSimulationService.availableAlgorithms().stream()
                .map(AlgorithmResponse::from)
                .toList();
    }

    @PostMapping("/simulate")
    public SimulationResponse simulate(@Valid @RequestBody SimulatePicksRequest request) {
        SimulatePicksCommand command = new SimulatePicksCommand(
                request.bgngYmd(),
                request.endYmd(),
                request.num(),
                request.x(),
                request.y(),
                request.inputMoney(),
                request.combinedN(),
                request.algorithmCodes());

        return SimulationResponse.from(pickSimulationService.simulate(command));
    }

    @GetMapping("/kpis")
    public PickKpiResponse kpis(
            @RequestParam String bgngYmd,
            @RequestParam String endYmd,
            @RequestParam(defaultValue = "day") String groupBy,
            @RequestParam(required = false) List<String> algorithmCodes) {
        PickKpiQuery query = new PickKpiQuery(
                bgngYmd,
                endYmd,
                PickKpiQuery.GroupBy.valueOf(groupBy.toUpperCase(Locale.ROOT)),
                algorithmCodes);
        return PickKpiResponse.from(pickKpiService.kpis(query));
    }

    /** 시뮬레이션 슬립의 실제 조합(어떤 경기를 어떻게 묶었는지)을 레그 단위로 조회한다. */
    @GetMapping("/slips")
    public List<PickSlipResponse> slips(
            @RequestParam String bgngYmd,
            @RequestParam String endYmd,
            @RequestParam(required = false) List<String> algorithmCodes) {
        return pickSlipQueryService.slips(bgngYmd, endYmd, algorithmCodes).stream()
                .map(PickSlipResponse::from)
                .toList();
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
