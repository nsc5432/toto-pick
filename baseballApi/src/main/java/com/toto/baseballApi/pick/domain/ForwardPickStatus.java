package com.toto.baseballApi.pick.domain;

public enum ForwardPickStatus {

    /** Recorded, games not yet resolved. */
    PENDING,

    /** All legs matched to finished games and paid out. */
    SETTLED,

    /**
     * Abandoned without a payout — a leg's game never appeared in the results (postponed, cancelled,
     * relisted differently). Kept rather than deleted: a forward test that quietly drops the picks it
     * could not settle is choosing its own sample.
     */
    VOID
}
