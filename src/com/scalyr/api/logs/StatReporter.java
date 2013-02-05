/*
 * Scalyr client library
 * Copyright 2012 Scalyr, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.scalyr.api.logs;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;

import com.scalyr.api.internal.ScalyrUtil;

public class StatReporter {
  private static final long processStartTime = ScalyrUtil.currentTimeMillis();
  
  private static boolean registeredGeneralStats = false;
  private static boolean registeredMemoryStats = false;
  
  /**
   * Create all gauges implemented by this class.
   */
  public static void registerAll() {
    registerGeneralStats();
    registerMemoryStats();
  }
  
  /**
   * Create a set of Gauges to report general server statistics.
   */
  public static synchronized void registerGeneralStats() {
    if (registeredGeneralStats)
      return;
    
    registeredGeneralStats = true;
    
    Gauge.register(new Gauge(){@Override public Object sample() {
      return ScalyrUtil.currentTimeMillis() - processStartTime;
    }}, attributesWithTag("jvm.uptimeMs"));
  }

  /**
   * Create a set of Gauges to report JVM heap and garbage collection statistics at regular intervals.
   */
  public static synchronized void registerMemoryStats() {
    if (registeredMemoryStats)
      return;
    
    registeredMemoryStats = true;
    
    // Report used and free space for heap and non-heap memory.
    final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    final MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
    final MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
    
    Gauge.register(new Gauge(){@Override public Object sample() {
      return heapUsage.getUsed();
    }}, attributesWithTag("jvm.heap.used"));
    
    Gauge.register(new Gauge(){@Override public Object sample() {
      return heapUsage.getMax() - heapUsage.getUsed();
    }}, attributesWithTag("jvm.heap.free"));
    
    Gauge.register(new Gauge(){@Override public Object sample() {
      return nonHeapUsage.getUsed();
    }}, attributesWithTag("jvm.nonHeap.used"));
    
    Gauge.register(new Gauge(){@Override public Object sample() {
      return nonHeapUsage.getMax() - nonHeapUsage.getUsed();
    }}, attributesWithTag("jvm.nonHeap.free"));
    
    // Report used and free space for each memory pool
    for (MemoryPoolMXBean memPool : ManagementFactory.getMemoryPoolMXBeans()) {
      final MemoryPoolMXBean memPool_ = memPool;
      String tag = "jvm.pool." + memPool.getName().toLowerCase().replace(' ', '_');
      
      Gauge.register(new Gauge(){@Override public Object sample() {
        return memPool_.getUsage().getUsed();
      }}, attributesWithTag(tag + ".used"));
      
      Gauge.register(new Gauge(){@Override public Object sample() {
        return memPool_.getUsage().getMax() - memPool_.getUsage().getUsed();
      }}, attributesWithTag(tag + ".free"));
    }
    
    // Report invocation count and cumulative execution time for each garbage collector
    for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
      final GarbageCollectorMXBean collector_ = collector;
      String tag = "jvm.collector." + collector.getName().toLowerCase().replace(' ', '_');
      
      Gauge.register(new Gauge(){@Override public Object sample() {
        return collector_.getCollectionCount();
      }}, attributesWithTag(tag + ".count"));
      
      Gauge.register(new Gauge(){@Override public Object sample() {
        return collector_.getCollectionTime();
      }}, attributesWithTag(tag + ".timeMs"));
    }
  }
  
  private static EventAttributes attributesWithTag(String tag) {
    return new EventAttributes("source", "tsdb", "tag", tag);
  }
}