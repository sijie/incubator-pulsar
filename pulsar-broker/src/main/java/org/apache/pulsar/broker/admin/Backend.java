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
package org.apache.pulsar.broker.admin;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.apache.bookkeeper.conf.ClientConfiguration;
import org.apache.pulsar.common.backend.BackendData;

@Path("/backend")
@Api(value = "/backend", description = "Backend amin apis", tags = "backend")
@Produces(MediaType.APPLICATION_JSON)
public class Backend extends AdminResource {

    @GET
    @ApiOperation(value = "Get the backend info", response = BackendData.class)
    @ApiResponses(value = {
        @ApiResponse(code = 403, message = "Don't have admin permission")
    })
    public BackendData getBackendData() {
        ClientConfiguration conf = new ClientConfiguration();
        return new BackendData(
            pulsar().getConfiguration().getZookeeperServers(),
            pulsar().getConfiguration().getGlobalZookeeperServers(),
            conf.getZkLedgersRootPath());
    }

}
