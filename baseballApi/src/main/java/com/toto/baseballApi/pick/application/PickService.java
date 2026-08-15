package com.toto.baseballApi.pick.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.BaseballResultRepository;
import com.toto.baseballApi.pick.domain.PickAlgorithm;
import com.toto.baseballApi.pick.domain.PickDetail;
import com.toto.baseballApi.pick.domain.PickMaster;
import com.toto.baseballApi.pick.domain.PickMasterRepository;
import com.toto.baseballApi.pick.domain.PickSelection;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PickService {

    private final PickMasterRepository pickMasterRepository;
    private final BaseballResultRepository baseballResultRepository;

    /**
     * Spring collects every {@link PickAlgorithm} bean into this map, keyed by bean name.
     * Adding a new algorithm implementation (a new {@code @Component("name")}) makes it
     * selectable here with no change to this service — the DIP swap point.
     */
    private final Map<String, PickAlgorithm> algorithms;

    public PickMaster createPick(CreatePickCommand command) {
        boolean hasAlgorithm = command.algorithmName() != null;
        boolean hasManualPicks = command.manualPicks() != null && !command.manualPicks().isEmpty();
        if (hasAlgorithm == hasManualPicks) {
            throw new IllegalArgumentException(
                    "Exactly one of algorithmName or manualPicks must be provided");
        }

        List<PickSelection> selections;
        if (hasAlgorithm) {
            PickAlgorithm algorithm = algorithms.get(command.algorithmName());
            if (algorithm == null) {
                throw new IllegalArgumentException("Unknown algorithmName: " + command.algorithmName());
            }
            selections = algorithm.selectPicks(command.year(), command.round(), command.inputMoney());
        } else {
            selections = command.manualPicks();
        }

        List<PickDetail> details = selections.stream()
                .map(selection -> new PickDetail(null, selection.resultId(), selection.predictedTotalResult()))
                .toList();

        PickMaster pickMaster = new PickMaster(
                null,
                command.year(),
                command.round(),
                command.userName(),
                command.inputMoney(),
                null,
                details);

        return pickMasterRepository.save(pickMaster);
    }

    public PickMaster settlePick(Integer pickMasterId) {
        PickMaster pickMaster = pickMasterRepository.findById(pickMasterId)
                .orElseThrow(() -> new IllegalArgumentException("Pick not found: " + pickMasterId));
        if (pickMaster.outputMoney() != null) {
            throw new IllegalStateException("Pick already settled: " + pickMasterId);
        }

        List<Integer> resultIds = pickMaster.details().stream().map(PickDetail::resultId).toList();
        Map<Integer, BaseballResult> actualResultsById = baseballResultRepository.findAllById(resultIds).stream()
                .collect(Collectors.toMap(BaseballResult::id, r -> r));

        boolean allHit = true;
        BigDecimal combinedOdds = BigDecimal.ONE;
        for (PickDetail detail : pickMaster.details()) {
            BaseballResult actual = actualResultsById.get(detail.resultId());
            if (actual == null || !actual.totalResult().equals(detail.totalResult())) {
                allHit = false;
                break;
            }
            combinedOdds = combinedOdds.multiply(BigDecimal.valueOf(actual.totalDiv()));
        }

        BigDecimal outputMoney;
        if (allHit) {
            // "소수 둘째에서 올림" — round the combined odds UP at the 2nd decimal place.
            combinedOdds = combinedOdds.setScale(2, RoundingMode.CEILING);
            outputMoney = combinedOdds.multiply(pickMaster.inputMoney())
                    .setScale(0, RoundingMode.HALF_UP);
        } else {
            outputMoney = BigDecimal.ZERO;
        }

        PickMaster settled = new PickMaster(
                pickMaster.id(),
                pickMaster.year(),
                pickMaster.round(),
                pickMaster.userName(),
                pickMaster.inputMoney(),
                outputMoney,
                pickMaster.details());

        return pickMasterRepository.save(settled);
    }

    public List<PickMaster> settleAllPending() {
        return pickMasterRepository.findAllPendingSettlement().stream()
                .map(pending -> settlePick(pending.id()))
                .toList();
    }

    public Page<PickMaster> listPicks(Pageable pageable) {
        return pickMasterRepository.findAll(pageable);
    }
}
