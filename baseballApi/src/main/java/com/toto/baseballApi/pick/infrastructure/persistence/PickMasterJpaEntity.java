package com.toto.baseballApi.pick.infrastructure.persistence;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pick_mstr")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class PickMasterJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pick_mstr_id")
    private Integer id;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "round", nullable = false)
    private Integer round;

    @Column(name = "ymd", length = 8)
    private String ymd;

    @Column(name = "user_name", nullable = false, length = 20)
    private String userName;

    @Column(name = "algorithm_code", length = 30)
    private String algorithmCode;

    @Column(name = "input_money", nullable = false)
    private BigDecimal inputMoney;

    @Column(name = "output_money")
    private BigDecimal outputMoney;
}
