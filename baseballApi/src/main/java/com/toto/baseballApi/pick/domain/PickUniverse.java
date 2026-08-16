package com.toto.baseballApi.pick.domain;

import java.util.List;

/**
 * Which games the pick engine is allowed to see — one definition, shared by the persisting
 * simulation and the research backtest.
 *
 * <p>If the two disagreed on the eligible tournaments or the game types, a parameter search would
 * be tuning against a different universe than the one the simulation later bets on, and every
 * number it produced would be off in a way nothing would flag.
 */
public final class PickUniverse {

    /** Tournaments eligible for picks. */
    public static final List<String> TOURNAMENTS = List.of("KBO", "NPB", "MLB");

    /** The market picks are placed in — home/draw/away, so a draw is a distinct outcome. */
    public static final String THREE_WAY_GAME_TYPE = "야구 승1패";

    /** The market team form is measured from — no draw, so a win rate is well defined. */
    public static final String TWO_WAY_GAME_TYPE = "야구 승패";

    private PickUniverse() {
    }
}
