/*
 * Druid - a distributed column store.
 * Copyright (C) 2012  Metamarkets Group Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package com.metamx.druid.merger.coordinator;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.metamx.common.logger.Logger;
import com.metamx.druid.merger.common.TaskStatus;
import com.metamx.druid.merger.common.actions.TaskAction;
import com.metamx.druid.merger.common.TaskLock;
import com.metamx.druid.merger.common.task.Task;

import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implements an in-heap TaskStorage facility, with no persistence across restarts. This class is not
 * thread safe.
 */
public class HeapMemoryTaskStorage implements TaskStorage
{
  private final ReentrantLock giant = new ReentrantLock();
  private final Map<String, TaskStuff> tasks = Maps.newHashMap();
  private final Multimap<String, TaskLock> taskLocks = HashMultimap.create();
  private final Multimap<String, TaskAction> taskActions = ArrayListMultimap.create();

  private static final Logger log = new Logger(HeapMemoryTaskStorage.class);

  @Override
  public void insert(Task task, TaskStatus status)
  {
    giant.lock();

    try {
      Preconditions.checkNotNull(task, "task");
      Preconditions.checkNotNull(status, "status");
      Preconditions.checkArgument(
          task.getId().equals(status.getId()),
          "Task/Status ID mismatch[%s/%s]",
          task.getId(),
          status.getId()
      );

      if(tasks.containsKey(task.getId())) {
        throw new TaskExistsException(task.getId());
      }

      log.info("Inserting task %s with status: %s", task.getId(), status);
      tasks.put(task.getId(), new TaskStuff(task, status));
    } finally {
      giant.unlock();
    }
  }

  @Override
  public Optional<Task> getTask(String taskid)
  {
    giant.lock();

    try {
      Preconditions.checkNotNull(taskid, "taskid");
      if(tasks.containsKey(taskid)) {
        return Optional.of(tasks.get(taskid).getTask());
      } else {
        return Optional.absent();
      }
    } finally {
      giant.unlock();
    }
  }

  @Override
  public void setStatus(TaskStatus status)
  {
    giant.lock();

    try {
      Preconditions.checkNotNull(status, "status");

      final String taskid = status.getId();
      Preconditions.checkState(tasks.containsKey(taskid), "Task ID must already be present: %s", taskid);
      Preconditions.checkState(tasks.get(taskid).getStatus().isRunnable(), "Task status must be runnable: %s", taskid);
      log.info("Updating task %s to status: %s", taskid, status);
      tasks.put(taskid, tasks.get(taskid).withStatus(status));
    } finally {
      giant.unlock();
    }
  }

  @Override
  public Optional<TaskStatus> getStatus(String taskid)
  {
    giant.lock();

    try {
      Preconditions.checkNotNull(taskid, "taskid");
      if(tasks.containsKey(taskid)) {
        return Optional.of(tasks.get(taskid).getStatus());
      } else {
        return Optional.absent();
      }
    } finally {
      giant.unlock();
    }
  }

  @Override
  public List<String> getRunningTaskIds()
  {
    giant.lock();

    try {
      final ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
      for(final TaskStuff taskStuff : tasks.values()) {
        if(taskStuff.getStatus().isRunnable()) {
          listBuilder.add(taskStuff.getTask().getId());
        }
      }

      return listBuilder.build();
    } finally {
      giant.unlock();
    }
  }

  @Override
  public void addLock(final String taskid, final TaskLock taskLock)
  {
    giant.lock();

    try {
      Preconditions.checkNotNull(taskLock, "taskLock");
      taskLocks.put(taskid, taskLock);
    } finally {
      giant.unlock();
    }
  }

  @Override
  public void removeLock(final String taskid, final TaskLock taskLock)
  {
    giant.lock();

    try {
      Preconditions.checkNotNull(taskLock, "taskLock");
      taskLocks.remove(taskid, taskLock);
    } finally {
      giant.unlock();
    }
  }

  @Override
  public List<TaskLock> getLocks(final String taskid)
  {
    giant.lock();

    try {
      return ImmutableList.copyOf(taskLocks.get(taskid));
    } finally {
      giant.unlock();
    }
  }

  @Override
  public <T> void addAuditLog(Task task, TaskAction<T> taskAction)
  {
    giant.lock();

    try {
      taskActions.put(task.getId(), taskAction);
    } finally {
      giant.unlock();
    }
  }

  @Override
  public List<TaskAction> getAuditLogs(String taskid)
  {
    giant.lock();

    try {
      return ImmutableList.copyOf(taskActions.get(taskid));
    } finally {
      giant.unlock();
    }
  }

  private static class TaskStuff
  {
    final Task task;
    final TaskStatus status;

    private TaskStuff(Task task, TaskStatus status)
    {
      Preconditions.checkNotNull(task);
      Preconditions.checkNotNull(status);
      Preconditions.checkArgument(task.getId().equals(status.getId()));

      this.task = task;
      this.status = status;
    }

    public Task getTask()
    {
      return task;
    }

    public TaskStatus getStatus()
    {
      return status;
    }

    private TaskStuff withStatus(TaskStatus _status)
    {
      return new TaskStuff(task, _status);
    }
  }
}
