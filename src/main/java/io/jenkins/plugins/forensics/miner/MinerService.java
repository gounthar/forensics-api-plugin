package io.jenkins.plugins.forensics.miner;

import edu.hm.hafner.util.FilteredLog;

import java.util.List;
import java.util.Set;

import hudson.model.Run;

import io.jenkins.plugins.util.BuildAction;

/**
 * Queries the repository statistics of a build for a subselection of results.
 *
 * @author Ullrich Hafner
 */
public class MinerService {
    static final String NO_MINER_ERROR = "Repository miner is not configured, skipping repository mining";

    /**
     * Queries the statistics for the selected files of the aggregated repository statistics of the specified build.
     *
     * @param scm
     *         the SCM to get the results from (can be empty if there is just a single repository used)
     * @param build
     *         the build
     * @param files
     *         the files to get the statistics for
     * @param logger
     *         the logger
     *
     * @return the statistics for the selected files, if available
     */
    public RepositoryStatistics queryStatisticsFor(final String scm, final Run<?, ?> build,
            final Set<String> files, final FilteredLog logger) {
        var selected = new RepositoryStatistics();

        List<ForensicsBuildAction> actions = build.getActions(ForensicsBuildAction.class);
        if (actions.isEmpty()) {
            logger.logInfo(NO_MINER_ERROR);
            return selected;
        }

        var everything = actions.stream()
                .filter(a -> a.getScmKey().contains(scm))
                .findAny()
                .map(BuildAction::getResult)
                .orElse(new RepositoryStatistics());
        logger.logInfo("Extracting repository forensics for %d affected files (files in repository: %d)",
                files.size(), everything.size());

        for (String file : files) {
            if (everything.contains(file)) {
                selected.add(everything.get(file));
            }
            else {
                logger.logError("No statistics found for file '%s'", file);
            }
        }
        logger.logInfo("-> %d affected files processed", selected.size());
        return selected;
    }
}
