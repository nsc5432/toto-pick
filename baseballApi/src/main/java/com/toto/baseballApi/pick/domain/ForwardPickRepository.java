package com.toto.baseballApi.pick.domain;

import java.util.List;

public interface ForwardPickRepository {

    /** Appends a new pre-game pick and returns it with its assigned id. */
    ForwardPick append(ForwardPick pick);

    /**
     * Whether this algorithm already bet this slot. Generation is idempotent by design: re-running it
     * for a date must not produce a second bet on the same slot (마틴게일 R2), and must not let a
     * later re-run silently replace a record whose whole value is that it was written first.
     */
    boolean existsSlip(
            String algorithmCode, String userName, String ymd, String bucket, int slipNo);

    /** Every pick for one algorithm, chronologically — the order a staking session must be replayed in. */
    List<ForwardPick> findByAlgorithm(String algorithmCode, String userName);

    /**
     * Every pick on record, chronologically. Unpaginated on purpose: the forward test accumulates a
     * handful of slips a day by construction, and the report has to see all of them — a cumulative
     * edge measured over a page is not a cumulative edge.
     */
    List<ForwardPick> findAll();

    /** Writes only the settlement fields; everything recorded before the game stays untouched. */
    void settle(ForwardPick settled);
}
