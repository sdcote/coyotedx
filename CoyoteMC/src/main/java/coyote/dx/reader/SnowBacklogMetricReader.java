/*
 * Copyright (c) 2019 Stephan D. Cote' - All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the MIT License which accompanies this distribution, and is
 * available at http://creativecommons.org/licenses/MIT/
 */

package coyote.dx.reader;

import coyote.commons.StringUtil;
import coyote.commons.network.http.Method;
import coyote.dataframe.DataFrame;
import coyote.dx.CMC;
import coyote.dx.ConfigTag;
import coyote.dx.FrameReader;
import coyote.dx.context.TransactionContext;
import coyote.dx.context.TransformContext;
import coyote.loader.log.Log;
import coyote.mc.snow.*;

import java.net.URISyntaxException;
import java.util.*;

import static coyote.mc.snow.Predicate.IS;
import static coyote.mc.snow.Predicate.LIKE;

/**
 * This is a reader which connects to a ServiceNow instance and queries data via URL export and generates metrics based
 * on the stories in the "rm_story" table.
 *
 * <p>The goal of this reader is to generate metrics for sending backlog metrics to a writer for reporting or
 * publication to a database for later reporting.</p>
 *
 * <p>The following metrics are generated by this reader:<ul>
 * <li></li>
 * <li>{@code backlog_defect_new_count} - the number of new defects this sprint.</li>
 * <li>{@code backlog_active_count} - the number of active backlog items.</li>
 * <li>{@code backlog_feature_new_count} - the number of new features this sprint.</li>
 * <li>{@code backlog_active_new_count} - the number of new active items this sprint.</li>
 * <li>{@code backlog_[class]_count} - the number of items in this backlog with the classification of {@code [class]}.</li>
 * </ul>
 *
 * <p>This reader extends the WebServiceReader to make use of a common configuration and HTTP client framework. It
 * differs from other readers of this type by performing a read of all records from the source system on the first read
 * request at which time it consumes all the records, generates all its metrics then replaces the read-in records
 * (dataframes) with the generated metric dataframes.</p>
 *
 * <p>The following is a sample configuration:<pre>
 * "Reader" : {
 *   "class" : "SnowBacklogMetricReader",
 *   "source" : "https://myinstance.service-now.com/",
 *   "product": "My Project Name",
 *   "authenticator": {
 *     "class" : "BasicAuthentication",
 *     "username" : "jsmith",
 *     "password" : "passwd",
 *     "preemptive" : true
 *   }
 * },
 * </pre>
 *
 * <p>The {@code product} configuration element is used to lookup the backlog items and to retrieve the releases and
 * sprints for that product to determine the current sprint and a time window for calculating "new" backlog items.</p>
 *
 * <p>The instance name for all the metrics is set to the name of the product by default, but can be overridden by
 * adding the {@code instance} configuration property with the name to be used for the metric instance.</p>
 */
public class SnowBacklogMetricReader extends SnowMetricReader implements FrameReader {
  private static final long TWO_WEEKS = 1000 * 60 * 60 * 24 * 14; // in milliseconds
  private static final String NEW_DEFECT_COUNT = "backlog_defect_new_count";
  private static final String ACTIVE_COUNT = "backlog_active_count";
  private static final String READY_COUNT = "backlog_ready_count";
  private static final String TOTAL_POINTS = "backlog_point_total";
  private static final String NEW_FEATURE_COUNT = "backlog_feature_new_count";
  private static final String NEW_ACTIVE_COUNT = "backlog_active_new_count";

  SnowDateTime sprintStart = null;
  private List<SnowStory> stories = null;
  private String instanceName = null;

  /**
   * @param context The transformation context in which this component should operate
   */
  @Override
  public void open(TransformContext context) {
    super.open(context);

    // we need the project name from the configuration
    String product = getConfiguration().getString(PRODUCT);
    if (StringUtil.isBlank(product)) {
      context.setError("The " + getClass().getSimpleName() + " configuration did not contain the '" + PRODUCT + "' element");
      context.setState("Configuration Error");
      return;
    }
    if (context.isNotInError()) {
      // the instance name is important grouping key for other metrics of this type
      instanceName = getConfiguration().getString(INSTANCE);
      if (StringUtil.isBlank(instanceName)) instanceName = product;

      // get all backlog items for the configured project
      filter = new SnowFilter("product.name", LIKE, product).and("active", IS, "true");

      if (StringUtil.isEmpty(getString(ConfigTag.SELECTOR))) {
        getConfiguration().set(ConfigTag.SELECTOR, "records.*");
      }

      getResource().getDefaultParameters().setMethod(Method.GET);
    }
  }

  /**
   * Read in all the data via the web service reader, and replace its "read-in" data with our own, the metrics.
   *
   * @param context the context containing data related to the current transaction.
   * @return the metrics generated from the stories read in from ServiceNow
   */
  @Override
  public DataFrame read(TransactionContext context) {
    if (stories == null) {
      List<SnowSprint> sprints = getSprints(getConfiguration().getString(PRODUCT));
      for (SnowSprint sprint : sprints) {
        if (sprint.isCurrent()) {
          Log.debug(sprint);
          Log.notice("Current sprint " + sprint.getShortDescription() + " (" + sprint.getScheduledStartDate() + " - " + sprint.getScheduledEndDate() + ")");
          sprintStart = sprint.getScheduledStartDate();
          break;
        }
      }

      if (sprintStart == null) sprintStart = new SnowDateTime(new Date(getContext().getStartTime() - TWO_WEEKS));

      // We need to set the request path to that of the rm_story table
      try {
        getResource().setPath("rm_story.do?JSONv2&displayvalue=all&&sysparam_limit=2000&sysparm_query=" + filter.toEncodedString());
      } catch (URISyntaxException e) {
        e.printStackTrace();
      }

      stories = new ArrayList<>();
      for (DataFrame frame = super.read(context); frame != null; frame = super.read(context)) {
        try {
          stories.add(new SnowStory(frame));
        } catch (SnowException e) {
          Log.error("Received invalid data at record #" + (stories.size() + 1) + ": " + e.getMessage(), e);
        }
      }
      Log.debug("...read in " + stories.size() + " stories.");

      // replace the dataframes with our metrics
      dataframes = generateMetrics(stories);
      context.setLastFrame(false);
    }
    return super.read(context); // return all the replaced dataframes
  }

  /**
   * Generate metrics from the given stories
   *
   * @param stories the stories read in from the backlog
   * @return a set of metrics describing the characteristics of the backlog stories
   */
  private List<DataFrame> generateMetrics(List<SnowStory> stories) {
    List<DataFrame> metrics = new ArrayList<>();
    metrics.addAll(generateActiveItemCount(stories));
    metrics.addAll(generateNewItemCounts(stories));
    metrics.addAll(generateStoryTypeCounts(stories));
    metrics.addAll(generatePointMetrics(stories));
    return metrics;
  }

  private List<DataFrame> generatePointMetrics(List<SnowStory> stories) {
    List<DataFrame> metrics = new ArrayList<>();
    int readyCount = 0;
    int totalPoints = 0;
    for (SnowStory story : stories) {
      int points = story.getPoints();
      if (points > 0) {
        readyCount++;
        totalPoints += points;
      }
    }

    DataFrame metric = new DataFrame();
    metric.set(ConfigTag.NAME, READY_COUNT);
    metric.set(ConfigTag.VALUE, readyCount);
    metric.set(ConfigTag.HELP, "The number of ready (pointed) active backlog items");
    metric.set(ConfigTag.TYPE, CMC.GAUGE);
    metric.set(INSTANCE, instanceName);
    metrics.add(metric);
    metric = new DataFrame();
    metric.set(ConfigTag.NAME, TOTAL_POINTS);
    metric.set(ConfigTag.VALUE, totalPoints);
    metric.set(ConfigTag.HELP, "The total number of points in active backlog items");
    metric.set(ConfigTag.TYPE, CMC.GAUGE);
    metric.set(INSTANCE, instanceName);
    metrics.add(metric);


    return metrics;

  }

  private List<DataFrame> generateActiveItemCount(List<SnowStory> stories) {
    List<DataFrame> metrics = new ArrayList<>();
    int activeCount = 0;
    for (SnowStory story : stories) {
      if (story.isActive()) activeCount++;
    }

    DataFrame metric = new DataFrame();
    metric.set(ConfigTag.NAME, ACTIVE_COUNT);
    metric.set(ConfigTag.VALUE, activeCount);
    metric.set(ConfigTag.HELP, "The number of active backlog items");
    metric.set(ConfigTag.TYPE, CMC.GAUGE);
    metric.set(INSTANCE, instanceName);
    metrics.add(metric);

    return metrics;
  }

  private List<DataFrame> generateNewItemCounts(List<SnowStory> stories) {
    List<DataFrame> metrics = new ArrayList<>();
    int newDefectCount = 0;
    int newFeatureCount = 0;
    int activeCount = 0;

    for (SnowStory story : stories) {
      if (story.isActive() && story.getCreatedTimestamp().compareTo(sprintStart) > 0) {
        activeCount++;
        if (story.isDefect()) {
          newDefectCount++;
        } else if (story.isFeature()) {
          newFeatureCount++;
        }
      }
    }

    DataFrame metric = new DataFrame();
    metric.set(ConfigTag.NAME, NEW_DEFECT_COUNT);
    metric.set(ConfigTag.VALUE, newDefectCount);
    metric.set(ConfigTag.HELP, "The number of new active defect backlog items");
    metric.set(ConfigTag.TYPE, CMC.GAUGE);
    metric.set(INSTANCE, instanceName);
    metrics.add(metric);

    metric = new DataFrame();
    metric.set(ConfigTag.NAME, NEW_FEATURE_COUNT);
    metric.set(ConfigTag.VALUE, newFeatureCount);
    metric.set(ConfigTag.HELP, "The number of new active feature backlog items");
    metric.set(ConfigTag.TYPE, CMC.GAUGE);
    metric.set(INSTANCE, instanceName);
    metrics.add(metric);

    metric = new DataFrame();
    metric.set(ConfigTag.NAME, NEW_ACTIVE_COUNT);
    metric.set(ConfigTag.VALUE, activeCount);
    metric.set(ConfigTag.HELP, "The number of new active backlog items");
    metric.set(ConfigTag.TYPE, CMC.GAUGE);
    metric.set(INSTANCE, instanceName);
    metrics.add(metric);

    return metrics;
  }

  /**
   * Generate counts for each of the classification types (e.g., feature, defect, etc.) for active stories.
   *
   * @param stories the SnowStories representing the backlog to analyze
   * @return a set of metrics relating to the classification of active stories
   */
  private List<DataFrame> generateStoryTypeCounts(List<SnowStory> stories) {
    List<DataFrame> metrics = new ArrayList<>();
    Map<String, Integer> counts = new HashMap<>();
    for (SnowStory story : stories) {
      if (story.isActive()) {
        String storyType = story.getType().toLowerCase();
        if (StringUtil.isNotBlank(storyType)) {
          storyType = storyType.replace(' ', '_');
          if (counts.get(storyType) != null) {
            counts.put(storyType, counts.get(storyType) + 1);
          } else {
            counts.put(storyType, 1);
          }
        }
      }
    }
    for (Map.Entry<String, Integer> entry : counts.entrySet()) {
      DataFrame metric = new DataFrame();
      metric.set(ConfigTag.NAME, "backlog_" + entry.getKey() + "_count");
      metric.set(ConfigTag.VALUE, entry.getValue());
      metric.set(ConfigTag.HELP, "The number of active backlog items with the classification of '" + entry.getKey() + "'");
      metric.set(ConfigTag.TYPE, CMC.GAUGE);
      metric.set(INSTANCE, instanceName);
      metrics.add(metric);
    }
    return metrics;
  }

}
