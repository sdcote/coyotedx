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

import java.io.IOException;

import coyote.batch.AbstractConfigurableComponent;
import coyote.batch.ConfigurableComponent;
import coyote.batch.FrameValidator;
import coyote.batch.TransformContext;


/**
 * 
 */
public abstract class AbstractValidator extends AbstractConfigurableComponent implements FrameValidator, ConfigurableComponent {

  @Override
  public void open( TransformContext context ) {

  }




  @Override
  public void close() throws IOException {

  }

}
