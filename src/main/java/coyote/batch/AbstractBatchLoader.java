/*
 * Copyright (c) 2016 Stephan D. Cote' - All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the MIT License which accompanies this distribution, and is 
 * available at http://creativecommons.org/licenses/MIT/
 *
 * Contributors:
 *   Stephan D. Cote 
 *      - Initial concept and initial implementation
 */
package coyote.batch;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import coyote.commons.FileUtil;
import coyote.commons.StringUtil;
import coyote.commons.UriUtil;
import coyote.loader.AbstractLoader;
import coyote.loader.log.Log;
import coyote.loader.log.LogMsg;


/**
 * Base class for the Job and Service loaders which handle one or multiple jobs 
 * respectively.
 */
public abstract class AbstractBatchLoader extends AbstractLoader {

  /**
   * If there is no specified directory in the HOMDIR system property, just use the current working directory
   */
  public static final String DEFAULT_HOME = new String( System.getProperty( "user.dir" ) );




  /**
   * Determine the value of the "app.home" system property.
   * 
   * <p>If the app home property is already set, it is preserved, if not 
   * normalized. If there is no value, this attempts to determine the location 
   * of the configuration file used to configure this job and if found, uses 
   * that directory as the home directory of all transformation operations. The
   * reasoning is that all artifacts should be kept together. Also, it is 
   * probable that the batch job will be called from a central location while 
   * each batch job will live is its own project directory.</p>
   * 
   * <p>The most common use case is for the batch job to be called from a 
   * scheduler (e.g. cron) with an absolute path to a configuration file. 
   * Another very probable use case is the batch job being called from a 
   * project directory with one configuration file per directory.</p>
   * 
   * <p>It is possible that multiple files with different configurations will 
   * exist in one directory
   */
  protected void determineHomeDirectory() {
    // If our home directory is not specified as a system property...
    if ( System.getProperty( Job.APP_HOME ) == null ) {

      // use the first argument to the bootstrap loader to determine the 
      // location of our configuration file
      File cfgFile = new File( super.getCommandLineArguments()[0] );

      // If that file exists, then use that files parent directory as our work
      // directory
      if ( cfgFile.exists() ) {
        System.setProperty( Job.APP_HOME, cfgFile.getParentFile().getAbsolutePath() );
      } else {
        // we could not determine the path to the configuration file, use the 
        // current working directory
        System.setProperty( Job.APP_HOME, DEFAULT_HOME );
      }
    } else {

      // Normalize the "." that sometimes is set in the app.home property
      if ( System.getProperty( Job.APP_HOME ).trim().equals( "." ) ) {
        System.setProperty( Job.APP_HOME, DEFAULT_HOME );
      } else if ( System.getProperty( Job.APP_HOME ).trim().length() == 0 ) {
        // catch empty home property and just use the home directory
        System.setProperty( Job.APP_HOME, DEFAULT_HOME );
      }
    }

    // Remove all the relations and extra slashes from the home path
    System.setProperty( Job.APP_HOME, FileUtil.normalizePath( System.getProperty( Job.APP_HOME ) ) );
    Log.debug( LogMsg.createMsg( Batch.MSG, "Job.home_dir_set", System.getProperty( Job.APP_HOME ) ) );
  }




  protected void determineWorkDirectory() {

    File result = null;

    // First check for the app.work system property
    String path = System.getProperties().getProperty( Job.APP_WORK );

    if ( StringUtil.isNotBlank( path ) ) {
      String workDir = FileUtil.normalizePath( path );
      File workingDir = new File( workDir );
      if ( workingDir.exists() ) {
        if ( workingDir.isDirectory() ) {
          if ( workingDir.canWrite() ) {
            result = workingDir;
          } else {
            Log.warn( "The app.work property specified an un-writable (permissions) directory: " + workDir );
          }
        } else {
          Log.warn( "The app.work property does not specify a directory: " + workDir );
        }
      } else {
        // Try making it
        try {
          FileUtil.makeDirectory( workingDir );
          result = workingDir;
        } catch ( IOException e ) {
          Log.error( "Could not create working directory specified in app.work property: " + workDir + " - " + e.getMessage() );
        }
      }

    } else {
      // No app.work defined, so try to locate the configuration file used to 
      // create this job
      Log.debug( LogMsg.createMsg( Batch.MSG, "Job.no_work_dir_set", Job.APP_WORK, System.getProperty( coyote.loader.ConfigTag.CONFIG_URI ) ) );

      URI cfgUri = UriUtil.parse( System.getProperty( coyote.loader.ConfigTag.CONFIG_URI ) );
      if ( cfgUri != null && UriUtil.isFile( cfgUri ) ) {
        File cfgFile = UriUtil.getFile( cfgUri );
        if ( cfgFile != null ) {
          File workingDir = new File( cfgFile.getParent() );
          if ( workingDir.exists() && workingDir.isDirectory() && workingDir.canWrite() ) {
            result = workingDir;
          }
        }
      }
    }

    // If we have a result,
    if ( result != null ) {
      // set it as our working directory
      System.setProperty( Job.APP_WORK, result.getAbsolutePath() );
    } else {
      // else just use the current working directory
      System.setProperty( Job.APP_WORK, DEFAULT_HOME );
    }

    // Remove all the relations and extra slashes from the home path
    System.setProperty( Job.APP_WORK, FileUtil.normalizePath( System.getProperty( Job.APP_WORK ) ) );
    Log.debug( LogMsg.createMsg( Batch.MSG, "Job.work_dir_set", System.getProperty( Job.APP_WORK ) ) );
  }

}