package com.toto.baseballApi.baseballresult.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "baseball_result")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class BaseballResultJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

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

    @Column(name = "COND", length = 10)
    private String cond;

    @Column(name = "RES1", nullable = false)
    private Double res1;

    @Column(name = "RES2")
    private Double res2;

    @Column(name = "TOTAL_RESULT", nullable = false, length = 5)
    private String totalResult;

    @Column(name = "TOTAL_DIV", nullable = false)
    private Double totalDiv;

    @Column(name = "HOME_DIV")
    private Double homeDiv;

    @Column(name = "DRAW_DIV")
    private Double drawDiv;

    @Column(name = "AWAY_DIV")
    private Double awayDiv;

    /**
     * Package-private on purpose: the entity stays setter-free for the outside world;
     * only the repository adapter in this package may apply computed odds.
     */
    void applyDivs(Double homeDiv, Double drawDiv, Double awayDiv) {
        this.homeDiv = homeDiv;
        this.drawDiv = drawDiv;
        this.awayDiv = awayDiv;
    }
}
