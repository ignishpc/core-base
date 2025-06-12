/*
 * Copyright (C) 2019 César Pomar
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
package org.ignis.scheduler;


import org.ignis.scheduler.model.IClusterInfo;
import org.ignis.scheduler.model.IClusterRequest;
import org.ignis.scheduler.model.IContainerInfo;
import org.ignis.scheduler.model.IJobInfo;

import java.util.List;
import java.util.Map;

/**
 * @author César Pomar
 */
public class Ancoris implements IScheduler {

    public Ancoris(String url) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String createJob(String name, IClusterRequest driver, IClusterRequest... executors) throws ISchedulerException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void cancelJob(String id) throws ISchedulerException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public IJobInfo getJob(String id) throws ISchedulerException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public List<IJobInfo> listJobs(Map<String, String> filters) throws ISchedulerException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public IClusterInfo createCluster(String job, IClusterRequest request) throws ISchedulerException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public IClusterInfo getCluster(String job, String id) throws ISchedulerException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void destroyCluster(String job, String id) throws ISchedulerException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public IClusterInfo repairCluster(String job, IClusterInfo cluster, IClusterRequest request) throws ISchedulerException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public IContainerInfo.IStatus getContainerStatus(String job, String id) throws ISchedulerException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void healthCheck() throws ISchedulerException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

}
