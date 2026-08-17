package com.toto.baseballApi.research.presentation.dto;

import java.util.List;

import com.toto.baseballApi.research.domain.TwoWayOddsCheck;

public record TwoWayOddsCheckResponse(
        double oneRunHomeShare,
        int publishedGames,
        int pairedGames,
        int comparableGames,
        double meanAbsRelativeError,
        double meanSignedRelativeError,
        double twoWayFavoriteHitRate,
        double threeWayFavoriteHitRate,
        List<SideError> bySide,
        List<PriceBucket> byPriceBucket) {

    public record SideError(
            String side, int count, double meanAbsRelativeError, double meanSignedRelativeError) {
    }

    public record PriceBucket(
            String label, int count, double meanEstimatedFavoriteOdds,
            double twoWayFavoriteHitRate, double threeWayFavoriteHitRate,
            double meanAbsRelativeError) {
    }

    public static TwoWayOddsCheckResponse from(TwoWayOddsCheck check) {
        return new TwoWayOddsCheckResponse(
                check.oneRunHomeShare(),
                check.publishedGames(),
                check.pairedGames(),
                check.comparableGames(),
                check.meanAbsRelativeError(),
                check.meanSignedRelativeError(),
                check.twoWayFavoriteHitRate(),
                check.threeWayFavoriteHitRate(),
                check.bySide().stream()
                        .map(s -> new SideError(
                                s.side(), s.count(),
                                s.meanAbsRelativeError(), s.meanSignedRelativeError()))
                        .toList(),
                check.byPriceBucket().stream()
                        .map(b -> new PriceBucket(
                                b.label(), b.count(), b.meanEstimatedFavoriteOdds(),
                                b.twoWayFavoriteHitRate(), b.threeWayFavoriteHitRate(),
                                b.meanAbsRelativeError()))
                        .toList());
    }
}
