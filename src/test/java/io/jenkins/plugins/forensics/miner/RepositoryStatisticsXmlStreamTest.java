package io.jenkins.plugins.forensics.miner;

import org.junit.jupiter.api.Test;

import edu.hm.hafner.util.SerializableTest;
import edu.hm.hafner.util.TreeString;
import edu.hm.hafner.util.TreeStringBuilder;

import io.jenkins.plugins.forensics.miner.FileStatistics.FileStatisticsBuilder;

import static io.jenkins.plugins.forensics.assertions.Assertions.*;

/**
 * Tests the class {@link RepositoryStatisticsXmlStream}.
 *
 * @author Ullrich Hafner
 */
class RepositoryStatisticsXmlStreamTest extends SerializableTest<RepositoryStatistics> {
    private static final String FILE = "/path/to/file.txt";
    private static final TreeString FILE_TREE_STRING = new TreeStringBuilder().intern(FILE);
    private static final int ONE_DAY = 60 * 60 * 24;
    private static final String ISSUE_BUILDER = "/analysis/IssueBuilder.java";

    @Test
    void shouldReadBlamesOfForensics062() {
        assertThatForensicsAreCorrect(read("forensics-0.6.2.xml"));
    }

    @Test
    void shouldReadBlamesOfForensics070() {
        assertThatForensicsAreCorrect(read("forensics-0.7.0.xml"));
    }

    private RepositoryStatistics read(final String fileName) {
        var repositoryStatisticsReader = new RepositoryStatisticsXmlStream();

        return repositoryStatisticsReader.read(getResourceAsFile(fileName));
    }

    private void assertThatForensicsAreCorrect(final RepositoryStatistics statistics) {
        assertThat(statistics)
                .hasOnlyFiles(ISSUE_BUILDER, "/analysis/Report.java", "/analysis/FilteredLog.java");

        var fileStatistics = statistics.get(ISSUE_BUILDER);
        assertThat(fileStatistics).hasFileName(ISSUE_BUILDER)
                .hasCreationTime(1_506_775_701)
                .hasLastModificationTime(1_546_429_687)
                .hasNumberOfAuthors(1)
                .hasNumberOfCommits(32);
    }

    @Test
    void shouldWriteReport() {
        var statistics = new RepositoryStatistics();
        var fileStatistics = new FileStatisticsBuilder().build(FILE);
        var first = new CommitDiffItem("1", "name", ONE_DAY * 2)
                .addLines(4)
                .setNewPath(FILE_TREE_STRING);
        var second = new CommitDiffItem("2", "another", ONE_DAY * 3)
                .addLines(4)
                .deleteLines(3)
                .setNewPath(FILE_TREE_STRING);
        var third = new CommitDiffItem("3", "another", ONE_DAY * 4)
                .deleteLines(2)
                .setNewPath(FILE_TREE_STRING);
        fileStatistics.inspectCommit(first);
        fileStatistics.inspectCommit(second);
        fileStatistics.inspectCommit(third);
        statistics.add(fileStatistics);

        var stream = new RepositoryStatisticsXmlStream();
        var path = createTempFile();
        stream.write(path, statistics);

        var restored = stream.read(path);

        assertThat(restored).hasFiles(FILE);
        var restoredFile = restored.get(FILE);
        assertThat(restoredFile)
                .hasNumberOfAuthors(2)
                .hasNumberOfCommits(3)
                .hasLinesOfCode(8 - 5)
                .hasAbsoluteChurn(8 + 5)
                .hasCreationTime(ONE_DAY * 2)
                .hasLastModificationTime(ONE_DAY * 4);
    }

    @Override
    protected RepositoryStatistics createSerializable() {
        return read("forensics-0.7.0.xml");
    }
}
