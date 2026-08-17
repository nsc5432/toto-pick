package com.toto.baseballApi.pick.infrastructure.persistence;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pick_forward_leg")
@IdClass(ForwardPickLegId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class ForwardPickLegJpaEntity {

    @Id
    @Column(name = "pick_forward_id")
    private Integer forwardPickId;

    @Id
    @Column(name = "leg_no")
    private Integer legNo;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "round", nullable = false)
    private Integer round;

    @Column(name = "TOURNAMENT", nullable = false, length = 20)
    private String tournament;

    @Column(name = "YMD", nullable = false, length = 8)
    private String ymd;

    @Column(name = "TM", nullable = false, length = 5)
    private String tm;

    @Column(name = "HOME", nullable = false, length = 20)
    private String home;

    @Column(name = "AWAY", nullable = false, length = 20)
    private String away;

    @Column(name = "GAME_TYPE", nullable = false, length = 20)
    private String gameType;

    @Column(name = "total_result", nullable = false, length = 5)
    private String totalResult;

    @Column(name = "backed_odds", nullable = false)
    private BigDecimal backedOdds;

    @Column(name = "overround", nullable = false)
    private BigDecimal overround;

    // Settlement-time only.
    @Setter
    @Column(name = "result_id")
    private Integer resultId;

    @Setter
    @Column(name = "leg_result", length = 5)
    private String legResult;
}
