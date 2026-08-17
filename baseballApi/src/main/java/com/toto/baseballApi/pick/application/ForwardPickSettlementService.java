package com.toto.baseballApi.pick.application;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.BaseballResultRepository;
import com.toto.baseballApi.baseballresult.domain.FixtureKey;
import com.toto.baseballApi.pick.domain.BackedPrice;
import com.toto.baseballApi.pick.domain.ForwardPick;
import com.toto.baseballApi.pick.domain.ForwardPickRepository;
import com.toto.baseballApi.pick.domain.ForwardPickStatus;
import com.toto.baseballApi.pick.domain.LegTally;
import com.toto.baseballApi.pick.domain.PickDetail;
import com.toto.baseballApi.pick.domain.PickSettlement;
import com.toto.baseballApi.pick.domain.PickUniverse;

import lombok.RequiredArgsConstructor;

/**
 * Settles forward picks once their games appear in {@code baseball_result}.
 *
 * <p>Rewrites nothing that was recorded before the game. Payout still comes from {@code totalDiv},
 * and the benchmark still comes from the price stored at bet time — not from a price re-derived now.
 * Re-deriving would be the quiet way to lose the property that makes this measurement worth having:
 * the prices seen when the bet was placed are part of the evidence, not an implementation detail.
 *
 * <p>Idempotent: already-settled picks are left alone, so it is safe to run on a schedule.
 */
@Service
@RequiredArgsConstructor
public class ForwardPickSettlementService {

    private final ForwardPickRepository forwardPickRepository;
    private final BaseballResultRepository baseballResultRepository;

    @Transactional
    public ForwardSettlementResult settle(String algorithmCode, String userName) {
        List<ForwardPick> picks = forwardPickRepository.findByAlgorithm(algorithmCode, userName);
        List<ForwardPick> pending = picks.stream()
                .filter(pick -> pick.status() == ForwardPickStatus.PENDING)
                .toList();
        if (pending.isEmpty()) {
            return new ForwardSettlementResult(algorithmCode, 0, 0, 0, LegTally.EMPTY,
                    BigDecimal.ZERO, BigDecimal.ZERO);
        }

        Map<String, Map<FixtureKey, BaseballResult>> resultsByMarket = loadResults(pending);

        int settled = 0;
        int stillPending = 0;
        int hits = 0;
        LegTally legs = LegTally.EMPTY;
        BigDecimal staked = BigDecimal.ZERO;
        BigDecimal returned = BigDecimal.ZERO;

        for (ForwardPick pick : pending) {
            List<ForwardPick.ForwardPickLeg> resolved = new ArrayList<>(pick.legs().size());
            Map<Integer, BaseballResult> gamesById = new HashMap<>();
            Map<Integer, BackedPrice> pricesById = new HashMap<>();
            boolean complete = true;

            for (ForwardPick.ForwardPickLeg leg : pick.legs()) {
                BaseballResult actual = resultsByMarket
                        .getOrDefault(leg.gameType(), Map.of())
                        .get(leg.fixture());
                if (actual == null || actual.totalResult() == null) {
                    complete = false;
                    break;
                }
                resolved.add(new ForwardPick.ForwardPickLeg(
                        leg.legNo(), leg.fixture(), leg.gameType(), leg.totalResult(),
                        leg.backedOdds(), leg.overround(), actual.id(), actual.totalResult()));
                gamesById.put(actual.id(), actual);
                pricesById.put(actual.id(), leg.price());
            }
            if (!complete) {
                stillPending++;
                continue;
            }

            List<PickDetail> details = resolved.stream()
                    .map(leg -> new PickDetail(null, leg.resultId(), leg.totalResult()))
                    .toList();
            BigDecimal payout = PickSettlement.settle(details, gamesById, pick.inputMoney());

            ForwardPick done = new ForwardPick(
                    pick.id(), pick.algorithmCode(), pick.paramSignature(), pick.userName(),
                    pick.ymd(), pick.bucket(), pick.slipNo(), pick.inputMoney(), pick.combinedOdds(),
                    ForwardPickStatus.SETTLED, payout, pick.createdAt(), LocalDateTime.now(), resolved);
            forwardPickRepository.settle(done);

            settled++;
            if (done.hit()) {
                hits++;
            }
            staked = staked.add(pick.inputMoney());
            returned = returned.add(payout);
            legs = legs.plus(PickSettlement.legTally(details, gamesById, pricesById));
        }
        return new ForwardSettlementResult(
                algorithmCode, settled, stillPending, hits, legs, staked, returned);
    }

    /** One query per market, covering the whole span the pending picks touch. */
    private Map<String, Map<FixtureKey, BaseballResult>> loadResults(List<ForwardPick> pending) {
        String from = pending.stream().map(ForwardPick::ymd).min(String::compareTo).orElseThrow();
        String to = pending.stream().map(ForwardPick::ymd).max(String::compareTo).orElseThrow();

        Map<String, Map<FixtureKey, BaseballResult>> byMarket = new HashMap<>();
        for (String gameType : List.of(
                PickUniverse.THREE_WAY_GAME_TYPE, PickUniverse.TWO_WAY_GAME_TYPE)) {
            Map<FixtureKey, BaseballResult> byFixture = new HashMap<>();
            for (BaseballResult game : baseballResultRepository
                    .findByGameTypeAndTournamentsAndYmdBetween(
                            gameType, PickUniverse.TOURNAMENTS, from, to)) {
                // Lowest id wins, matching how every other pairing in this codebase breaks ties.
                byFixture.merge(FixtureKey.of(game), game,
                        (kept, candidate) -> kept.id() <= candidate.id() ? kept : candidate);
            }
            byMarket.put(gameType, byFixture);
        }
        return byMarket;
    }
}
