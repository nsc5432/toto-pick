package com.toto.baseballApi.research.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.toto.baseballApi.baseballresult.domain.BaseballResult;
import com.toto.baseballApi.baseballresult.domain.BaseballResultRepository;
import com.toto.baseballApi.baseballresult.domain.ThreeWayOdds;
import com.toto.baseballApi.baseballresult.domain.TwoWayOdds;
import com.toto.baseballApi.baseballresult.domain.TwoWayOddsEstimator;
import com.toto.baseballApi.pick.domain.MarketPairIndex;
import com.toto.baseballApi.pick.domain.PickUniverse;
import com.toto.baseballApi.research.domain.TwoWayOddsCheck;

import lombok.RequiredArgsConstructor;

/**
 * Read-only validation of {@code TwoWayOddsEstimator} against real 승패 payouts. Writes nothing,
 * like everything else in this feature.
 *
 * <p>Run this before trusting any 2-way result. See {@link TwoWayOddsCheck} for why the comparison is
 * legitimate despite reading {@code TOTAL_DIV}.
 */
@Service
@RequiredArgsConstructor
public class TwoWayOddsCheckService {

    private static final String HOME_WIN = "승";
    private static final String AWAY_WIN = "패";

    /**
     * Upper bounds of the favorite-price buckets; the last bucket is everything above. Tuned to the
     * range a 2-way favorite can actually occupy: its probability is at least 0.5 by definition, so
     * its price never exceeds {@code 1/(0.5 × 1.13629) ≈ 1.76}. Wider buckets would sit empty.
     */
    private static final double[] BUCKET_BOUNDS = {1.30, 1.40, 1.50, 1.60, 1.70};

    private final BaseballResultRepository baseballResultRepository;

    @Transactional(readOnly = true)
    public TwoWayOddsCheck check(String bgngYmd, String endYmd, Double oneRunHomeShare) {
        if (bgngYmd.compareTo(endYmd) > 0) {
            throw new IllegalArgumentException("bgngYmd must not be after endYmd");
        }
        double share = oneRunHomeShare == null
                ? TwoWayOddsEstimator.DEFAULT_ONE_RUN_HOME_SHARE
                : oneRunHomeShare;

        List<BaseballResult> threeWayGames = baseballResultRepository
                .findByGameTypeAndTournamentsAndYmdBetween(
                        PickUniverse.THREE_WAY_GAME_TYPE, PickUniverse.TOURNAMENTS, bgngYmd, endYmd);
        MarketPairIndex pairs = MarketPairIndex.build(baseballResultRepository
                .findByGameTypeAndTournamentsAndYmdBetween(
                        PickUniverse.TWO_WAY_GAME_TYPE, PickUniverse.TOURNAMENTS, bgngYmd, endYmd));

        List<Sample> samples = new ArrayList<>();
        int publishedGames = 0;
        int pairedGames = 0;

        for (BaseballResult game : PickUniverse.distinctFixtures(threeWayGames)) {
            ThreeWayOdds published = game.publishedOdds();
            if (published == null) {
                continue;
            }
            publishedGames++;

            BaseballResult pair = pairs.pairOf(game);
            if (pair == null || pair.totalResult() == null || pair.totalDiv() == null
                    || pair.totalDiv() <= 1.0) {
                continue;
            }
            pairedGames++;

            TwoWayOdds estimated = TwoWayOddsEstimator.from(published, share);
            if (estimated == null || estimated.home() == estimated.away()) {
                continue;
            }
            boolean homeFavored = estimated.home() < estimated.away();
            String backed = homeFavored ? HOME_WIN : AWAY_WIN;
            double favoriteOdds = homeFavored ? estimated.home() : estimated.away();

            // The realized side is the one whose real price we can see, so that is where the
            // estimate gets checked. Which side that is depends on the outcome — fine for measuring
            // price error, and the reason the result is also reported split by side.
            String realized = pair.totalResult();
            if (!HOME_WIN.equals(realized) && !AWAY_WIN.equals(realized)) {
                continue;
            }
            double estimatedRealized = HOME_WIN.equals(realized) ? estimated.home() : estimated.away();
            double relativeError = (estimatedRealized - pair.totalDiv()) / pair.totalDiv();

            samples.add(new Sample(
                    favoriteOdds,
                    realized,
                    relativeError,
                    backed.equals(pair.totalResult()),
                    backed.equals(game.totalResult())));
        }

        return new TwoWayOddsCheck(
                share,
                publishedGames,
                pairedGames,
                samples.size(),
                mean(samples, s -> Math.abs(s.relativeError())),
                mean(samples, Sample::relativeError),
                rate(samples, Sample::twoWayFavoriteHit),
                rate(samples, Sample::threeWayFavoriteHit),
                bySide(samples),
                byPriceBucket(samples));
    }

    private record Sample(
            double favoriteOdds,
            String realizedSide,
            double relativeError,
            boolean twoWayFavoriteHit,
            boolean threeWayFavoriteHit) {
    }

    private static List<TwoWayOddsCheck.SideError> bySide(List<Sample> samples) {
        List<TwoWayOddsCheck.SideError> bySide = new ArrayList<>();
        for (String side : List.of(HOME_WIN, AWAY_WIN)) {
            List<Sample> subset = samples.stream()
                    .filter(s -> side.equals(s.realizedSide()))
                    .toList();
            bySide.add(new TwoWayOddsCheck.SideError(
                    side,
                    subset.size(),
                    mean(subset, s -> Math.abs(s.relativeError())),
                    mean(subset, Sample::relativeError)));
        }
        return bySide;
    }

    private static List<TwoWayOddsCheck.PriceBucket> byPriceBucket(List<Sample> samples) {
        Map<String, List<Sample>> byBucket = new LinkedHashMap<>();
        for (String label : bucketLabels()) {
            byBucket.put(label, new ArrayList<>());
        }
        for (Sample sample : samples) {
            byBucket.get(bucketLabel(sample.favoriteOdds())).add(sample);
        }
        return byBucket.entrySet().stream()
                .map(entry -> new TwoWayOddsCheck.PriceBucket(
                        entry.getKey(),
                        entry.getValue().size(),
                        mean(entry.getValue(), Sample::favoriteOdds),
                        rate(entry.getValue(), Sample::twoWayFavoriteHit),
                        rate(entry.getValue(), Sample::threeWayFavoriteHit),
                        mean(entry.getValue(), s -> Math.abs(s.relativeError()))))
                .toList();
    }

    private static List<String> bucketLabels() {
        List<String> labels = new ArrayList<>();
        double low = 1.0;
        for (double bound : BUCKET_BOUNDS) {
            labels.add(format(low) + "-" + format(bound));
            low = bound;
        }
        labels.add(format(low) + "+");
        return labels;
    }

    private static String bucketLabel(double odds) {
        double low = 1.0;
        for (double bound : BUCKET_BOUNDS) {
            if (odds < bound) {
                return format(low) + "-" + format(bound);
            }
            low = bound;
        }
        return format(low) + "+";
    }

    private static String format(double bound) {
        return String.format("%.2f", bound);
    }

    private static double mean(List<Sample> samples, java.util.function.ToDoubleFunction<Sample> value) {
        return samples.isEmpty() ? 0.0 : samples.stream().mapToDouble(value).average().orElse(0.0);
    }

    private static double rate(List<Sample> samples, java.util.function.Predicate<Sample> hit) {
        return samples.isEmpty() ? 0.0 : (double) samples.stream().filter(hit).count() / samples.size();
    }
}
