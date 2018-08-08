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
package org.ignis.backend.cluster.tasks.container;

import java.nio.ByteBuffer;
import java.util.Map;
import org.apache.thrift.TException;
import org.ignis.backend.cluster.IContainer;
import org.ignis.backend.exception.IgnisException;

/**
 *
 * @author César Pomar
 */
public class ISendFilesTask extends IContainerTask {

    private final Map<String, ByteBuffer> files;

    public ISendFilesTask(IContainer container, Map<String, ByteBuffer> files) {
        super(container);
        this.files = files;
    }

    @Override
    public void execute() throws IgnisException {
        for (Map.Entry<String, ByteBuffer> entry : files.entrySet()) {
            try {
                container.getFileManager().sendFile(entry.getKey(), entry.getValue());
            } catch (TException ex) {
                throw new IgnisException("Fails to send " + entry.getKey(), ex);
            }
        }
    }

}