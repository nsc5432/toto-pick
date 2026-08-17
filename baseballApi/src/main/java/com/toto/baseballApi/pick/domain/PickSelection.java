package com.toto.baseballApi.pick.domain;

/**
 * One leg of a slip: the game, and which outcome it backs.
 *
 * @param price the pre-game price of the backed side, or {@code null} to let settlement read it from
 *              the game's own {@code publishedOdds()}. Only algorithms betting a market whose rows
 *              carry no published quote — the 2-way "야구 승패" rows — need to supply it; see
 *              {@link BackedPrice}
 */
public record PickSelection(
        Integer resultId,
        String predictedTotalResult,
        BackedPrice price) {

    /** A leg priced from the game's own published odds — the usual case. */
    public PickSelection(Integer resultId, String predictedTotalResult) {
        this(resultId, predictedTotalResult, null);
    }
}
