package io.jenkins.plugins.forensics.miner;

import edu.hm.hafner.echarts.BuildResult;
import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.JacksonFacade;
import edu.hm.hafner.echarts.LineSeries;
import edu.hm.hafner.echarts.LineSeries.FilledMode;
import edu.hm.hafner.echarts.LineSeries.StackedMode;
import edu.hm.hafner.echarts.LinesChartModel;
import edu.hm.hafner.echarts.LinesDataSet;
import edu.hm.hafner.echarts.SeriesBuilder;

import io.jenkins.plugins.echarts.JenkinsPalette;

/**
 * Builds the Java side model for a trend chart showing the number modified files, commits and authors in the
 * repository. The trend chart contains three series, one for each criteria.  The number of builds to consider is
 * controlled by a {@link ChartModelConfiguration} instance. The created model object can be serialized to JSON (e.g.,
 * using the {@link JacksonFacade}) and can be used 1:1 as ECharts configuration object in the corresponding JS file.
 *
 * @author Ullrich Hafner
 * @see JacksonFacade
 */
class RelativeCountTrendChart {
    /**
     * Creates the chart for the specified results.
     *
     * @param results
     *         the forensics results to render - these results must be provided in descending order, i.e. the current
     *         build is the head of the list, then the previous builds, and so on
     * @param configuration
     *         the chart configuration to be used
     * @param seriesBuilder
     *         the builder to plot the data points
     * @param <T>
     *         the type of the action that stores the results
     *
     * @return the chart model, ready to be serialized to JSON
     */
    <T> LinesChartModel create(final Iterable<? extends BuildResult<T>> results,
            final ChartModelConfiguration configuration, final SeriesBuilder<T> seriesBuilder) {
        var dataSet = seriesBuilder.createDataSet(configuration, results);
        var model = new LinesChartModel(dataSet);
        if (dataSet.getDomainAxisSize() > 0) {
            var authors = getSeries(dataSet, "Authors", JenkinsPalette.BLUE,
                    RelativeCountForensicsSeriesBuilder.AUTHORS_KEY);
            var commits = getSeries(dataSet, "Commits", JenkinsPalette.GREEN,
                    RelativeCountForensicsSeriesBuilder.COMMITS_KEY);
            var files = getSeries(dataSet, "Modified files", JenkinsPalette.ORANGE,
                    RelativeCountForensicsSeriesBuilder.FILES_KEY);

            model.addSeries(authors, commits, files);
        }

        return model;
    }

    private LineSeries getSeries(final LinesDataSet dataSet,
            final String name, final JenkinsPalette color, final String dataSetId) {
        var series = new LineSeries(name, color.normal(), StackedMode.SEPARATE_LINES, FilledMode.LINES);
        series.addAll(dataSet.getSeries(dataSetId));
        return series;
    }
}
