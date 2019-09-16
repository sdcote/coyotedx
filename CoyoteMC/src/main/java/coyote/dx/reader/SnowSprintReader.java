package coyote.dx.reader;

import coyote.commons.network.http.Method;
import coyote.dataframe.DataFrame;
import coyote.dx.FrameReader;
import coyote.dx.context.TransactionContext;
import coyote.dx.context.TransformContext;
import coyote.loader.log.Log;
import coyote.mc.snow.SnowException;
import coyote.mc.snow.SnowFilter;
import coyote.mc.snow.SnowSprint;

import java.net.URISyntaxException;

import static coyote.mc.snow.Predicate.IS;

/**
 * This is a reader which connects to a ServiceNow instance and queries data via URL export to retrieve sprints for a
 * project.
 *
 * <p>The primary use case to date is to create a local cache of sprint records which other components can read as part
 * of a pre-processing task to set the start and end dates for the current and previous sprints.</p>
 */
public class SnowSprintReader extends SnowReader implements FrameReader {


  private static final String RELEASE = "release";

  /**
   * @param context The transformation context in which this component should operate
   */
  @Override
  public void open(TransformContext context) {
    super.open(context);
    getResource().getDefaultParameters().setMethod(Method.GET);
    String release = getConfiguration().getString(RELEASE);
    SnowFilter filter = new SnowFilter("release.number", IS, release);
    try {
      getResource().setPath("rm_sprint.do?JSONv2&sysparm_query=" + filter.toEncodedString());
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
    Log.info("Connecting to " + getResource().getFullURI());
  }


  @Override
  public DataFrame read(TransactionContext context) {
    DataFrame retval = super.read(context);
    if (retval != null) {
      try {
        SnowSprint sprint = new SnowSprint(retval);
        if (sprint.isCurrent()) Log.info("Sprint: " + sprint.getShortDescription()+ " ("+sprint.getNumber()+") is the current sprint");
      } catch (SnowException e) {
        Log.error("Could not parse retrieved data into a SnowSprint: " + e.getLocalizedMessage());
      }
    }
    return retval;
  }

}
