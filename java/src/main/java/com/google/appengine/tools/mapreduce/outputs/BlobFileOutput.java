// Copyright 2012 Google Inc. All Rights Reserved.

package com.google.appengine.tools.mapreduce.outputs;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.appengine.api.files.AppEngineFile;
import com.google.appengine.tools.mapreduce.Output;
import com.google.appengine.tools.mapreduce.OutputWriter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;

/**
 * An {@link Output} that writes bytes to a set of blob files, one per shard.
 *
 * @author ohler@google.com (Christian Ohler)
 */
@SuppressWarnings("deprecation")
public final class BlobFileOutput extends Output<ByteBuffer, List<AppEngineFile>> {

  private static final long serialVersionUID = 868276534742230776L;

  private final String mimeType;
  private final String fileNamePattern;

  public BlobFileOutput(String fileNamePattern, String mimeType) {
    this.mimeType = checkNotNull(mimeType, "Null mimeType");
    this.fileNamePattern = checkNotNull(fileNamePattern, "Null fileNamePattern");
  }

  @Override
  public List<BlobFileOutputWriter> createWriters(int numShards) {
    ImmutableList.Builder<BlobFileOutputWriter> out = ImmutableList.builder();
    for (int i = 0; i < numShards; i++) {
      out.add(new BlobFileOutputWriter(String.format(fileNamePattern, i), mimeType));
    }
    return out.build();
  }

  /**
   * Returns a list of AppEngineFiles that has one element for each reduce
   * shard.  Each element is either an {@code AppEngineFile} or null (if that
   * reduce shard emitted no data).
   */
  @Override
  public List<AppEngineFile> finish(Collection<? extends OutputWriter<ByteBuffer>> writers) {
    List<AppEngineFile> out = Lists.newArrayList();
    for (OutputWriter<ByteBuffer> w : writers) {
      BlobFileOutputWriter writer = (BlobFileOutputWriter) w;
      out.add(writer.getFile());
    }
    return out;
  }
}
