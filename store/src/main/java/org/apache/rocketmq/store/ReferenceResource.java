/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.store;

import java.util.concurrent.atomic.AtomicLong;

public abstract class ReferenceResource {
    // 文件缓冲区引用数量，默认1
    protected final AtomicLong refCount = new AtomicLong(1);
    // 存活状态，资源处于非存活状态 - 不可用
    protected volatile boolean available = true;
    // 执行完子类的cleanUp() 资源完全释放
    protected volatile boolean cleanupOver = false;
    // 第一次shutdown时间 第一次关闭资源可能失败，外部程序可能还依耐当前资源（refCount>0,此时记录初次关闭资源的时间）
    // 之后 再次关闭该资源的时候，会传递一个interval 参数，如果系统当前时间-firstShutdownTimestamp》interval 强制关闭
    private volatile long firstShutdownTimestamp = 0;


    public synchronized boolean hold() {
        if (this.isAvailable()) {
            if (this.refCount.getAndIncrement() > 0) {
                return true;
            } else {
                this.refCount.getAndDecrement();
            }
        }

        return false;
    }

    public boolean isAvailable() {
        return this.available;
    }

    public void shutdown(final long intervalForcibly) {
        if (this.available) {
            this.available = false;
            // 初次关闭时间
            this.firstShutdownTimestamp = System.currentTimeMillis();
            // 引用计数-1
            this.release();
        } else if (this.getRefCount() > 0) {
            if ((System.currentTimeMillis() - this.firstShutdownTimestamp) >= intervalForcibly) {
                // 强制关闭
                this.refCount.set(-1000 - this.getRefCount());
                this.release();
            }
        }
    }

    public void release() {
        // -1
        long value = this.refCount.decrementAndGet();
        //
        if (value > 0)
            return;

        // 当前资源无其他程序依赖，可以释放
        synchronized (this) {

            this.cleanupOver = this.cleanup(value);
        }
    }

    public long getRefCount() {
        return this.refCount.get();
    }

    public abstract boolean cleanup(final long currentRef);

    public boolean isCleanupOver() {
        return this.refCount.get() <= 0 && this.cleanupOver;
    }
}
