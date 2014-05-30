package com.google.appengine.tools.mapreduce.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.appengine.tools.mapreduce.Counters;
import com.google.appengine.tools.mapreduce.Output;
import com.google.appengine.tools.mapreduce.OutputWriter;
import com.google.appengine.tools.mapreduce.WorkerContext;
import com.google.appengine.tools.mapreduce.impl.pipeline.ResultAndStatus;
import com.google.appengine.tools.mapreduce.impl.shardedjob.ShardedJobController;
import com.google.appengine.tools.mapreduce.impl.shardedjob.Status;
import com.google.appengine.tools.pipeline.NoSuchObjectException;
import com.google.appengine.tools.pipeline.OrphanedObjectException;
import com.google.appengine.tools.pipeline.PipelineServiceFactory;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

public class WorkerController<I, O, R, C extends WorkerContext<O>> extends
    ShardedJobController<WorkerShardTask<I, O, C>> {

  private static final long serialVersionUID = 931651840864967980L;
  private static final Logger log = Logger.getLogger(WorkerController.class.getName());

  private final String mrJobId;
  private final Counters counters;
  private final Output<O, R> output;
  private final String resultPromiseHandle;

  public WorkerController(String mrJobId, String shardedJobName, Counters initialCounters,
      Output<O, R> output, String resultPromiseHandle) {
    super(shardedJobName);
    this.mrJobId = checkNotNull(mrJobId, "Null jobId");
    this.counters = checkNotNull(initialCounters, "Null counters");
    this.output = checkNotNull(output, "Null output");
    this.resultPromiseHandle = checkNotNull(resultPromiseHandle, "Null resultPromiseHandle");
  }

  @Override
  public void completed(List<? extends WorkerShardTask<I, O, C>> workers) {
    ImmutableList.Builder<OutputWriter<O>> outputWriters = ImmutableList.builder();
    for (WorkerShardTask<I, O, C> worker : workers) {
      outputWriters.add(worker.getOutputWriter());
    }
    output.setContext(new BaseContext(mrJobId));
    R outputResult;
    try {
      outputResult = output.finish(outputWriters.build());
    } catch (IOException e) {
      throw new RuntimeException(output + ".finish() threw IOException");
    }
    for (WorkerShardTask<I, O, C> worker : workers) {
      counters.addAll(worker.getContext().getCounters());
    }
    Status status = new Status(Status.StatusCode.DONE);
    ResultAndStatus<R> resultAndStatus = new ResultAndStatus<>(
        new MapReduceResultImpl<>(outputResult, counters), status);
    submitPromisedJob(resultAndStatus);
  }

  @Override
  public void failed(Status status) {
    submitPromisedJob(new ResultAndStatus<R>(null, status));
  }

  // TODO(user): consider using a pipeline for it after b/12067201 is fixed.
  private void submitPromisedJob(final ResultAndStatus<R> resultAndStatus) {
    try {
      PipelineServiceFactory.newPipelineService().submitPromisedValue(resultPromiseHandle,
          resultAndStatus);
    } catch (OrphanedObjectException e) {
      log.warning("Discarding an orphaned promiseHandle: " + resultPromiseHandle);
    } catch (NoSuchObjectException e) {
      // Let taskqueue retry.
      throw new RuntimeException(resultPromiseHandle + ": Handle not found, can't submit "
          + resultAndStatus + " going to retry.", e);
    }
  }
}