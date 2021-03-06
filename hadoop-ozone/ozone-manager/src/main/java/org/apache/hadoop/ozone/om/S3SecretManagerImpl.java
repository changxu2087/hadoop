/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.apache.hadoop.ozone.om;

import com.google.common.base.Preconditions;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.hadoop.hdds.conf.OzoneConfiguration;
import org.apache.hadoop.ozone.OmUtils;
import org.apache.hadoop.ozone.om.helpers.S3SecretValue;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * S3 Secret manager.
 */
public class S3SecretManagerImpl implements S3SecretManager {
  private static final Logger LOG =
      LoggerFactory.getLogger(S3SecretManagerImpl.class);

  /**
   * OMMetadataManager is used for accessing OM MetadataDB and ReadWriteLock.
   */
  private final OMMetadataManager omMetadataManager;
  private final OzoneConfiguration configuration;

  /**
   * Constructs S3SecretManager.
   *
   * @param omMetadataManager
   */
  public S3SecretManagerImpl(OzoneConfiguration configuration,
      OMMetadataManager omMetadataManager) {
    this.configuration = configuration;
    this.omMetadataManager = omMetadataManager;
  }

  @Override
  public S3SecretValue getS3Secret(String kerberosID) throws IOException {
    Preconditions.checkArgument(Strings.isNotBlank(kerberosID),
        "kerberosID cannot be null or empty.");
    byte[] awsAccessKey = OmUtils.getMD5Digest(kerberosID);
    S3SecretValue result = null;
    omMetadataManager.getLock().acquireS3SecretLock(kerberosID);
    try {
      byte[] s3Secret =
          omMetadataManager.getS3SecretTable().get(awsAccessKey);
      if(s3Secret == null) {
        byte[] secret = OmUtils.getSHADigest();
        result = new S3SecretValue(kerberosID, DigestUtils.sha256Hex(secret));
        omMetadataManager.getS3SecretTable()
            .put(awsAccessKey, result.getProtobuf().toByteArray());
      } else {
        result = S3SecretValue.fromProtobuf(
            OzoneManagerProtocolProtos.S3Secret.parseFrom(s3Secret));
      }
      result.setAwsAccessKey(DigestUtils.md5Hex(awsAccessKey));
    } finally {
      omMetadataManager.getLock().releaseS3SecretLock(kerberosID);
    }
    return result;
  }
}
