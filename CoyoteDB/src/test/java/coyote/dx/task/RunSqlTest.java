/*
 * Copyright (c) 2017 Stephan D. Cote' - All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the MIT License which accompanies this distribution, and is 
 * available at http://creativecommons.org/licenses/MIT/
 */
package coyote.dx.task;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.File;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import coyote.TestingLoader;
import coyote.dataframe.DataFrame;
import coyote.dx.ConfigTag;
import coyote.dx.TransformEngine;
import coyote.dx.context.TransformContext;
import coyote.loader.cfg.Config;
import coyote.loader.cfg.ConfigurationException;
import coyote.loader.log.ConsoleAppender;
import coyote.loader.log.Log;


/**
 * 
 */
public class RunSqlTest {
  private static final String CATALOG = "runsql";
  //private static final String JDBC_DRIVER = "org.h2.Driver";
  private static final String JDBC_SOURCE = "org.h2.jdbcx.JdbcDataSource";
  private static final String DB_URL = "jdbc:h2:./" + CATALOG;
  private static final String USER = "username";
  private static final String PASS = "password";
  private static final String SCHEMA = "test";
  private static final String TABLE = "testdata";




  /**
   * @throws java.lang.Exception
   */
  @BeforeClass
  public static void setUpBeforeClass() throws Exception {
    Log.addLogger(Log.DEFAULT_LOGGER_NAME, new ConsoleAppender(Log.TRACE_EVENTS | Log.DEBUG_EVENTS | Log.INFO_EVENTS | Log.NOTICE_EVENTS | Log.WARN_EVENTS | Log.ERROR_EVENTS | Log.FATAL_EVENTS));
    //Log.addLogger(Log.DEFAULT_LOGGER_NAME, new ConsoleAppender(Log.INFO_EVENTS | Log.WARN_EVENTS | Log.ERROR_EVENTS | Log.FATAL_EVENTS));
  }




  /**
   * @throws Exception
   */
  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    File dbfile = new File(CATALOG + ".mv.db");
    Log.debug(dbfile.getAbsolutePath());
    dbfile.delete();
    // delete the trace file if it exists - it's text file showing what commands were processed
    dbfile = new File(CATALOG + ".trace.db");
    if (dbfile.exists()) {
      Log.debug(dbfile.getAbsolutePath());
      dbfile.delete();
    }
  }




  @Test
  public void test() throws ConfigurationException {
    // Create a Job
    DataFrame jobFrame = new DataFrame().set(ConfigTag.NAME, "test") //
        .set(ConfigTag.TASK, // 
            new DataFrame().set("RunSql",
                new DataFrame() // 
                    .set(ConfigTag.SOURCE, "src/test/resources/RunSqlTest1.sql") //
                    .set(ConfigTag.TARGET, DB_URL) //
                    .set(ConfigTag.DRIVER, JDBC_SOURCE) //
                    .set(ConfigTag.USERNAME, USER) //
                    .set(ConfigTag.PASSWORD, PASS) //
                    .set(ConfigTag.SCHEMA, SCHEMA) //
                    .set(ConfigTag.TABLE, TABLE) //
                    .set(ConfigTag.AUTO_CREATE, true) //
            ) //
    );

    Config configuration = new Config();
    configuration.add(ConfigTag.JOB, jobFrame);
    //System.out.println(configuration.toFormattedString());

    TestingLoader loader = new TestingLoader();
    loader.configure(configuration);
    loader.start(); // run the job

    // Check for results
    TransformEngine engine = loader.getEngine();
    TransformContext context = engine.getContext();
    assertNotNull(context);
    assertFalse(context.isInError());
  }

}
