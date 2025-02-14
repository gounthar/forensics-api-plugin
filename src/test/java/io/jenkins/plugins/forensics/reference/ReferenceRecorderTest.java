package io.jenkins.plugins.forensics.reference;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.VisibleForTesting;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.metadata.PrimaryInstanceMetadataAction;
import jenkins.scm.api.mixin.ChangeRequestSCMHead;

import io.jenkins.plugins.forensics.reference.ReferenceRecorder.ScmFacade;
import io.jenkins.plugins.util.JenkinsFacade;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link ReferenceRecorder}.
 *
 * @author Ullrich Hafner
 */
class ReferenceRecorderTest {
    /**
     * Verifies that the reference recorder has no initial value for the default branch.
     */
    @Test
    void shouldInitCorrectly() {
        var recorder = new NullReferenceRecorder();

        assertThat(recorder)
                .hasTargetBranch(StringUtils.EMPTY)
                .hasScm(StringUtils.EMPTY)
                .hasRequiredResult(Result.UNSTABLE)
                .isNotLatestBuildIfNotFound()
                .isNotConsiderRunningBuild();
    }

    /**
     * Verifies the first alternative: the current build is for a pull request part in a multi-branch project. In this
     *  case, the target branch stored in the PR will be used as the reference job.
     */
    @Test
    void shouldObtainReferenceFromPullRequestTarget() {
        var log = createLog();

        var referenceBuild = findReferenceBuild(log, build -> { });

        assertThat(log.getInfoMessages()).contains(
                "No reference job configured",
                "Found a `MultiBranchProject`, trying to resolve the target branch from the configuration",
                "-> no target branch configured in step",
                "-> detected a pull or merge request for target branch 'pr-target'",
                "-> inferred job for target branch: 'pr-target'",
                "Found reference build 'pr-id' for target branch",
                "-> Build 'pr-id' has a result SUCCESS");

        assertThat(referenceBuild).hasReferenceBuildId("pr-id");
    }

    /**
     * Verifies a variation of the first alternative: the found reference build has an insufficient result,
     * so the predecessor will be chosen.
     */
    @Test
    void shouldObtainReferenceFromPullRequestTargetWithMatchingResult() {
        var log = createLog();

        var referenceBuild = findReferenceBuild(log, build -> {
            var successful = createBuild("successful", Result.SUCCESS);
            when(build.getPreviousCompletedBuild()).thenAnswer(a -> successful);
            when(build.getResult()).thenReturn(Result.FAILURE); // reference failed
        });

        assertThat(log.getInfoMessages()).contains(
                "No reference job configured",
                "Found a `MultiBranchProject`, trying to resolve the target branch from the configuration",
                "-> no target branch configured in step",
                "-> detected a pull or merge request for target branch 'pr-target'",
                "-> inferred job for target branch: 'pr-target'",
                "Found reference build 'pr-id' for target branch",
                "-> Previous build 'successful' has a result SUCCESS");

        assertThat(referenceBuild).hasReferenceBuildId("successful");
    }

    /**
     * Verifies another variation of the first alternative: the found reference build has an insufficient result,
     * but the predecessor as well.
     */
    @Test
    void shouldFailToGetReferenceBecauseOfFailure() {
        var log = createLog();

        var referenceBuild = findReferenceBuild(log, build -> {
            var failure = createBuild("failure", Result.FAILURE);
            when(build.getPreviousCompletedBuild()).thenAnswer(a -> failure);
            when(build.getResult()).thenReturn(Result.FAILURE); // reference failed as well
        });

        assertThat(log.getInfoMessages()).contains(
                "No reference job configured",
                "Found a `MultiBranchProject`, trying to resolve the target branch from the configuration",
                "-> no target branch configured in step",
                "-> detected a pull or merge request for target branch 'pr-target'",
                "-> inferred job for target branch: 'pr-target'",
                "Found reference build 'pr-id' for target branch",
                "-> ignoring reference build 'pr-id' or one of its predecessors since none have a result of UNSTABLE or better",
                "No reference build with required status found that contains matching commits");

        assertThat(referenceBuild).doesNotHaveReferenceBuild();
    }

    private ReferenceBuild findReferenceBuild(final FilteredLog log, final Consumer<Run<?, ?>> configuration) {
        Run<?, ?> build = mock(Run.class);
        Job<?, ?> job = createJob(build);
        var topLevel = createMultiBranch(job);

        var recorder = createSut();

        Run<?, ?> prBuild = configurePrJobAndBuild(recorder, topLevel, job);
        when(recorder.find(build, prBuild, log)).thenReturn(Optional.of(prBuild));

        configuration.accept(prBuild);

        return recorder.findReferenceBuild(build, log);
    }

    private Run<?, ?> createBuild(final String displayName, final Result result) {
        Run<?, ?> build = mock(Run.class);
        when(build.getFullDisplayName()).thenReturn(displayName);
        when(build.getDisplayName()).thenReturn(displayName);
        when(build.getExternalizableId()).thenReturn(displayName);
        when(build.getResult()).thenReturn(result);
        return build;
    }

    /**
     * Verifies the second alternative: the current build is part of a multi-branch project (but not for a PR).
     * Additionally, a primary branch has been configured for the multi-branch project using the action {@link
     * PrimaryInstanceMetadataAction}. In this case, this configured primary target will be used as the reference job.
     */
    @Test
    void shouldFindReferenceJobUsingPrimaryBranch() {
        var log = createLog();

        Run<?, ?> build = mock(Run.class);
        Job<?, ?> job = createJob(build);
        var topLevel = createMultiBranch(job);

        var recorder = createSut();

        configurePrimaryBranch(recorder, topLevel, job, build, log);

        var referenceBuild = recorder.findReferenceBuild(build, log);

        assertThat(log.getInfoMessages()).contains(
                "No reference job configured",
                "Found a `MultiBranchProject`, trying to resolve the target branch from the configuration",
                "-> no target branch configured in step",
                "-> using configured primary branch 'main' of SCM as target branch",
                "Found reference build 'main-id' for target branch",
                "-> Build 'main-id' has a result SUCCESS");

        assertThat(referenceBuild).hasReferenceBuildId("main-id");
    }

    /**
     * Verifies the third alternative: the current build is for a pull request part in a multi-branch project. However,
     * the step has been configured with the parameter {@code targetBranch}. In this case, the configured target branch
     * will override the target branch of the pull PR.
     */
    @Test
    void targetShouldHavePrecedenceBeforePullRequestTarget() {
        var log = createLog();

        var referenceBuild = findReferenceWithRunningBuild(log, true);

        assertThat(log.getInfoMessages()).contains(
                "No reference job configured",
                "Found a `MultiBranchProject`, trying to resolve the target branch from the configuration",
                "-> using target branch 'target' as configured in step",
                "-> inferred job for target branch: 'target'",
                "Found reference build 'target-id' for target branch",
                "-> Build 'target-id' has a result SUCCESS");

        assertThat(referenceBuild).hasReferenceBuildId("target-id");
    }

    @Test
    void shouldIgnoreRunningBuildsIfOptionIsDeactivated() {
        var log = createLog();

        var noReferenceBuild = findReferenceWithRunningBuild(log, false);

        assertThat(log.getInfoMessages()).contains(
                "-> inferred job for target branch: 'target'",
                "No completed build found for reference job 'target'");

        assertThat(noReferenceBuild).hasReferenceBuildId("-");
    }

    private ReferenceBuild findReferenceWithRunningBuild(final FilteredLog log, final boolean isComplete) {
        var build = mock(Run.class);
        var job = createJob(build);
        var topLevel = createMultiBranch(job);

        var scmFacade = mock(ScmFacade.class);
        var recorder = createSut(scmFacade);

        configurePrJobAndBuild(recorder, topLevel, job); // will not be used since the target branch has been set
        configureTargetJobAndBuild(recorder, topLevel, build, log, isComplete);

        return recorder.findReferenceBuild(build, log);
    }

    @Test
    void shouldTakeCareOfRunningBuildsOption() {
        var log = createLog();

        Run<?, ?> build = mock(Run.class);
        Job<?, ?> job = createJob(build);
        var topLevel = createMultiBranch(job);

        var scmFacade = mock(ScmFacade.class);
        var recorder = createSut(scmFacade);
        recorder.setConsiderRunningBuild(true);

        configurePrJobAndBuild(recorder, topLevel, job); // will not be used since the target branch has been set
        configureTargetJobAndBuild(recorder, topLevel, build, log, false);

        when(recorder.isConsiderRunningBuild()).thenReturn(true);
        var referenceBuild = recorder.findReferenceBuild(build, log);

        assertThat(log.getInfoMessages()).contains(
                "No reference job configured",
                "Found a `MultiBranchProject`, trying to resolve the target branch from the configuration",
                "-> using target branch 'target' as configured in step",
                "-> inferred job for target branch: 'target'",
                "Found reference build 'target-id' for target branch",
                "-> Build 'target-id' has a result SUCCESS");

        assertThat(referenceBuild).hasReferenceBuildId("target-id");
    }

    /**
     * Verifies the fourth alternative: the current build is part of a multi-branch project (but not for a PR).
     * Additionally, a primary branch has been configured for the multi-branch project using the action {@link
     * PrimaryInstanceMetadataAction}. However, the step has been configured with the parameter {@code targetBranch}. In
     * this case, the configured target branch will override the primary branch.
     */
    @Test
    void targetShouldHavePrecedenceBeforePrimaryBranchTarget() {
        var log = createLog();

        Run<?, ?> build = mock(Run.class);
        Job<?, ?> job = createJob(build);
        var topLevel = createMultiBranch(job);

        var recorder = createSut();

        configurePrimaryBranch(recorder, topLevel, job, build, log); // will not be used since target branch has been set
        configureTargetJobAndBuild(recorder, topLevel, build, log, true);

        var referenceBuild = recorder.findReferenceBuild(build, log);

        assertThat(log.getInfoMessages())
                .contains("No reference job configured",
                        "Found a `MultiBranchProject`, trying to resolve the target branch from the configuration",
                        "-> using target branch 'target' as configured in step",
                        "-> inferred job for target branch: 'target'",
                        "Found reference build 'target-id' for target branch",
                        "-> Build 'target-id' has a result SUCCESS");

        assertThat(referenceBuild).hasReferenceBuildId("target-id");
    }

    /**
     * Verifies the fallback alternative: neither a PR, nor a primary branch, nor a target branch parameter are set. In
     * this case the default target branch {@code master} will be used.
     */
    @Test
    void shouldNotFindReferenceJobForMultiBranchProject() {
        var log = createLog();

        Run<?, ?> build = mock(Run.class);
        Job<?, ?> job = createJob(build);
        createMultiBranch(job);

        var recorder = createSut();
        var referenceBuild = recorder.findReferenceBuild(build, log);

        assertThat(log.getInfoMessages())
                .anySatisfy(m -> assertThat(m).contains("Found a `MultiBranchProject`"))
                .anySatisfy(m -> assertThat(m).contains("falling back to plugin default target branch 'master'"));

        assertThat(referenceBuild.getReferenceBuild()).isEmpty();
    }

    private ReferenceRecorder createSut() {
        return createSut(mock(ScmFacade.class));
    }

    private ReferenceRecorder createSut(final ScmFacade scmFacade) {
        var recorder = spy(ReferenceRecorder.class);
        when(recorder.getScmFacade()).thenReturn(scmFacade);
        return recorder;
    }

    private FilteredLog createLog() {
        return new FilteredLog("EMPTY");
    }

    private WorkflowMultiBranchProject createMultiBranch(final Job<?, ?> job) {
        WorkflowMultiBranchProject parent = mock(WorkflowMultiBranchProject.class);
        when(job.getParent()).thenAnswer(i -> parent);
        return parent;
    }

    private Job<?, ?> createJob(final Run<?, ?> build) {
        Job<?, ?> job = mock(Job.class);
        when(build.getParent()).thenAnswer(i -> job);
        return job;
    }

    private void configurePrimaryBranch(final ReferenceRecorder recorder,
            final WorkflowMultiBranchProject topLevel, final Job<?, ?> job,
            final Run<?, ?> build, final FilteredLog log) {
        Job<?, ?> main = mock(Job.class);
        Run<?, ?> mainBuild = createBuild("main-id", Result.SUCCESS);

        when(main.getLastCompletedBuild()).thenAnswer(i -> mainBuild);
        when(main.getDisplayName()).thenReturn("main");
        when(main.getAction(PrimaryInstanceMetadataAction.class)).thenReturn(mock(PrimaryInstanceMetadataAction.class));

        Item item = mock(Item.class);
        when(item.getAllJobs()).thenAnswer(i -> Arrays.<Job<?, ?>>asList(job, main));

        when(topLevel.getAllItems()).thenReturn(Collections.singletonList(item));

        when(recorder.find(build, mainBuild, log)).thenReturn(Optional.of(mainBuild));
    }

    private Run<?, ?> configurePrJobAndBuild(final ReferenceRecorder recorder,
            final WorkflowMultiBranchProject parent, final Job<?, ?> job) {
        Job<?, ?> prJob = mock(Job.class);
        when(prJob.getDisplayName()).thenReturn("pr-target");
        when(parent.getItemByBranchName("pr-target")).thenAnswer(i -> prJob);
        Run<?, ?> prBuild = createBuild("pr-id", Result.SUCCESS);
        when(prJob.getLastCompletedBuild()).thenAnswer(i -> prBuild);

        ChangeRequestSCMHead pr = mock(ChangeRequestSCMHead.class);
        when(pr.toString()).thenReturn("pr");
        ScmFacade scmFacade = mock(ScmFacade.class);
        when(scmFacade.findHead(job)).thenReturn(Optional.of(pr));
        SCMHead target = mock(SCMHead.class);
        when(pr.getTarget()).thenReturn(target);
        when(target.getName()).thenReturn("pr-target");

        when(recorder.getScmFacade()).thenReturn(scmFacade);

        return prBuild;
    }

    private Run<?, ?> configureTargetJobAndBuild(final ReferenceRecorder recorder,
            final WorkflowMultiBranchProject parent, final Run<?, ?> build, final FilteredLog log,
            final boolean isComplete) {
        recorder.setTargetBranch("target");

        Job<?, ?> targetJob = mock(Job.class);
        when(targetJob.getDisplayName()).thenReturn("target");
        when(parent.getItemByBranchName("target")).thenAnswer(i -> targetJob);
        Run<?, ?> targetBuild = createBuild("target-id", Result.SUCCESS);
        if (isComplete) {
            when(targetJob.getLastCompletedBuild()).thenAnswer(i -> targetBuild);
        }
        else {
            when(targetJob.getLastBuild()).thenAnswer(i -> targetBuild);
        }
        when(recorder.find(build, targetBuild, log)).thenReturn(Optional.of(targetBuild));

        return targetBuild;
    }

    private static class NullReferenceRecorder extends ReferenceRecorder {
        @VisibleForTesting
        NullReferenceRecorder() {
            super(new JenkinsFacade());
        }
    }
}
