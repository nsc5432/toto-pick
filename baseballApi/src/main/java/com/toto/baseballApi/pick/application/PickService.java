package com.toto.baseballApi.pick.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.BaseballResultRepository;
import com.toto.baseballApi.pick.domain.PickDetail;
import com.toto.baseballApi.pick.domain.PickMaster;
import com.toto.baseballApi.pick.domain.PickMasterRepository;
import com.toto.baseballApi.pick.domain.PickSettlement;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PickService {

    private final PickMasterRepository pickMasterRepository;
    private final BaseballResultRepository baseballResultRepository;

    public PickMaster createPick(CreatePickCommand command) {
        if (command.manualPicks() == null || command.manualPicks().isEmpty()) {
            throw new IllegalArgumentException("manualPicks must be provided");
        }

        List<PickDetail> details = command.manualPicks().stream()
                .map(selection -> new PickDetail(null, selection.resultId(), selection.predictedTotalResult()))
                .toList();

        PickMaster pickMaster = new PickMaster(
                null,
                command.year(),
                command.round(),
                null,
                command.userName(),
                null,
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

        BigDecimal outputMoney = PickSettlement.settle(
                pickMaster.details(), actualResultsById, pickMaster.inputMoney());

        PickMaster settled = new PickMaster(
                pickMaster.id(),
                pickMaster.year(),
                pickMaster.round(),
                pickMaster.ymd(),
                pickMaster.userName(),
                pickMaster.algorithmCode(),
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
