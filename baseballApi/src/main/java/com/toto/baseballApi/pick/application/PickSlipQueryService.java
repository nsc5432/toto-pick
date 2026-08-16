package com.toto.baseballApi.pick.application;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.BaseballResultRepository;
import com.toto.baseballApi.baseballresult.domain.ThreeWayOdds;
import com.toto.baseballApi.pick.domain.PickDetail;
import com.toto.baseballApi.pick.domain.PickMaster;
import com.toto.baseballApi.pick.domain.PickMasterRepository;

import lombok.RequiredArgsConstructor;

/**
 * Serves the slip-composition view: the simulation user's persisted slips in a ymd range, each leg
 * joined back to its game (teams, backed price, realized result) so the screen can show what was
 * actually combined — not just the result ids.
 */
@Service
@RequiredArgsConstructor
public class PickSlipQueryService {

    private final PickMasterRepository pickMasterRepository;
    private final BaseballResultRepository baseballResultRepository;

    @Transactional(readOnly = true)
    public List<PickSlipView> slips(String bgngYmd, String endYmd, List<String> algorithmCodes) {
        if (bgngYmd.compareTo(endYmd) > 0) {
            throw new IllegalArgumentException("bgngYmd must not be after endYmd");
        }
        List<PickMaster> masters = pickMasterRepository.findWithDetails(
                PickSimulationService.SIMULATION_USER_NAME, algorithmCodes, bgngYmd, endYmd);
        if (masters.isEmpty()) {
            return List.of();
        }

        List<Integer> resultIds = masters.stream()
                .flatMap(m -> m.details().stream())
                .map(PickDetail::resultId)
                .distinct()
                .toList();
        Map<Integer, BaseballResult> gamesById = baseballResultRepository.findAllById(resultIds)
                .stream()
                .collect(Collectors.toMap(BaseballResult::id, Function.identity()));

        return masters.stream()
                .sorted(Comparator.comparing(PickMaster::ymd, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PickMaster::algorithmCode)
                        .thenComparing(PickMaster::id))
                .map(master -> toView(master, gamesById))
                .toList();
    }

    private PickSlipView toView(PickMaster master, Map<Integer, BaseballResult> gamesById) {
        List<PickSlipView.Leg> legs = master.details().stream()
                .map(detail -> toLeg(detail, gamesById.get(detail.resultId())))
                .toList();
        boolean hit = master.outputMoney() != null
                && master.outputMoney().compareTo(BigDecimal.ZERO) > 0;
        return new PickSlipView(
                master.id(), master.year(), master.round(), master.ymd(), master.algorithmCode(),
                master.inputMoney(), master.outputMoney(), hit, legs);
    }

    private PickSlipView.Leg toLeg(PickDetail detail, BaseballResult game) {
        if (game == null) {
            return new PickSlipView.Leg(
                    detail.resultId(), null, null, null, detail.totalResult(), null, null, false);
        }
        return new PickSlipView.Leg(
                detail.resultId(), game.ymd(), game.home(), game.away(),
                detail.totalResult(), backedOdds(game.publishedOdds(), detail.totalResult()),
                game.totalResult(),
                game.totalResult() != null && game.totalResult().equals(detail.totalResult()));
    }

    private Double backedOdds(ThreeWayOdds odds, String predicted) {
        if (odds == null) {
            return null;
        }
        return switch (predicted) {
            case "승" -> odds.home();
            case "1" -> odds.draw();
            case "패" -> odds.away();
            default -> null;
        };
    }
}
