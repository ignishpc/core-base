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
package org.ignis.backend.cluster.tasks.executor;

import org.apache.thrift.TException;
import org.ignis.backend.cluster.IExecutor;
import org.ignis.backend.cluster.ITaskContext;
import org.ignis.backend.cluster.tasks.ICache;
import org.ignis.backend.exception.IExecutorExceptionWrapper;
import org.ignis.backend.exception.IgnisException;
import org.ignis.rpc.IExecutorException;
import org.slf4j.LoggerFactory;

/**
 * @author César Pomar
 */
public class ICacheTask extends IExecutorTask {

    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(IFlatmapTask.class);

    private final ICache cache;

    public ICacheTask(String name, IExecutor executor, ICache cache) {
        super(name, executor);
        this.cache = cache;
    }

    @Override
    public void run(ITaskContext context) throws IgnisException {
        LOGGER.info(log() + "updating cache from " + cache.getActualLevel() + " to " + cache.getNextLevel());
        try {
            if (cache.getActualLevel().equals(ICache.Level.NO_CACHE) &&
                    cache.getNextLevel().equals(ICache.Level.NO_CACHE)) {
                executor.getCacheContextModule().loadCache(cache.getId());
                executor.getCacheContextModule().cache(cache.getId(), (byte) ICache.Level.NO_CACHE.getInt());
            }
            executor.getCacheContextModule().cache(cache.getId(), (byte) cache.getNextLevel().getInt());
        } catch (IExecutorException ex) {
            throw new IExecutorExceptionWrapper(ex);
        } catch (TException ex) {
            throw new IgnisException(ex.getMessage(), ex);
        }
        LOGGER.info(log() + "Cache updated");
    }

}
