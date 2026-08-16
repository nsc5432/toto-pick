package com.toto.baseballApi.pick.infrastructure.algorithm;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.toto.baseballApi.pick.domain.FavoriteMartingaleAlgorithm;
import com.toto.baseballApi.pick.domain.FavoriteOddsSlipAlgorithm;
import com.toto.baseballApi.pick.domain.MarketEdgeSlipAlgorithm;
import com.toto.baseballApi.pick.domain.PickAlgorithm;
import com.toto.baseballApi.pick.domain.WinRateOddsSlipSelector;

/**
 * Registers pure-logic {@link PickAlgorithm} implementations from the domain package as beans,
 * keeping the domain free of Spring annotations. Algorithms that need repositories should instead
 * live in this package as {@code @Component}s implementing the domain port. Either way, one bean
 * with a unique {@code code()} is all it takes to surface in the API, simulation, and dashboard.
 */
@Configuration
class PickAlgorithmConfig {

    @Bean
    PickAlgorithm winRateOddsAlgorithm() {
        return new WinRateOddsSlipSelector();
    }

    @Bean
    PickAlgorithm favoriteOddsAlgorithm() {
        return new FavoriteOddsSlipAlgorithm();
    }

    @Bean
    PickAlgorithm marketEdgeAlgorithm() {
        return new MarketEdgeSlipAlgorithm();
    }

    @Bean
    PickAlgorithm favoriteMartingaleAlgorithm() {
        return new FavoriteMartingaleAlgorithm();
    }
}
