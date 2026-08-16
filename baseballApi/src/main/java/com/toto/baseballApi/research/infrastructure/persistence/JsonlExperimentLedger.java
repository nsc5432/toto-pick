package com.toto.baseballApi.research.infrastructure.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.toto.baseballApi.research.application.ResearchProperties;
import com.toto.baseballApi.research.domain.Experiment;
import com.toto.baseballApi.research.domain.ExperimentLedger;
import com.toto.baseballApi.research.domain.ObjectiveScore;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The experiment ledger as one JSON object per line.
 *
 * <p>A plain file rather than a table, for two reasons. The schema is applied by hand in this
 * project ({@code ddl-auto: none}, no migration tool), so a new table would be a manual step
 * standing between someone and their first search. And the file is the interface an automated
 * search loop actually wants: an agent can read, grep and diff the history directly, without the
 * API running, and it lands in version control next to the algorithms it describes.
 *
 * <p>Appending is line-at-a-time and synchronized, so a crash mid-sweep truncates at a line
 * boundary and costs at most the run in flight. A line that fails to parse is skipped with a
 * warning rather than failing the read — a corrupt tail must not make the whole history unreadable.
 */
@Component
class JsonlExperimentLedger implements ExperimentLedger {

    private static final Logger log = LoggerFactory.getLogger(JsonlExperimentLedger.class);

    /**
     * Its own mapper rather than the injected web one, so the ledger's on-disk format cannot be
     * changed out from under years of accumulated history by a later tweak to API serialization.
     */
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final Path path;

    JsonlExperimentLedger(ResearchProperties properties) {
        this.path = Path.of(properties.ledger().path()).toAbsolutePath();
    }

    @Override
    public synchronized void appendAll(List<Experiment> experiments) {
        if (experiments.isEmpty()) {
            return;
        }
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<String> lines = new ArrayList<>(experiments.size());
            for (Experiment experiment : experiments) {
                lines.add(objectMapper.writeValueAsString(experiment));
            }
            Files.write(path, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("Appended {} experiment(s) to {}", experiments.size(), path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to append to experiment ledger " + path, e);
        }
    }

    @Override
    public List<Experiment> findAll() {
        if (!Files.exists(path)) {
            return List.of();
        }
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            return lines.map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .map(this::parse)
                    .filter(experiment -> experiment != null)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read experiment ledger " + path, e);
        }
    }

    private Experiment parse(String line) {
        try {
            return objectMapper.readValue(line, Experiment.class);
        } catch (JacksonException e) {
            log.warn("Skipping unparseable ledger line in {}: {}", path, e.getMessage());
            return null;
        }
    }

    @Override
    public List<Experiment> leaderboard(int limit) {
        Map<String, Experiment> bestByAlgorithm = new LinkedHashMap<>();
        for (Experiment experiment : findAll()) {
            bestByAlgorithm.merge(experiment.algorithmCode(), experiment,
                    (existing, candidate) ->
                            ObjectiveScore.BEST_FIRST.compare(candidate.score(), existing.score()) < 0
                                    ? candidate : existing);
        }
        return bestByAlgorithm.values().stream()
                .sorted(Comparator.comparing(Experiment::score, ObjectiveScore.BEST_FIRST))
                .limit(limit)
                .toList();
    }
}
