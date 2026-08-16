package com.toto.baseballApi.research.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.toto.baseballApi.research.application.ResearchProperties;

/** Binds {@code research.*} from {@code application.yaml} so the goal and search budget are config, not code. */
@Configuration
@EnableConfigurationProperties(ResearchProperties.class)
class ResearchConfig {
}
