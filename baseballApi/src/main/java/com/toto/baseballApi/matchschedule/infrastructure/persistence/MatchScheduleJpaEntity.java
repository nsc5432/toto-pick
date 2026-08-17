package com.toto.baseballApi.matchschedule.infrastructure.persistence;

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
@Table(name = "match_schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MatchScheduleJpaEntity {

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

    @Column(name = "PUB_HOME_DIV")
    private Double pubHomeDiv;

    @Column(name = "PUB_DRAW_DIV")
    private Double pubDrawDiv;

    @Column(name = "PUB_AWAY_DIV")
    private Double pubAwayDiv;
}
