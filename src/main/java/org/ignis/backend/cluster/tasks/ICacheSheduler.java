/*
 * Copyright (C) 2018 
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.ignis.backend.cluster.tasks;

import java.util.Collections;
import java.util.List;
import org.apache.thrift.TException;
import org.ignis.backend.cluster.IExecutionContext;
import org.ignis.backend.exception.IgnisException;
import org.slf4j.LoggerFactory;

/**
 *
 * @author César Pomar
 */
public final class ICacheSheduler extends ITaskScheduler {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ICacheSheduler.class);

    private boolean executed;
    private boolean cached;
    private final List<ITask> cache;
    private final List<ITask> loadCache;
    private final List<ITask> uncache;
    private final ITaskScheduler dataScheduler;

    public ICacheSheduler(ILock lock,List<ITask> cache, List<ITask> loadCache, List<ITask> uncache, ITaskScheduler dataScheduler) {
        super(Collections.emptyList(), Collections.singletonList(lock), Collections.singletonList(dataScheduler));
        this.loadCache = loadCache;
        this.cache = cache;
        this.uncache = uncache;
        this.dataScheduler = dataScheduler;
    }

    public void cache() {
        cached = true;
    }

    public void uncache() {
        if (isCache()) {
            if (executed) {
                for (ITask task : uncache) {
                    try {
                        task.execute(new IExecutionContext());
                    } catch (TException ex) {
                        LOGGER.warn("Exception while uncache " + ex);
                    }
                }
            }
            cached = false;
            executed = false;
        }
    }

    public boolean isCache() {
        return cached;
    }

    public ITaskScheduler getDataScheduler() {
        return dataScheduler;
    }

    @Override
    protected void execute(IThreadPool pool, IExecutionContext context) throws IgnisException {
        if (isCache()) {
            ITaskScheduler sheduler;
            if (executed) {
                sheduler = new ITaskScheduler(loadCache, Collections.emptyList(), Collections.emptyList());
            } else {
                super.execute(pool, context);
                sheduler = new ITaskScheduler(cache, Collections.emptyList(), Collections.emptyList());
            }
            sheduler.execute(pool, context);
        } else {
            super.execute(pool, context);
        }
        executed = true;
    }

}
