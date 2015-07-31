/*
 * Copyright (c) 2015 Stephan D. Cote' - All rights reserved.
 * 
 * This program and the accompanying materials are made available under the 
 * terms of the MIT License which accompanies this distribution, and is 
 * available at http://creativecommons.org/licenses/MIT/
 *
 * Contributors:
 *   Stephan D. Cote 
 *      - Initial concept and initial implementation
 */
package coyote.batch.validate;

//import static org.junit.Assert.*;
import static org.junit.Assert.*;

import java.io.IOException;

import org.junit.Test;

import coyote.batch.AbstractTest;
import coyote.batch.ConfigurationException;
import coyote.batch.FrameValidator;
import coyote.batch.TransactionContext;
import coyote.batch.ValidationException;
import coyote.dataframe.DataFrame;


/**
 * 
 */
public class NotEmptyTest extends AbstractTest {

  @Test
  public void test() {

    String cfgData = "{ \"field\" : \"model\",  \"desc\" : \"Model cannot be empty\" }";
    DataFrame configuration = parseConfiguration( cfgData );

    // Create a transaction context
    TransactionContext context = createTransactionContext();

    // Populate it with test data
    DataFrame sourceFrame = new DataFrame();
    sourceFrame.put( "model", "PT4500" );
    context.setSourceFrame( sourceFrame );

    try (FrameValidator validator = new NotEmpty()) {
      // Configure the validator
      validator.setConfiguration( configuration );
      // open (initialize) the component 
      validator.open( getTransformContext() );

      boolean result = validator.process( context );
      assertTrue( result );

      // Create a new test frame and place it in the context for validation
      sourceFrame = new DataFrame();
      sourceFrame.put( "model", " " );
      context.setSourceFrame( sourceFrame );

      result = validator.process( context );
      assertFalse( result );

    } catch ( ConfigurationException | ValidationException | IOException e ) {
      e.printStackTrace();
      fail( e.getMessage() );
    }

  }

}