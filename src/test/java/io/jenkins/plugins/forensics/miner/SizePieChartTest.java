package io.jenkins.plugins.forensics.miner;

import org.junit.jupiter.api.Test;

import edu.hm.hafner.echarts.PieData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SizePieChartTest {
    @Test
    void shouldCreateEmptyModel() {
        var chart = new SizePieChart();
        RepositoryStatistics repositoryStatisticsStub = mock(RepositoryStatistics.class);
        int breakpoint = 1;

        var model = chart.create(repositoryStatisticsStub, FileStatistics::getNumberOfCommits, breakpoint);

        assertThat(model.getData()).isEmpty();
    }

    @Test
    void shouldCreateSinglePiSegment() {
        var chart = new SizePieChart();
        int numberOfFileStatistics = 1;
        var repositoryStatisticsStub = getRepositoryStatisticsStub(numberOfFileStatistics);
        int breakpoint = 1;
        List<PieData> list = getPieData(breakpoint);

        var model = chart.create(repositoryStatisticsStub, FileStatistics::getNumberOfCommits, breakpoint);

        assertThat(model.getData())
                .isNotEmpty()
                .hasSize(list.size())
                .isEqualTo(list);
    }

    private List<PieData> getPieData(final int... breakpoint) {
        List<PieData> list = new ArrayList<>();
        for (int i : breakpoint) {
            var pieData = new PieData("< " + i, 1);
            list.add(pieData);
        }
        return list;
    }

    private RepositoryStatistics getRepositoryStatisticsStub(final int numberOfFileStatistics) {
        RepositoryStatistics repositoryStatisticsStub = mock(RepositoryStatistics.class);
        Set<FileStatistics> hashSet = new HashSet<>();
        for (int i = 0; i < numberOfFileStatistics; i++) {
            FileStatistics fileStatistics = mock(FileStatistics.class);
            when(fileStatistics.getNumberOfCommits()).thenReturn(i);
            when(fileStatistics.getFileName()).thenReturn("testName" + i);
            hashSet.add(fileStatistics);
        }
        when(repositoryStatisticsStub.getFileStatistics()).thenReturn(hashSet);
        FileStatistics test = mock(FileStatistics.class);
        when(test.getNumberOfCommits()).thenReturn(2);

        return repositoryStatisticsStub;
    }

    @Test
    void shouldCreateTwoPieSegments() {
        var chart = new SizePieChart();
        int numberOfFileStatistics = 2;
        var repositoryStatisticsStub = getRepositoryStatisticsStub(numberOfFileStatistics);
        int breakpoint1 = 1;
        int breakpoint2 = 2;
        List<PieData> list = getPieData(breakpoint1, breakpoint2);

        var model = chart.create(repositoryStatisticsStub, FileStatistics::getNumberOfCommits, breakpoint1,
                breakpoint2);

        assertThat(model.getData())
                .isNotEmpty()
                .hasSize(list.size())
                .isEqualTo(list);
    }
}
