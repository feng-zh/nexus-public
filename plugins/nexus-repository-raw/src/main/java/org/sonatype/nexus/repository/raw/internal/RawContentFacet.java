/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.repository.raw.internal;

import java.io.IOException;

import javax.annotation.Nullable;

import org.sonatype.nexus.repository.Facet;
import org.sonatype.nexus.repository.InvalidContentException;
import org.sonatype.nexus.repository.cache.CacheInfo;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;

/**
 * Provides persistent storage for {@link Content}.
 *
 * @since 3.0
 */
@Facet.Exposed
public interface RawContentFacet
    extends Facet
{
  @Nullable
  Content get(String path) throws IOException;

  Content put(String path, Payload content) throws IOException, InvalidContentException;

  boolean delete(String path) throws IOException;

  /**
   * Raw proxy facet specific method: invoked when cached content (returned by {@link #get(String)} method of this
   * same facet instance) is found to be up to date after remote checks. This method applies the passed in {@link
   * CacheInfo} to the {@link Content}'s underlying asset.
   */
  void setCacheInfo(String path, Content content, CacheInfo cacheInfo) throws IOException;
}
