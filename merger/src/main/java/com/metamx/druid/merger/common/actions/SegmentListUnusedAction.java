package com.metamx.druid.merger.common.actions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.metamx.druid.client.DataSegment;
import com.metamx.druid.merger.common.task.Task;
import org.joda.time.Interval;

import java.io.IOException;
import java.util.List;

public class SegmentListUnusedAction implements TaskAction<List<DataSegment>>
{
  private final String dataSource;
  private final Interval interval;

  @JsonCreator
  public SegmentListUnusedAction(
      @JsonProperty("dataSource") String dataSource,
      @JsonProperty("interval") Interval interval
  )
  {
    this.dataSource = dataSource;
    this.interval = interval;
  }

  @JsonProperty
  public String getDataSource()
  {
    return dataSource;
  }

  @JsonProperty
  public Interval getInterval()
  {
    return interval;
  }

  public TypeReference<List<DataSegment>> getReturnTypeReference()
  {
    return new TypeReference<List<DataSegment>>() {};
  }

  @Override
  public List<DataSegment> perform(Task task, TaskActionToolbox toolbox) throws IOException
  {
    return toolbox.getMergerDBCoordinator().getUnusedSegmentsForInterval(dataSource, interval);
  }

  @Override
  public String toString()
  {
    return "SegmentListUnusedAction{" +
           "dataSource='" + dataSource + '\'' +
           ", interval=" + interval +
           '}';
  }
}
