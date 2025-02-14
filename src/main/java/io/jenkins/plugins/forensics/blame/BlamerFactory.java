package io.jenkins.plugins.forensics.blame;

import edu.hm.hafner.util.FilteredLog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import hudson.ExtensionPoint;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.SCM;

import io.jenkins.plugins.forensics.blame.Blamer.NullBlamer;
import io.jenkins.plugins.forensics.util.ScmResolver;
import io.jenkins.plugins.util.JenkinsFacade;

/**
 * Jenkins' extension point that allows plugins to create {@link Blamer} instances based on a supported {@link SCM}.
 *
 * @author Ullrich Hafner
 */
public abstract class BlamerFactory implements ExtensionPoint {
    /**
     * Returns a blamer for the specified {@link SCM}.
     *
     * @param scm
     *         the {@link SCM} to create the blamer for
     * @param run
     *         the current build
     * @param workspace
     *         the workspace of the current build
     * @param listener
     *         a task listener
     * @param logger
     *         a logger to report error messages
     *
     * @return a blamer instance that can blame authors for the specified {@link SCM}
     */
    public abstract Optional<Blamer> createBlamer(SCM scm, Run<?, ?> run, FilePath workspace,
            TaskListener listener, FilteredLog logger);

    /**
     * Returns a blamer for the specified {@link Run build}.
     *
     * @param run
     *         the current build
     * @param scmDirectories
     *         paths to search for the SCM repository
     * @param listener
     *         a task listener
     * @param logger
     *         a logger to report error messages
     *
     * @return a blamer for the SCM of the specified build or a {@link NullBlamer} if the SCM is not supported
     */
    public static Blamer findBlamer(final Run<?, ?> run,
            final Collection<FilePath> scmDirectories, final TaskListener listener, final FilteredLog logger) {
        return scmDirectories.stream()
                .map(directory -> findBlamer(run, directory, listener, logger))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseGet(() -> createNullBlamer(logger));
    }

    /**
     * Returns a blamer for the specified {@link SCM repository}.
     *
     * @param scm
     *         the key of the SCM repository (substring that must be part of the SCM key)
     * @param run
     *         the current build
     * @param workTree
     *         the working tree of the repository
     * @param listener
     *         a task listener
     * @param logger
     *         a logger to report error messages
     *
     * @return a blamer for the SCM of the specified build or a {@link NullBlamer} if the SCM is not supported
     */
    public static Blamer findBlamer(final String scm, final Run<?, ?> run,
            final FilePath workTree, final TaskListener listener, final FilteredLog logger) {
        Collection<? extends SCM> scms = new ScmResolver().getScms(run, scm);
        if (scms.isEmpty()) {
            logger.logInfo("-> no SCM found");
            return new NullBlamer();
        }
        return findAllExtensions().stream()
                .map(blamerFactory -> blamerFactory.createBlamer(scms.iterator().next(), run, workTree, listener, logger))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseGet(() -> createNullBlamer(logger));
    }

    private static Blamer createNullBlamer(final FilteredLog logger) {
        if (findAllExtensions().isEmpty()) {
            logger.logInfo("-> No blamer installed yet. You need to install the 'git-forensics' plugin to enable blaming for Git.");
        }
        else {
            logger.logInfo("-> No suitable blamer found.");
        }
        return new NullBlamer();
    }

    private static Optional<Blamer> findBlamer(final Run<?, ?> run, final FilePath workTree,
            final TaskListener listener, final FilteredLog logger) {
        var scm = new ScmResolver().getScm(run);

        return findAllExtensions().stream()
                .map(blamerFactory -> blamerFactory.createBlamer(scm, run, workTree, listener, logger))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static List<BlamerFactory> findAllExtensions() {
        return new JenkinsFacade().getExtensionsFor(BlamerFactory.class);
    }
}
