/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.pulsar.functions.runtime.functioncache;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.functions.utils.Exceptions;

/**
 * An implementation of {@link FunctionCacheManager}.
 */
@Slf4j
public class FunctionCacheManagerImpl implements FunctionCacheManager {

    /** Registered Functions **/
    private final Map<String, FunctionCacheEntry> cacheFunctions;

    public FunctionCacheManagerImpl() {
        this.cacheFunctions = Collections.synchronizedMap(Maps.newHashMap());
    }

    @VisibleForTesting
    Map<String, FunctionCacheEntry> getCacheFunctions() {
        return cacheFunctions;
    }

    @Override
    public ClassLoader getClassLoader(String fid) {
        checkNotNull(fid, "FunctionID not set");

        synchronized (cacheFunctions) {
            FunctionCacheEntry entry = cacheFunctions.get(fid);
            checkState(entry != null,
                "No dependencies are registered for function " + fid);
            return entry.getClassLoader();
        }
    }

    @Override
    public void registerFunctionInstance(String fid,
                                         String eid,
                                         List<String> requiredJarFiles,
                                         List<URL> requiredClasspaths)
            throws IOException {
        checkNotNull(fid, "FunctionID not set");

        synchronized (cacheFunctions) {
            FunctionCacheEntry entry = cacheFunctions.get(fid);

            if (null == entry) {
                URL[] urls = new URL[requiredJarFiles.size() + requiredClasspaths.size()];
                int count = 0;
                try {
                    // add jar files to urls
                    for (String jarFile : requiredJarFiles) {
                        urls[count++] = new File(jarFile).toURI().toURL();
                    }

                    // add classpaths
                    for (URL url : requiredClasspaths) {
                        urls[count++] = url;
                    }

                    cacheFunctions.put(
                        fid,
                        new FunctionCacheEntry(
                            requiredJarFiles,
                            requiredClasspaths,
                            urls,
                            eid));
                } catch (Throwable cause) {
                    Exceptions.rethrowIOException(cause);
                }
            } else {
                entry.register(
                    eid,
                    requiredJarFiles,
                    requiredClasspaths);
            }
        }
    }

    @Override
    public void unregisterFunctionInstance(String fid,
                                           String eid) {
        synchronized (cacheFunctions) {
            FunctionCacheEntry entry = cacheFunctions.get(fid);

            if (null != entry) {
                if (entry.unregister(eid)) {
                    cacheFunctions.remove(fid);
                    entry.close();
                }
            }
        }
    }

    @Override
    public void close() {
        synchronized (cacheFunctions) {
            cacheFunctions.values().forEach(FunctionCacheEntry::close);
        }
    }


}
