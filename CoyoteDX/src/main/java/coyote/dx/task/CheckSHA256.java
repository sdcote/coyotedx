/*
 * Copyright (c) 2017 Stephan D. Cote' - All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the MIT License which accompanies this distribution, and is
 * available at http://creativecommons.org/licenses/MIT/
 *
 * Contributors:
 *   Stephan D. Cote
 *      - Initial concept and implementation
 */
package coyote.dx.task;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


/**
 * Perform a SHA-256 checksum on the given file and compare it with the checksum
 * in a file with the same name with a ".sha256" suffix.
 *
 * <p>If the checksum file does not exist, it will be created.
 *
 * <p>The checksum will be posted in the transform context with the name of
 * the checksum file so other components and tasks can ensure the integrity of
 * the file throughout the job.
 */
public class CheckSHA256 extends AbstractDigestTask {

  /**
   * Utility method to get the digest for the given file.
   *
   * @param file file to check
   *
   * @return the digest of the file or "" if there were errors
   */
  public static String digest( final File file ) {
    try {
      return digest( file, MessageDigest.getInstance( "SHA-256" ) );
    } catch ( NoSuchAlgorithmException | IOException e ) {
      return "";
    }
  }




  public CheckSHA256() {
    CHECKSUM_EXTENSION = ".sha256";
    ALGORITHM = "SHA-256";
  }




  @Override
  MessageDigest getDigest() {
    try {
      return MessageDigest.getInstance( ALGORITHM );
    } catch ( final NoSuchAlgorithmException ignore ) {}
    return null;
  }

}
