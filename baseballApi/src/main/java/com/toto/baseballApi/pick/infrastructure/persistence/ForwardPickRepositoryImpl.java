package com.toto.baseballApi.pick.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.toto.baseballApi.baseballresult.domain.FixtureKey;
import com.toto.baseballApi.pick.domain.ForwardPick;
import com.toto.baseballApi.pick.domain.ForwardPickRepository;
import com.toto.baseballApi.pick.domain.ForwardPickStatus;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
class ForwardPickRepositoryImpl implements ForwardPickRepository {

    private final ForwardPickJpaRepository pickRepository;
    private final ForwardPickLegJpaRepository legRepository;

    @Override
    public ForwardPick append(ForwardPick pick) {
        ForwardPickJpaEntity saved = pickRepository.save(new ForwardPickJpaEntity(
                null, pick.algorithmCode(), pick.paramSignature(), pick.userName(),
                pick.ymd(), pick.bucket(), pick.slipNo(), pick.inputMoney(), pick.combinedOdds(),
                pick.status().name(), pick.outputMoney(), pick.createdAt(), pick.settledAt()));

        List<ForwardPickLegJpaEntity> legs = pick.legs().stream()
                .map(leg -> new ForwardPickLegJpaEntity(
                        saved.getId(), leg.legNo(),
                        leg.fixture().year(), leg.fixture().round(), leg.fixture().tournament(),
                        leg.fixture().ymd(), leg.fixture().tm(), leg.fixture().home(),
                        leg.fixture().away(), leg.gameType(), leg.totalResult(),
                        leg.backedOdds(), leg.overround(), leg.resultId(), leg.legResult()))
                .toList();
        legRepository.saveAll(legs);

        return toDomain(saved, legs);
    }

    @Override
    public boolean existsSlip(
            String algorithmCode, String userName, String ymd, String bucket, int slipNo) {
        return pickRepository.existsByAlgorithmCodeAndUserNameAndYmdAndBucketAndSlipNo(
                algorithmCode, userName, ymd, bucket, slipNo);
    }

    @Override
    public List<ForwardPick> findByAlgorithm(String algorithmCode, String userName) {
        return withLegs(
                pickRepository.findByAlgorithmCodeAndUserNameOrderByYmdAscIdAsc(algorithmCode, userName));
    }

    @Override
    public List<ForwardPick> findAll() {
        return withLegs(pickRepository.findAllByOrderByYmdAscIdAsc());
    }

    /** One extra query for every leg of the given picks, rather than one per pick. */
    private List<ForwardPick> withLegs(List<ForwardPickJpaEntity> picks) {
        if (picks.isEmpty()) {
            return List.of();
        }
        Map<Integer, List<ForwardPickLegJpaEntity>> legsByPick =
                legRepository.findByForwardPickIdInOrderByForwardPickIdAscLegNoAsc(
                                picks.stream().map(ForwardPickJpaEntity::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(ForwardPickLegJpaEntity::getForwardPickId));

        List<ForwardPick> result = new ArrayList<>(picks.size());
        for (ForwardPickJpaEntity pick : picks) {
            result.add(toDomain(pick, legsByPick.getOrDefault(pick.getId(), List.of())));
        }
        return result;
    }

    @Override
    public void settle(ForwardPick settled) {
        ForwardPickJpaEntity entity = pickRepository.findById(settled.id()).orElseThrow(
                () -> new IllegalArgumentException("Unknown forward pick: " + settled.id()));
        entity.setStatus(settled.status().name());
        entity.setOutputMoney(settled.outputMoney());
        entity.setSettledAt(settled.settledAt());
        pickRepository.save(entity);

        Map<Integer, ForwardPick.ForwardPickLeg> byLegNo = settled.legs().stream()
                .collect(Collectors.toMap(ForwardPick.ForwardPickLeg::legNo, leg -> leg));
        List<ForwardPickLegJpaEntity> legs =
                legRepository.findByForwardPickIdInOrderByForwardPickIdAscLegNoAsc(
                        List.of(settled.id()));
        for (ForwardPickLegJpaEntity leg : legs) {
            ForwardPick.ForwardPickLeg source = byLegNo.get(leg.getLegNo());
            if (source != null) {
                leg.setResultId(source.resultId());
                leg.setLegResult(source.legResult());
            }
        }
        legRepository.saveAll(legs);
    }

    private static ForwardPick toDomain(
            ForwardPickJpaEntity pick, List<ForwardPickLegJpaEntity> legs) {
        return new ForwardPick(
                pick.getId(), pick.getAlgorithmCode(), pick.getParamSignature(), pick.getUserName(),
                pick.getYmd(), pick.getBucket(), pick.getSlipNo(),
                pick.getInputMoney(), pick.getCombinedOdds(),
                ForwardPickStatus.valueOf(pick.getStatus()), pick.getOutputMoney(),
                pick.getCreatedAt(), pick.getSettledAt(),
                legs.stream().map(ForwardPickRepositoryImpl::toDomain).toList());
    }

    private static ForwardPick.ForwardPickLeg toDomain(ForwardPickLegJpaEntity leg) {
        return new ForwardPick.ForwardPickLeg(
                leg.getLegNo(),
                new FixtureKey(leg.getYear(), leg.getRound(), leg.getTournament(),
                        leg.getYmd(), leg.getTm(), leg.getHome(), leg.getAway()),
                leg.getGameType(), leg.getTotalResult(),
                leg.getBackedOdds(), leg.getOverround(), leg.getResultId(), leg.getLegResult());
    }
}
