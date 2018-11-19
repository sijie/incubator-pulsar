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
package org.apache.pulsar.functions.worker.rest.api;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.apache.bookkeeper.common.concurrent.FutureUtils.result;
import static org.apache.pulsar.functions.utils.Reflections.createInstance;

import com.google.gson.Gson;

import com.google.gson.GsonBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

import lombok.extern.slf4j.Slf4j;

import org.apache.bookkeeper.api.StorageClient;
import org.apache.bookkeeper.api.kv.Table;
import org.apache.bookkeeper.api.kv.result.KeyValue;
import org.apache.bookkeeper.clients.StorageClientBuilder;
import org.apache.bookkeeper.clients.config.StorageClientSettings;
import org.apache.bookkeeper.clients.exceptions.NamespaceNotFoundException;
import org.apache.bookkeeper.clients.exceptions.StreamNotFoundException;
import org.apache.bookkeeper.common.concurrent.FutureUtils;
import org.apache.commons.io.IOUtils;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.join;
import static org.apache.pulsar.functions.utils.Utils.*;

import org.apache.commons.lang3.StringUtils;
import org.apache.pulsar.client.admin.PulsarAdminException;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.Reader;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.common.functions.FunctionConfig;
import org.apache.pulsar.common.io.ConnectorDefinition;
import org.apache.pulsar.common.io.SinkConfig;
import org.apache.pulsar.common.io.SourceConfig;
import org.apache.pulsar.common.nar.NarClassLoader;
import org.apache.pulsar.common.policies.data.ErrorData;
import org.apache.pulsar.common.policies.data.FunctionStats;
import org.apache.pulsar.common.policies.data.TenantInfo;
import org.apache.pulsar.common.util.Codec;
import org.apache.pulsar.functions.proto.Function.FunctionDetails;
import org.apache.pulsar.functions.proto.Function.FunctionMetaData;
import org.apache.pulsar.functions.proto.Function.PackageLocationMetaData;
import org.apache.pulsar.functions.proto.Function.SinkSpec;
import org.apache.pulsar.functions.proto.Function.SourceSpec;
import org.apache.pulsar.functions.proto.InstanceCommunication;
import org.apache.pulsar.functions.proto.InstanceCommunication.FunctionStatus;
import org.apache.pulsar.functions.sink.PulsarSink;
import org.apache.pulsar.functions.utils.FunctionConfigUtils;
import org.apache.pulsar.functions.utils.SinkConfigUtils;
import org.apache.pulsar.functions.utils.SourceConfigUtils;
import org.apache.pulsar.functions.utils.StateUtils;
import org.apache.pulsar.functions.worker.FunctionMetaDataManager;
import org.apache.pulsar.functions.worker.FunctionRuntimeManager;
import org.apache.pulsar.functions.worker.Utils;
import org.apache.pulsar.functions.worker.WorkerService;
import org.apache.pulsar.functions.worker.request.RequestResult;
import org.apache.pulsar.io.core.Sink;
import org.apache.pulsar.io.core.Source;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;

import net.jodah.typetools.TypeResolver;

// TODO:-Currently The source/sink/functions all share this backend. In the future it might make sense
// to seperate them out in their own implementations as well.
@Slf4j
public class FunctionsImpl {

    public static final String FUNCTION = "Function";
    public static final String SOURCE = "Source";
    public static final String SINK = "Sink";

    private final Supplier<WorkerService> workerServiceSupplier;

    public FunctionsImpl(Supplier<WorkerService> workerServiceSupplier) {
        this.workerServiceSupplier = workerServiceSupplier;
    }

    private WorkerService worker() {
        try {
            return checkNotNull(workerServiceSupplier.get());
        } catch (Throwable t) {
            log.info("Failed to get worker service", t);
            throw t;
        }
    }

    private boolean isWorkerServiceAvailable() {
        WorkerService workerService = workerServiceSupplier.get();
        if (workerService == null) {
            return false;
        }
        if (!workerService.isInitialized()) {
            return false;
        }
        return true;
    }

    public Response registerFunction(final String tenant, final String namespace, final String componentName,
                                     final InputStream uploadedInputStream, final FormDataContentDisposition fileDetail,
                                     final String functionPkgUrl, final String functionDetailsJson, final String componentConfigJson,
                                     final String componentType, final String clientRole) {

        if (!isWorkerServiceAvailable()) {
            return getUnavailableResponse();
        }

        if (tenant == null) {
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData("Tenant is not provided")).build();
        }
        if (namespace == null) {
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData("Namespace is not provided")).build();
        }
        if (componentName == null) {
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(componentType + " Name is not provided")).build();
        }

        try {
            TenantInfo tenantInfo = worker().getBrokerAdmin().tenants().getTenantInfo(tenant);
            String qualifedNamespace = tenant + "/" + namespace;
            if (!worker().getBrokerAdmin().namespaces().getNamespaces(tenant).contains(qualifedNamespace)) {
                log.error("{}/{}/{} Namespace {} does not exist", tenant, namespace,
                        componentName, namespace);
                return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                        .entity(new ErrorData("Namespace does not exist")).build();
            }
        } catch (PulsarAdminException.NotAuthorizedException e) {
            log.error("{}/{}/{} Client [{}] is not admin and authorized to operate {} on tenant", tenant, namespace,
                    componentName, clientRole, componentType);
            return Response.status(Status.UNAUTHORIZED).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData("client is not authorize to perform operation")).build();

        } catch (PulsarAdminException.NotFoundException e) {
            log.error("{}/{}/{} Tenant {} does not exist", tenant, namespace,
                    componentName, tenant);
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData("Tenant does not exist")).build();
        } catch (PulsarAdminException e) {
            log.error("{}/{}/{} Issues getting tenant data", tenant, namespace,
                    componentName, e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(e.getMessage())).build();
        }

        try {
            if (!isAuthorizedRole(tenant, clientRole)) {
                log.error("{}/{}/{} Client [{}] is not admin and authorized to register {}", tenant, namespace,
                        componentName, clientRole, componentType);
                return Response.status(Status.UNAUTHORIZED).type(MediaType.APPLICATION_JSON)
                        .entity(new ErrorData("client is not authorize to perform operation")).build();
            }
        } catch (PulsarAdminException e) {
            log.error("{}/{}/{} Failed to authorize [{}]", tenant, namespace, componentName, e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(e.getMessage())).build();
        }

        FunctionMetaDataManager functionMetaDataManager = worker().getFunctionMetaDataManager();

        if (functionMetaDataManager.containsFunction(tenant, namespace, componentName)) {
            log.error("{} {}/{}/{} already exists", componentType, tenant, namespace, componentName);
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format("%s %s already exists", componentType, componentName))).build();
        }

        FunctionDetails functionDetails;
        boolean isPkgUrlProvided = isNotBlank(functionPkgUrl);
        File uploadedInputStreamAsFile = null;
        if (uploadedInputStream != null) {
            uploadedInputStreamAsFile = dumpToTmpFile(uploadedInputStream);
        }
        // validate parameters
        try {
            if (isPkgUrlProvided) {
                functionDetails = validateUpdateRequestParamsWithPkgUrl(tenant, namespace, componentName, functionPkgUrl,
                        functionDetailsJson, componentConfigJson, componentType);
            } else {
                functionDetails = validateUpdateRequestParams(tenant, namespace, componentName, uploadedInputStreamAsFile,
                        fileDetail, functionDetailsJson, componentConfigJson, componentType);
            }
        } catch (Exception e) {
            log.error("Invalid register {} request @ /{}/{}/{}", componentType, tenant, namespace, componentName, e);
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(e.getMessage())).build();
        }

        try {
            worker().getFunctionRuntimeManager().getRuntimeFactory().doAdmissionChecks(functionDetails);
        } catch (Exception e) {
            log.error("{} {}/{}/{} cannot be admitted by the runtime factory", componentType, tenant, namespace, componentName);
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format("%s %s cannot be admitted:- %s", componentType, componentName, e.getMessage()))).build();
        }

        // function state
        FunctionMetaData.Builder functionMetaDataBuilder = FunctionMetaData.newBuilder()
                .setFunctionDetails(functionDetails).setCreateTime(System.currentTimeMillis()).setVersion(0);


        PackageLocationMetaData.Builder packageLocationMetaDataBuilder;
        try {
            packageLocationMetaDataBuilder = getFunctionPackageLocation(functionDetails, componentType,
                functionPkgUrl, fileDetail, uploadedInputStreamAsFile);
        } catch (Exception e) {
            return Response.serverError().type(MediaType.APPLICATION_JSON).entity(new ErrorData(e.getMessage()))
                    .build();
        }

        functionMetaDataBuilder.setPackageLocation(packageLocationMetaDataBuilder);
        return updateRequest(functionMetaDataBuilder.build());
    }

    private PackageLocationMetaData.Builder getFunctionPackageLocation(FunctionDetails functionDetails,
                                                                       String componentType, String functionPkgUrl,
                                                                       final FormDataContentDisposition fileDetail,
                                                                       File uploadedInputStreamAsFile) throws Exception {
        String tenant = functionDetails.getTenant();
        String namespace = functionDetails.getNamespace();
        String componentName = functionDetails.getName();
        PackageLocationMetaData.Builder packageLocationMetaDataBuilder = PackageLocationMetaData.newBuilder();
        boolean isBuiltin = isFunctionCodeBuiltin(functionDetails);
        boolean isPkgUrlProvided = isNotBlank(functionPkgUrl);
        if (worker().getFunctionRuntimeManager().getRuntimeFactory().externallyManaged()) {
            // For externally managed schedulers, the pkgUrl/builtin stuff should be copied to bk
            if (isBuiltin) {
                File sinkOrSource;
                if (componentType.equals(SOURCE)) {
                    String archiveName = functionDetails.getSource().getBuiltin();
                    sinkOrSource = worker().getConnectorsManager().getSourceArchive(archiveName).toFile();
                } else {
                    String archiveName = functionDetails.getSink().getBuiltin();
                    sinkOrSource = worker().getConnectorsManager().getSinkArchive(archiveName).toFile();
                }
                packageLocationMetaDataBuilder.setPackagePath(createPackagePath(tenant, namespace, componentName,
                        sinkOrSource.getName()));
                packageLocationMetaDataBuilder.setOriginalFileName(sinkOrSource.getName());
                log.info("Uploading {} package to {}", componentType, packageLocationMetaDataBuilder.getPackagePath());
                Utils.uploadFileToBookkeeper(packageLocationMetaDataBuilder.getPackagePath(), sinkOrSource, worker().getDlogNamespace());
            } else if (isPkgUrlProvided) {
                File file = extractFileFromPkg(functionPkgUrl);
                packageLocationMetaDataBuilder.setPackagePath(createPackagePath(tenant, namespace, componentName,
                        file.getName()));
                packageLocationMetaDataBuilder.setOriginalFileName(file.getName());
                log.info("Uploading {} package to {}", componentType, packageLocationMetaDataBuilder.getPackagePath());
                Utils.uploadFileToBookkeeper(packageLocationMetaDataBuilder.getPackagePath(), file, worker().getDlogNamespace());
            } else {
                packageLocationMetaDataBuilder.setPackagePath(createPackagePath(tenant, namespace, componentName,
                        fileDetail.getFileName()));
                packageLocationMetaDataBuilder.setOriginalFileName(fileDetail.getFileName());
                log.info("Uploading {} package to {}", componentType, packageLocationMetaDataBuilder.getPackagePath());
                Utils.uploadFileToBookkeeper(packageLocationMetaDataBuilder.getPackagePath(), uploadedInputStreamAsFile, worker().getDlogNamespace());
            }
        } else {
            // For pulsar managed schedulers, the pkgUrl/builtin stuff should be copied to bk
            if (isBuiltin) {
                packageLocationMetaDataBuilder.setPackagePath("builtin://" + getFunctionCodeBuiltin(functionDetails));
            } else if (isPkgUrlProvided) {
                packageLocationMetaDataBuilder.setPackagePath(functionPkgUrl);
            } else {
                packageLocationMetaDataBuilder.setPackagePath(createPackagePath(tenant, namespace, componentName, fileDetail.getFileName()));
                packageLocationMetaDataBuilder.setOriginalFileName(fileDetail.getFileName());
                log.info("Uploading {} package to {}", componentType, packageLocationMetaDataBuilder.getPackagePath());
                Utils.uploadFileToBookkeeper(packageLocationMetaDataBuilder.getPackagePath(), uploadedInputStreamAsFile, worker().getDlogNamespace());
            }
        }
        return packageLocationMetaDataBuilder;
    }


    public Response updateFunction(final String tenant, final String namespace, final String componentName,
            final InputStream uploadedInputStream, final FormDataContentDisposition fileDetail,
            final String functionPkgUrl, final String functionDetailsJson, final String componentConfigJson,
            final String componentType, final String clientRole) {

        if (!isWorkerServiceAvailable()) {
            return getUnavailableResponse();
        }

        try {
            if (!isAuthorizedRole(tenant, clientRole)) {
                log.error("{}/{}/{} Client [{}] is not admin and authorized to update {}", tenant, namespace,
                        componentName, clientRole, componentType);
                return Response.status(Status.UNAUTHORIZED).type(MediaType.APPLICATION_JSON)
                        .entity(new ErrorData("client is not authorize to perform operation")).build();
            }
        } catch (PulsarAdminException e) {
            log.error("{}/{}/{} Failed to authorize [{}]", tenant, namespace, componentName, e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(e.getMessage())).build();
        }

        FunctionMetaDataManager functionMetaDataManager = worker().getFunctionMetaDataManager();

        if (!functionMetaDataManager.containsFunction(tenant, namespace, componentName)) {
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format("%s %s doesn't exist", componentType, componentName))).build();
        }

        FunctionDetails functionDetails;
        boolean isPkgUrlProvided = isNotBlank(functionPkgUrl);
        File uploadedInputStreamAsFile = null;
        if (uploadedInputStream != null) {
            uploadedInputStreamAsFile = dumpToTmpFile(uploadedInputStream);
        }
        // validate parameters
        try {
            if (isPkgUrlProvided) {
                functionDetails = validateUpdateRequestParamsWithPkgUrl(tenant, namespace, componentName, functionPkgUrl,
                        functionDetailsJson, componentConfigJson, componentType);
            } else {
                functionDetails = validateUpdateRequestParams(tenant, namespace, componentName, uploadedInputStreamAsFile,
                        fileDetail, functionDetailsJson, componentConfigJson, componentType);
            }
        } catch (Exception e) {
            log.error("Invalid register {} request @ /{}/{}/{}", componentType, tenant, namespace, componentName, e);
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(e.getMessage())).build();
        }

        try {
            worker().getFunctionRuntimeManager().getRuntimeFactory().doAdmissionChecks(functionDetails);
        } catch (Exception e) {
            log.error("Updated {} {}/{}/{} cannot be submitted to runtime factory", componentType, tenant, namespace, componentName);
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format("%s %s cannot be admitted:- %s", componentType, componentName, e.getMessage()))).build();
        }

        // function state
        FunctionMetaData.Builder functionMetaDataBuilder = FunctionMetaData.newBuilder()
                .setFunctionDetails(functionDetails).setCreateTime(System.currentTimeMillis()).setVersion(0);

        PackageLocationMetaData.Builder packageLocationMetaDataBuilder;
        try {
            packageLocationMetaDataBuilder = getFunctionPackageLocation(functionDetails, componentType,
                    functionPkgUrl, fileDetail, uploadedInputStreamAsFile);
        } catch (Exception e) {
            return Response.serverError().type(MediaType.APPLICATION_JSON).entity(new ErrorData(e.getMessage()))
                    .build();
        }

        functionMetaDataBuilder.setPackageLocation(packageLocationMetaDataBuilder);
        return updateRequest(functionMetaDataBuilder.build());
    }

    public Response deregisterFunction(final String tenant, final String namespace, final String componentName,
            String componentType, String clientRole) {

        if (!isWorkerServiceAvailable()) {
            return getUnavailableResponse();
        }

        try {
            if (!isAuthorizedRole(tenant, clientRole)) {
                log.error("{}/{}/{} Client [{}] is not admin and authorized to deregister {}", tenant, namespace,
                        componentName, clientRole, componentType);
                return Response.status(Status.UNAUTHORIZED).type(MediaType.APPLICATION_JSON)
                        .entity(new ErrorData("client is not authorize to perform operation")).build();
            }
        } catch (PulsarAdminException e) {
            log.error("{}/{}/{} Failed to authorize [{}]", tenant, namespace, componentName, e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(e.getMessage())).build();
        }

        // delete state table
        if (null != worker().getStateStoreAdminClient()) {
            final String tableNs = StateUtils.getStateNamespace(tenant, namespace);
            final String tableName = componentName;
            try {
                FutureUtils.result(worker().getStateStoreAdminClient().deleteStream(tableNs, tableName));
            } catch (NamespaceNotFoundException | StreamNotFoundException e) {
                // ignored if the state table doesn't exist
            } catch (Exception e) {
                log.error("{}/{}/{} Failed to delete state table", e);
                return Response
                    .status(Status.INTERNAL_SERVER_ERROR)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(e.getMessage()))
                    .build();
            }
        }

        // validate parameters
        try {
            validateDeregisterRequestParams(tenant, namespace, componentName, componentType);
        } catch (IllegalArgumentException e) {
            log.error("Invalid deregister {} request @ /{}/{}/{}", componentType, tenant, namespace, componentName, e);
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(e.getMessage())).build();
        }

        FunctionMetaDataManager functionMetaDataManager = worker().getFunctionMetaDataManager();
        if (!functionMetaDataManager.containsFunction(tenant, namespace, componentName)) {
            log.error("{} to deregister does not exist @ /{}/{}/{}", componentType, tenant, namespace, componentName);
            return Response.status(Status.NOT_FOUND).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format("%s %s doesn't exist", componentType, componentName))).build();
        }
        FunctionMetaData functionMetaData = functionMetaDataManager.getFunctionMetaData(tenant, namespace, componentName);
        if (!calculateSubjectType(functionMetaData).equals(componentType)) {
            log.error("{}/{}/{} is not a {}", tenant, namespace, componentName, componentType);
            return Response.status(Status.NOT_FOUND).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format("%s %s doesn't exist", componentType, componentName))).build();
        }

        CompletableFuture<RequestResult> completableFuture = functionMetaDataManager.deregisterFunction(tenant,
                namespace, componentName);

        RequestResult requestResult = null;
        try {
            requestResult = completableFuture.get();
            if (!requestResult.isSuccess()) {
                return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                        .entity(new ErrorData(requestResult.getMessage())).build();
            }
        } catch (ExecutionException e) {
            log.error("Execution Exception while deregistering {} @ /{}/{}/{}", componentType, tenant, namespace, componentName,
                    e);
            return Response.serverError().type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(e.getCause().getMessage())).build();
        } catch (InterruptedException e) {
            log.error("Interrupted Exception while deregistering {} @ /{}/{}/{}", componentType, tenant, namespace, componentName,
                    e);
            return Response.status(Status.REQUEST_TIMEOUT).type(MediaType.APPLICATION_JSON).build();
        }

        return Response.status(Status.OK).entity(requestResult.toJson()).build();
    }

    public Response getFunctionInfo(final String tenant, final String namespace, final String componentName,
                                    final String componentType)
            throws IOException {

        if (!isWorkerServiceAvailable()) {
            return getUnavailableResponse();
        }

        // validate parameters
        try {
            validateGetFunctionRequestParams(tenant, namespace, componentName, componentType);
        } catch (IllegalArgumentException e) {
            log.error("Invalid get {} request @ /{}/{}/{}", componentType, tenant, namespace, componentName, e);
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(e.getMessage())).build();
        }

        FunctionMetaDataManager functionMetaDataManager = worker().getFunctionMetaDataManager();
        if (!functionMetaDataManager.containsFunction(tenant, namespace, componentName)) {
            log.error("{} does not exist @ /{}/{}/{}", componentType, tenant, namespace, componentName);
            return Response.status(Status.NOT_FOUND).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format(componentType + " %s doesn't exist", componentName))).build();
        }
        FunctionMetaData functionMetaData = functionMetaDataManager.getFunctionMetaData(tenant, namespace, componentName);
        if (!calculateSubjectType(functionMetaData).equals(componentType)) {
            log.error("{}/{}/{} is not a {}", tenant, namespace, componentName, componentType);
            return Response.status(Status.NOT_FOUND).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format(componentType + " %s doesn't exist", componentName))).build();
        }

        String retval;
        if (componentType.equals(FUNCTION)) {
            FunctionConfig config = FunctionConfigUtils.convertFromDetails(functionMetaData.getFunctionDetails());
            retval = new Gson().toJson(config);
        } else if (componentType.equals(SOURCE)) {
            SourceConfig config = SourceConfigUtils.convertFromDetails(functionMetaData.getFunctionDetails());
            retval = new Gson().toJson(config);
        } else {
            SinkConfig config = SinkConfigUtils.convertFromDetails(functionMetaData.getFunctionDetails());
            retval = new Gson().toJson(config);
        }
        return Response.status(Status.OK).entity(retval).build();
    }

    public Response stopFunctionInstance(final String tenant, final String namespace, final String componentName,
            final String componentType, final String instanceId, URI uri) {
        return stopFunctionInstance(tenant, namespace, componentName, componentType, instanceId, false, uri);
    }

    public Response restartFunctionInstance(final String tenant, final String namespace, final String componentName,
            final String componentType, final String instanceId, URI uri) {
        return stopFunctionInstance(tenant, namespace, componentName, componentType, instanceId, true, uri);
    }

    public Response stopFunctionInstance(final String tenant, final String namespace, final String componentName,
            final String componentType, final String instanceId, boolean restart, URI uri) {

        if (!isWorkerServiceAvailable()) {
            return getUnavailableResponse();
        }

        // validate parameters
        try {
            validateGetFunctionInstanceRequestParams(tenant, namespace, componentName, componentType, instanceId);
        } catch (IllegalArgumentException e) {
            log.error("Invalid restart {} request @ /{}/{}/{}", componentType, tenant, namespace, componentName, e);
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(e.getMessage())).build();
        }

        FunctionMetaDataManager functionMetaDataManager = worker().getFunctionMetaDataManager();
        if (!functionMetaDataManager.containsFunction(tenant, namespace, componentName)) {
            log.error("{} does not exist @ /{}/{}/{}", componentType, tenant, namespace, componentName);
            return Response.status(Status.NOT_FOUND).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format("%s %s doesn't exist", componentType, componentName))).build();
        }

        FunctionMetaData functionMetaData = functionMetaDataManager.getFunctionMetaData(tenant, namespace, componentName);
        if (!calculateSubjectType(functionMetaData).equals(componentType)) {
            log.error("{}/{}/{} is not a {}", tenant, namespace, componentName, componentType);
            return Response.status(Status.NOT_FOUND).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format(componentType + " %s doesn't exist", componentName))).build();
        }

        FunctionRuntimeManager functionRuntimeManager = worker().getFunctionRuntimeManager();
        try {
            return functionRuntimeManager.stopFunctionInstance(tenant, namespace, componentName,
                    Integer.parseInt(instanceId), restart, uri);
        } catch (WebApplicationException we) {
            throw we;
        } catch (Exception e) {
            log.error("Failed to restart {}: {}/{}/{}/{}", componentType, tenant, namespace, componentName, instanceId, e);
            return Response.status(Status.INTERNAL_SERVER_ERROR.getStatusCode(), e.getMessage()).build();
        }
    }

    public Response stopFunctionInstances(final String tenant, final String namespace, final String componentName,
                                          final String componentType) {
        return stopFunctionInstances(tenant, namespace, componentName, componentType, false);
    }

    public Response restartFunctionInstances(final String tenant, final String namespace, final String componentName,
                                             final String componentType) {
        return stopFunctionInstances(tenant, namespace, componentName, componentType, true);
    }

    public Response stopFunctionInstances(final String tenant, final String namespace, final String componentName,
            final String componentType, boolean restart) {

        if (!isWorkerServiceAvailable()) {
            return getUnavailableResponse();
        }

        // validate parameters
        try {
            validateGetFunctionRequestParams(tenant, namespace, componentName, componentType);
        } catch (IllegalArgumentException e) {
            log.error("Invalid restart {} request @ /{}/{}/{}", componentType, tenant, namespace, componentName, e);
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(e.getMessage())).build();
        }

        FunctionMetaDataManager functionMetaDataManager = worker().getFunctionMetaDataManager();
        if (!functionMetaDataManager.containsFunction(tenant, namespace, componentName)) {
            log.error("{} in getFunctionStatus does not exist @ /{}/{}/{}", componentType, tenant, namespace, componentName);
            return Response.status(Status.NOT_FOUND).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format("%s %s doesn't exist", componentType, componentName))).build();
        }

        FunctionMetaData functionMetaData = functionMetaDataManager.getFunctionMetaData(tenant, namespace, componentName);
        if (!calculateSubjectType(functionMetaData).equals(componentType)) {
            log.error("{}/{}/{} is not a {}", tenant, namespace, componentName, componentType);
            return Response.status(Status.NOT_FOUND).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format(componentType + " %s doesn't exist", componentName))).build();
        }

        FunctionRuntimeManager functionRuntimeManager = worker().getFunctionRuntimeManager();
        try {
            return functionRuntimeManager.stopFunctionInstances(tenant, namespace, componentName, restart);
        } catch (WebApplicationException we) {
            throw we;
        } catch (Exception e) {
            log.error("Failed to restart {}: {}/{}/{}", componentType, tenant, namespace, componentName, e);
            return Response.status(Status.INTERNAL_SERVER_ERROR.getStatusCode(), e.getMessage()).build();
        }
    }

    public Response getFunctionInstanceStatus(final String tenant, final String namespace, final String componentName,
                                              final String componentType, final String instanceId, URI uri) throws IOException {

        if (!isWorkerServiceAvailable()) {
            return getUnavailableResponse();
        }

        // validate parameters
        try {
            validateGetFunctionInstanceRequestParams(tenant, namespace, componentName, componentType, instanceId);
        } catch (IllegalArgumentException e) {
            log.error("Invalid get {} Status request @ /{}/{}/{}", componentType, tenant, namespace, componentName, e);
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(e.getMessage())).build();
        }

        FunctionMetaDataManager functionMetaDataManager = worker().getFunctionMetaDataManager();
        if (!functionMetaDataManager.containsFunction(tenant, namespace, componentName)) {
            log.error("{} in get {} Status does not exist @ /{}/{}/{}", componentType, componentType, tenant, namespace, componentName);
            return Response.status(Status.NOT_FOUND).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format("%s %s doesn't exist", componentType, componentName))).build();
        }
        FunctionMetaData functionMetaData = functionMetaDataManager.getFunctionMetaData(tenant, namespace, componentName);
        if (!calculateSubjectType(functionMetaData).equals(componentType)) {
            log.error("{}/{}/{} is not a {}", tenant, namespace, componentName, componentType);
            return Response.status(Status.NOT_FOUND).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format(componentType + " %s doesn't exist", componentName))).build();
        }
        int instanceIdInt = Integer.parseInt(instanceId);
        if (instanceIdInt < 0 || instanceIdInt >= functionMetaData.getFunctionDetails().getParallelism()) {
            log.error("instanceId in get {} Status out of bounds @ /{}/{}/{}", componentType, tenant, namespace, componentName);
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format("Invalid InstanceId"))).build();
        }

        FunctionRuntimeManager functionRuntimeManager = worker().getFunctionRuntimeManager();
        FunctionStatus functionStatus = null;
        try {
            functionStatus = functionRuntimeManager.getFunctionInstanceStatus(tenant, namespace, componentName,
                    Integer.parseInt(instanceId), uri);
        } catch (WebApplicationException we) {
            throw we;
        } catch (Exception e) {
            log.error("{}/{}/{} Got Exception Getting Status", tenant, namespace, componentName, e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(new ErrorData(e.getMessage())).build();
        }

        String jsonResponse = org.apache.pulsar.functions.utils.Utils.printJson(functionStatus);
        return Response.status(Status.OK).entity(jsonResponse).build();
    }

    public Response getFunctionStatus(final String tenant, final String namespace, final String componentName,
                                      final String componentType, URI uri)
            throws IOException {

        if (!isWorkerServiceAvailable()) {
            return getUnavailableResponse();
        }

        // validate parameters
        try {
            validateGetFunctionRequestParams(tenant, namespace, componentName, componentType);
        } catch (IllegalArgumentException e) {
            log.error("Invalid get {} Status request @ /{}/{}/{}", componentType, tenant, namespace, componentName, e);
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(e.getMessage())).build();
        }

        FunctionMetaDataManager functionMetaDataManager = worker().getFunctionMetaDataManager();
        if (!functionMetaDataManager.containsFunction(tenant, namespace, componentName)) {
            log.error("{} in get {} Status does not exist @ /{}/{}/{}", componentType, componentType, tenant, namespace, componentName);
            return Response.status(Status.NOT_FOUND).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format("%s %s doesn't exist", componentType, componentName))).build();
        }

        FunctionMetaData functionMetaData = functionMetaDataManager.getFunctionMetaData(tenant, namespace, componentName);
        if (!calculateSubjectType(functionMetaData).equals(componentType)) {
            log.error("{}/{}/{} is not a {}", tenant, namespace, componentName, componentType);
            return Response.status(Status.NOT_FOUND).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format(componentType + " %s doesn't exist", componentName))).build();
        }

        FunctionRuntimeManager functionRuntimeManager = worker().getFunctionRuntimeManager();
        InstanceCommunication.FunctionStatusList functionStatusList = null;
        try {
            functionStatusList = functionRuntimeManager.getAllFunctionStatus(tenant, namespace, componentName, uri);
        } catch (WebApplicationException we) {
            throw we;
        } catch (Exception e) {
            log.error("{}/{}/{} Got Exception Getting Status", tenant, namespace, componentName, e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(new ErrorData(e.getMessage())).build();
        }

        String jsonResponse = org.apache.pulsar.functions.utils.Utils.printJson(functionStatusList);
        return Response.status(Status.OK).entity(jsonResponse).build();
    }

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public Response getFunctionStats(final String tenant, final String namespace, final String componentName,
                                     final String componentType, URI uri) throws IOException {
        if (!isWorkerServiceAvailable()) {
            return getUnavailableResponse();
        }

        // validate parameters
        try {
            validateGetFunctionRequestParams(tenant, namespace, componentName, componentType);
        } catch (IllegalArgumentException e) {
            log.error("Invalid get {} Status request @ /{}/{}/{}", componentType, tenant, namespace, componentName, e);
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(e.getMessage())).build();
        }

        FunctionMetaDataManager functionMetaDataManager = worker().getFunctionMetaDataManager();
        if (!functionMetaDataManager.containsFunction(tenant, namespace, componentName)) {
            log.error("{} in get {} Status does not exist @ /{}/{}/{}", componentType, componentType, tenant, namespace, componentName);
            return Response.status(Status.NOT_FOUND).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format("%s %s doesn't exist", componentType, componentName))).build();
        }

        FunctionMetaData functionMetaData = functionMetaDataManager.getFunctionMetaData(tenant, namespace, componentName);
        if (!calculateSubjectType(functionMetaData).equals(componentType)) {
            log.error("{}/{}/{} is not a {}", tenant, namespace, componentName, componentType);
            return Response.status(Status.NOT_FOUND).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format(componentType + " %s doesn't exist", componentName))).build();
        }

        FunctionRuntimeManager functionRuntimeManager = worker().getFunctionRuntimeManager();
        FunctionStats functionStats;
        try {
            functionStats = functionRuntimeManager.getFunctionStats(tenant, namespace, componentName, uri);
        } catch (WebApplicationException we) {
            throw we;
        } catch (Exception e) {
            log.error("{}/{}/{} Got Exception Getting Status", tenant, namespace, componentName, e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(new ErrorData(e.getMessage())).build();
        }

        return Response.status(Status.OK).entity(gson.toJson(functionStats)).build();

    }

    public Response getFunctionsInstanceStats(final String tenant, final String namespace, final String componentName,
                                      final String componentType, String instanceId, URI uri) {
        if (!isWorkerServiceAvailable()) {
            return getUnavailableResponse();
        }

        // validate parameters
        try {
            validateGetFunctionInstanceRequestParams(tenant, namespace, componentName, componentType, instanceId);
        } catch (IllegalArgumentException e) {
            log.error("Invalid get {} Stats request @ /{}/{}/{}", componentType, tenant, namespace, componentName, e);
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(e.getMessage())).build();
        }

        FunctionMetaDataManager functionMetaDataManager = worker().getFunctionMetaDataManager();
        if (!functionMetaDataManager.containsFunction(tenant, namespace, componentName)) {
            log.error("{} in get {} Stats does not exist @ /{}/{}/{}", componentType, componentType, tenant, namespace, componentName);
            return Response.status(Status.NOT_FOUND).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format("%s %s doesn't exist", componentType, componentName))).build();
        }
        FunctionMetaData functionMetaData = functionMetaDataManager.getFunctionMetaData(tenant, namespace, componentName);
        if (!calculateSubjectType(functionMetaData).equals(componentType)) {
            log.error("{}/{}/{} is not a {}", tenant, namespace, componentName, componentType);
            return Response.status(Status.NOT_FOUND).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format(componentType + " %s doesn't exist", componentName))).build();
        }
        int instanceIdInt = Integer.parseInt(instanceId);
        if (instanceIdInt < 0 || instanceIdInt >= functionMetaData.getFunctionDetails().getParallelism()) {
            log.error("instanceId in get {} Stats out of bounds @ /{}/{}/{}", componentType, tenant, namespace, componentName);
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format("Invalid InstanceId"))).build();
        }

        FunctionRuntimeManager functionRuntimeManager = worker().getFunctionRuntimeManager();
        FunctionStats.FunctionInstanceStats.FunctionInstanceStatsData functionInstanceStatsData;
        try {
            functionInstanceStatsData = functionRuntimeManager.getFunctionInstanceStats(tenant, namespace, componentName,
                    Integer.parseInt(instanceId), uri);
        } catch (WebApplicationException we) {
            throw we;
        } catch (Exception e) {
            log.error("{}/{}/{} Got Exception Getting Stats", tenant, namespace, componentName, e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).entity(new ErrorData(e.getMessage())).build();
        }

        return Response.status(Status.OK).entity(gson.toJson(functionInstanceStatsData)).build();
    }

    public Response listFunctions(final String tenant, final String namespace, String componentType) {

        if (!isWorkerServiceAvailable()) {
            return getUnavailableResponse();
        }

        // validate parameters
        try {
            validateListFunctionRequestParams(tenant, namespace);
        } catch (IllegalArgumentException e) {
            log.error("Invalid list {} request @ /{}/{}", componentType, tenant, namespace, e);
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(e.getMessage())).build();
        }

        FunctionMetaDataManager functionMetaDataManager = worker().getFunctionMetaDataManager();

        Collection<FunctionMetaData> functionStateList = functionMetaDataManager.listFunctions(tenant, namespace);
        List<String> retval = new LinkedList<>();
        for (FunctionMetaData functionMetaData : functionStateList) {
            if (calculateSubjectType(functionMetaData).equals(componentType)) {
                retval.add(functionMetaData.getFunctionDetails().getName());
            }
        }

        return Response.status(Status.OK).entity(new Gson().toJson(retval.toArray())).build();
    }

    private Response updateRequest(FunctionMetaData functionMetaData) {

        // Submit to FMT
        FunctionMetaDataManager functionMetaDataManager = worker().getFunctionMetaDataManager();

        CompletableFuture<RequestResult> completableFuture = functionMetaDataManager.updateFunction(functionMetaData);

        RequestResult requestResult = null;
        try {
            requestResult = completableFuture.get();
            if (!requestResult.isSuccess()) {
                return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                        .entity(new ErrorData(requestResult.getMessage())).build();
            }
        } catch (ExecutionException e) {
            return Response.serverError().type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(e.getCause().getMessage())).build();
        } catch (InterruptedException e) {
            return Response.status(Status.REQUEST_TIMEOUT).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(e.getCause().getMessage())).build();
        }

        return Response.status(Status.OK).build();
    }

    public List<ConnectorDefinition> getListOfConnectors() {
        if (!isWorkerServiceAvailable()) {
            throw new WebApplicationException(
                    Response.status(Status.SERVICE_UNAVAILABLE).type(MediaType.APPLICATION_JSON)
                            .entity(new ErrorData("Function worker service is not avaialable")).build());
        }

        return this.worker().getConnectorsManager().getConnectors();
    }

    public Response triggerFunction(final String tenant, final String namespace, final String functionName,
            final String input, final InputStream uploadedInputStream, final String topic) {

        if (!isWorkerServiceAvailable()) {
            return getUnavailableResponse();
        }

        FunctionDetails functionDetails;
        // validate parameters
        try {
            validateTriggerRequestParams(tenant, namespace, functionName, topic, input, uploadedInputStream);
        } catch (IllegalArgumentException e) {
            log.error("Invalid trigger function request @ /{}/{}/{}", tenant, namespace, functionName, e);
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(e.getMessage())).build();
        }

        FunctionMetaDataManager functionMetaDataManager = worker().getFunctionMetaDataManager();
        if (!functionMetaDataManager.containsFunction(tenant, namespace, functionName)) {
            log.error("Function in trigger function does not exist @ /{}/{}/{}", tenant, namespace, functionName);
            return Response.status(Status.NOT_FOUND).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(String.format("Function %s doesn't exist", functionName))).build();
        }

        FunctionMetaData functionMetaData = functionMetaDataManager.getFunctionMetaData(tenant, namespace,
                functionName);

        String inputTopicToWrite;
        if (topic != null) {
            inputTopicToWrite = topic;
        } else if (functionMetaData.getFunctionDetails().getSource().getInputSpecsCount() == 1) {
            inputTopicToWrite = functionMetaData.getFunctionDetails().getSource().getInputSpecsMap()
                    .keySet().iterator().next();
        } else {
            log.error("Function in trigger function has more than 1 input topics @ /{}/{}/{}", tenant, namespace, functionName);
            return Response.status(Status.BAD_REQUEST).build();
        }
        if (functionMetaData.getFunctionDetails().getSource().getInputSpecsCount() == 0
                || !functionMetaData.getFunctionDetails().getSource().getInputSpecsMap()
                        .containsKey(inputTopicToWrite)) {
            log.error("Function in trigger function has unidentified topic @ /{}/{}/{} {}", tenant, namespace, functionName, inputTopicToWrite);

            return Response.status(Status.BAD_REQUEST).build();
        }
        String outputTopic = functionMetaData.getFunctionDetails().getSink().getTopic();
        Reader<byte[]> reader = null;
        Producer<byte[]> producer = null;
        try {
            if (outputTopic != null && !outputTopic.isEmpty()) {
                reader = worker().getClient().newReader().topic(outputTopic).startMessageId(MessageId.latest).create();
            }
            producer = worker().getClient().newProducer(Schema.AUTO_PRODUCE_BYTES())
                .topic(inputTopicToWrite)
                .create();
            byte[] targetArray;
            if (uploadedInputStream != null) {
                targetArray = new byte[uploadedInputStream.available()];
                uploadedInputStream.read(targetArray);
            } else {
                targetArray = input.getBytes();
            }
            MessageId msgId = producer.send(targetArray);
            if (reader == null) {
                return Response.status(Status.OK).build();
            }
            long curTime = System.currentTimeMillis();
            long maxTime = curTime + 1000;
            while (curTime < maxTime) {
                Message msg = reader.readNext(10000, TimeUnit.MILLISECONDS);
                if (msg == null)
                    break;
                if (msg.getProperties().containsKey("__pfn_input_msg_id__")
                        && msg.getProperties().containsKey("__pfn_input_topic__")) {
                    MessageId newMsgId = MessageId.fromByteArray(
                            Base64.getDecoder().decode((String) msg.getProperties().get("__pfn_input_msg_id__")));
                    if (msgId.equals(newMsgId)
                            && msg.getProperties().get("__pfn_input_topic__").equals(inputTopicToWrite)) {
                        return Response.status(Status.OK).entity(msg.getData()).build();
                    }
                }
                curTime = System.currentTimeMillis();
            }
            return Response.status(Status.REQUEST_TIMEOUT).build();
        } catch (Exception e) {
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            if (reader != null) {
                reader.closeAsync();
            }
            if (producer != null) {
                producer.closeAsync();
            }
        }
    }

    public Response getFunctionState(final String tenant, final String namespace,
                                     final String functionName, final String key) {
        if (!isWorkerServiceAvailable()) {
            return getUnavailableResponse();
        }

        if (null == worker().getStateStoreAdminClient()) {
            return getStateStoreUnvailableResponse();
        }

        // validate parameters
        try {
            validateGetFunctionStateParams(tenant, namespace, functionName, key);
        } catch (IllegalArgumentException e) {
            log.error("Invalid getFunctionState request @ /{}/{}/{}/{}",
                tenant, namespace, functionName, key, e);
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                .entity(new ErrorData(e.getMessage())).build();
        }

        String tableNs = StateUtils.getStateNamespace(tenant, namespace);
        String tableName = functionName;

        String stateStorageServiceUrl = worker().getWorkerConfig().getStateStorageServiceUrl();

        try (StorageClient client = StorageClientBuilder.newBuilder()
            .withSettings(StorageClientSettings.newBuilder()
                .serviceUri(stateStorageServiceUrl)
                .clientName("functions-admin")
                .build())
            .withNamespace(tableNs)
            .build()) {
            try (Table<ByteBuf, ByteBuf> table = result(client.openTable(tableName))) {
                try (KeyValue<ByteBuf, ByteBuf> kv = result(table.getKv(Unpooled.wrappedBuffer(key.getBytes(UTF_8))))) {
                    if (null == kv) {
                        return Response.status(Status.NOT_FOUND)
                            .entity(new String("key '" + key + "' doesn't exist."))
                            .build();
                    } else {
                        String value;
                        if (kv.isNumber()) {
                            value = "value : " + kv.numberValue() + ", version : " + kv.version();
                        } else {
                            value = "value : " + new String(ByteBufUtil.getBytes(kv.value()), UTF_8)
                                + ", version : " + kv.version();
                        }
                        return Response.status(Status.OK)
                            .entity(new String(value))
                            .build();
                    }
                }
            } catch (Exception e) {
                log.error("Error while getFunctionState request @ /{}/{}/{}/{}",
                    tenant, namespace, functionName, key, e);
                return Response.status(Status.INTERNAL_SERVER_ERROR).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(e.getMessage())).build();
            }
        }
    }

    public Response uploadFunction(final InputStream uploadedInputStream, final String path) {
        // validate parameters
        try {
            if (uploadedInputStream == null || path == null) {
                throw new IllegalArgumentException("Function Package is not provided " + path);
            }
        } catch (IllegalArgumentException e) {
            log.error("Invalid upload function request @ /{}", path, e);
            return Response.status(Status.BAD_REQUEST).type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorData(e.getMessage())).build();
        }

        // Upload to bookkeeper
        try {
            log.info("Uploading function package to {}", path);

            Utils.uploadToBookeeper(worker().getDlogNamespace(), uploadedInputStream, path);
        } catch (IOException e) {
            log.error("Error uploading file {}", path, e);
            return Response.serverError().type(MediaType.APPLICATION_JSON).entity(new ErrorData(e.getMessage()))
                    .build();
        }

        return Response.status(Status.OK).build();
    }

    public Response downloadFunction(final String path) {
        return Response.status(Status.OK).entity(new StreamingOutput() {
            @Override
            public void write(final OutputStream output) throws IOException {
                if (path.startsWith(org.apache.pulsar.common.functions.Utils.HTTP)) {
                    URL url = new URL(path);
                    IOUtils.copy(url.openStream(), output);
                } else if (path.startsWith(org.apache.pulsar.common.functions.Utils.FILE)) {
                    URL url = new URL(path);
                    File file;
                    try {
                        file = new File(url.toURI());
                        IOUtils.copy(new FileInputStream(file), output);
                    } catch (URISyntaxException e) {
                        throw new IllegalArgumentException("invalid file url path: " + path);
                    }
                } else {
                    Utils.downloadFromBookkeeper(worker().getDlogNamespace(), output, path);
                }
            }
        }).build();
    }

    private void validateListFunctionRequestParams(String tenant, String namespace) throws IllegalArgumentException {

        if (tenant == null) {
            throw new IllegalArgumentException("Tenant is not provided");
        }
        if (namespace == null) {
            throw new IllegalArgumentException("Namespace is not provided");
        }
    }

    private void validateGetFunctionInstanceRequestParams(String tenant, String namespace, String componentName,
            String componentType, String instanceId) throws IllegalArgumentException {
        validateGetFunctionRequestParams(tenant, namespace, componentName, componentType);
        if (instanceId == null) {
            throw new IllegalArgumentException(String.format("%s Instance Id is not provided", componentType));
        }
    }

    private void validateGetFunctionRequestParams(String tenant, String namespace, String subject, String subjectType)
            throws IllegalArgumentException {

        if (tenant == null) {
            throw new IllegalArgumentException("Tenant is not provided");
        }
        if (namespace == null) {
            throw new IllegalArgumentException("Namespace is not provided");
        }
        if (subject == null) {
            throw new IllegalArgumentException(subjectType + " Name is not provided");
        }
    }

    private void validateDeregisterRequestParams(String tenant, String namespace, String subject, String subjectType)
            throws IllegalArgumentException {

        if (tenant == null) {
            throw new IllegalArgumentException("Tenant is not provided");
        }
        if (namespace == null) {
            throw new IllegalArgumentException("Namespace is not provided");
        }
        if (subject == null) {
            throw new IllegalArgumentException(subjectType + " Name is not provided");
        }
    }

    private FunctionDetails validateUpdateRequestParamsWithPkgUrl(String tenant, String namespace, String componentName,
            String functionPkgUrl, String functionDetailsJson, String componentConfigJson,
            String componentType)
            throws IllegalArgumentException, IOException, URISyntaxException {
        if (!org.apache.pulsar.common.functions.Utils.isFunctionPackageUrlSupported(functionPkgUrl)) {
            throw new IllegalArgumentException("Function Package url is not valid. supported url (http/https/file)");
        }
        FunctionDetails functionDetails = validateUpdateRequestParams(tenant, namespace, componentName,
                functionDetailsJson, componentConfigJson, componentType, functionPkgUrl, null);
        return functionDetails;
    }

    private FunctionDetails validateUpdateRequestParams(String tenant, String namespace, String componentName,
            File uploadedInputStreamAsFile, FormDataContentDisposition fileDetail, String functionDetailsJson,
            String componentConfigJson, String componentType)
            throws IllegalArgumentException, IOException, URISyntaxException {

        FunctionDetails functionDetails = validateUpdateRequestParams(tenant, namespace, componentName,
                functionDetailsJson, componentConfigJson, componentType,null, uploadedInputStreamAsFile);
        if (!isFunctionCodeBuiltin(functionDetails) && (uploadedInputStreamAsFile == null || fileDetail == null)) {
            throw new IllegalArgumentException("Function Package is not provided");
        }

        return functionDetails;
    }

    private static File dumpToTmpFile(InputStream uploadedInputStream) {
        try {
            File tmpFile = File.createTempFile("functions", null);
            tmpFile.deleteOnExit();
            Files.copy(uploadedInputStream, tmpFile.toPath(), REPLACE_EXISTING);
            return tmpFile;
        } catch (IOException e) {
            throw new RuntimeException("Cannot create a temporary file", e);
        }
    }

    private void validateGetFunctionStateParams(String tenant, String namespace, String functionName, String key)
        throws IllegalArgumentException {

        if (tenant == null) {
            throw new IllegalArgumentException("Tenant is not provided");
        }
        if (namespace == null) {
            throw new IllegalArgumentException("Namespace is not provided");
        }
        if (functionName == null) {
            throw new IllegalArgumentException("Function Name is not provided");
        }
        if (key == null) {
            throw new IllegalArgumentException("Key is not provided");
        }
    }

    private boolean isFunctionCodeBuiltin(FunctionDetails functionDetails) {
        if (functionDetails.hasSource()) {
            SourceSpec sourceSpec = functionDetails.getSource();
            if (!isEmpty(sourceSpec.getBuiltin())) {
                return true;
            }
        }

        if (functionDetails.hasSink()) {
            SinkSpec sinkSpec = functionDetails.getSink();
            if (!isEmpty(sinkSpec.getBuiltin())) {
                return true;
            }
        }

        return false;
    }

    private String getFunctionCodeBuiltin(FunctionDetails functionDetails) {
        if (functionDetails.hasSource()) {
            SourceSpec sourceSpec = functionDetails.getSource();
            if (!isEmpty(sourceSpec.getBuiltin())) {
                return sourceSpec.getBuiltin();
            }
        }

        if (functionDetails.hasSink()) {
            SinkSpec sinkSpec = functionDetails.getSink();
            if (!isEmpty(sinkSpec.getBuiltin())) {
                return sinkSpec.getBuiltin();
            }
        }

        return null;
    }

    private FunctionDetails validateUpdateRequestParams(String tenant, String namespace, String componentName,
            String functionDetailsJson, String componentConfigJson, String componentType,
            String functionPkgUrl, File uploadedInputStreamAsFile) throws IOException {
        if (tenant == null) {
            throw new IllegalArgumentException("Tenant is not provided");
        }
        if (namespace == null) {
            throw new IllegalArgumentException("Namespace is not provided");
        }
        if (componentName == null) {
            throw new IllegalArgumentException(String.format("%s Name is not provided", componentType));
        }

        if (componentType.equals(FUNCTION) && !isEmpty(componentConfigJson)) {
            FunctionConfig functionConfig = new Gson().fromJson(componentConfigJson, FunctionConfig.class);
            // The rest end points take precendence over whatever is there in functionconfig
            functionConfig.setTenant(tenant);
            functionConfig.setNamespace(namespace);
            functionConfig.setName(componentName);
            FunctionConfigUtils.inferMissingArguments(functionConfig);
            ClassLoader clsLoader = FunctionConfigUtils.validate(functionConfig, functionPkgUrl, uploadedInputStreamAsFile);
            return FunctionConfigUtils.convert(functionConfig, clsLoader);
        }
        if (componentType.equals(SOURCE)) {
            Path archivePath = null;
            SourceConfig sourceConfig = new Gson().fromJson(componentConfigJson, SourceConfig.class);
            // The rest end points take precendence over whatever is there in sourceconfig
            sourceConfig.setTenant(tenant);
            sourceConfig.setNamespace(namespace);
            sourceConfig.setName(componentName);
            org.apache.pulsar.common.functions.Utils.inferMissingArguments(sourceConfig);
            if (!StringUtils.isEmpty(sourceConfig.getArchive())) {
                String builtinArchive = sourceConfig.getArchive();
                if (builtinArchive.startsWith(org.apache.pulsar.common.functions.Utils.BUILTIN)) {
                    builtinArchive = builtinArchive.replaceFirst("^builtin://", "");
                }
                try {
                    archivePath = this.worker().getConnectorsManager().getSourceArchive(builtinArchive);
                } catch (Exception e) {
                    throw new IllegalArgumentException(String.format("No Source archive %s found", archivePath));
                }
            }
            NarClassLoader clsLoader = SourceConfigUtils.validate(sourceConfig, archivePath, functionPkgUrl, uploadedInputStreamAsFile);
            return SourceConfigUtils.convert(sourceConfig, clsLoader);
        }
        if (componentType.equals(SINK)) {
            Path archivePath = null;
            SinkConfig sinkConfig = new Gson().fromJson(componentConfigJson, SinkConfig.class);
            // The rest end points take precendence over whatever is there in sinkConfig
            sinkConfig.setTenant(tenant);
            sinkConfig.setNamespace(namespace);
            sinkConfig.setName(componentName);
            org.apache.pulsar.common.functions.Utils.inferMissingArguments(sinkConfig);
            if (!StringUtils.isEmpty(sinkConfig.getArchive())) {
                String builtinArchive = sinkConfig.getArchive();
                if (builtinArchive.startsWith(org.apache.pulsar.common.functions.Utils.BUILTIN)) {
                    builtinArchive = builtinArchive.replaceFirst("^builtin://", "");
                }
                try {
                    archivePath = this.worker().getConnectorsManager().getSinkArchive(builtinArchive);
                } catch (Exception e) {
                    throw new IllegalArgumentException(String.format("No Sink archive %s found", archivePath));
                }
            }
            NarClassLoader clsLoader = SinkConfigUtils.validate(sinkConfig, archivePath, functionPkgUrl, uploadedInputStreamAsFile);
            return SinkConfigUtils.convert(sinkConfig, clsLoader);
        }
        FunctionDetails.Builder functionDetailsBuilder = FunctionDetails.newBuilder();
        org.apache.pulsar.functions.utils.Utils.mergeJson(functionDetailsJson, functionDetailsBuilder);
        if (isNotBlank(functionPkgUrl)) {
            // set package-url if present
            functionDetailsBuilder.setPackageUrl(functionPkgUrl);
        }
        ClassLoader clsLoader = null;
        if (functionDetailsBuilder.getRuntime() == FunctionDetails.Runtime.JAVA) {
            if (!isEmpty(functionPkgUrl)) {
                try {
                    clsLoader = extractClassLoader(functionPkgUrl);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Corrupted Jar file", e);
                }
            } else {
                try {
                    clsLoader = loadJar(uploadedInputStreamAsFile);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Corrupted Jar file", e);
                }
            }
        }
        validateFunctionClassTypes(clsLoader, functionDetailsBuilder);

        FunctionDetails functionDetails = functionDetailsBuilder.build();

        List<String> missingFields = new LinkedList<>();
        if (functionDetails.getTenant() == null || functionDetails.getTenant().isEmpty()) {
            missingFields.add("Tenant");
        }
        if (functionDetails.getNamespace() == null || functionDetails.getNamespace().isEmpty()) {
            missingFields.add("Namespace");
        }
        if (functionDetails.getName() == null || functionDetails.getName().isEmpty()) {
            missingFields.add("Name");
        }
        if (functionDetails.getClassName() == null || functionDetails.getClassName().isEmpty()) {
            missingFields.add("ClassName");
        }
        // TODO in the future add more check here for functions and connectors
        if (!functionDetails.getSource().isInitialized()) {
            missingFields.add("Source");
        }
        // TODO in the future add more check here for functions and connectors
        if (!functionDetails.getSink().isInitialized()) {
            missingFields.add("Sink");
        }
        if (!missingFields.isEmpty()) {
            String errorMessage = join(missingFields, ",");
            throw new IllegalArgumentException(errorMessage + " is not provided");
        }
        if (functionDetails.getParallelism() <= 0) {
            throw new IllegalArgumentException("Parallelism needs to be set to a positive number");
        }
        return functionDetails;
    }

    private void validateFunctionClassTypes(ClassLoader classLoader, FunctionDetails.Builder functionDetailsBuilder) {

        // validate only if classLoader is provided
        if (classLoader == null) {
            return;
        }

        if (isBlank(functionDetailsBuilder.getClassName())) {
            throw new IllegalArgumentException("function class-name can't be empty");
        }

        // validate function class-type
        Object functionObject = createInstance(functionDetailsBuilder.getClassName(), classLoader);
        Class<?>[] typeArgs = org.apache.pulsar.functions.utils.Utils.getFunctionTypes(functionObject, false);

        if (!(functionObject instanceof org.apache.pulsar.functions.api.Function)
                && !(functionObject instanceof java.util.function.Function)) {
            throw new RuntimeException("User class must either be Function or java.util.Function");
        }

        if (functionDetailsBuilder.hasSource() && functionDetailsBuilder.getSource() != null
                && isNotBlank(functionDetailsBuilder.getSource().getClassName())) {
            try {
                String sourceClassName = functionDetailsBuilder.getSource().getClassName();
                String argClassName = getTypeArg(sourceClassName, Source.class, classLoader).getName();
                functionDetailsBuilder
                        .setSource(functionDetailsBuilder.getSourceBuilder().setTypeClassName(argClassName));

                // if sink-class not present then set same arg as source
                if (!functionDetailsBuilder.hasSink() || isBlank(functionDetailsBuilder.getSink().getClassName())) {
                    functionDetailsBuilder
                            .setSink(functionDetailsBuilder.getSinkBuilder().setTypeClassName(argClassName));
                }

            } catch (IllegalArgumentException ie) {
                throw ie;
            } catch (Exception e) {
                log.error("Failed to validate source class", e);
                throw new IllegalArgumentException("Failed to validate source class-name", e);
            }
        } else if (isBlank(functionDetailsBuilder.getSourceBuilder().getTypeClassName())) {
            // if function-src-class is not present then set function-src type-class according to function class
            functionDetailsBuilder
                    .setSource(functionDetailsBuilder.getSourceBuilder().setTypeClassName(typeArgs[0].getName()));
        }

        if (functionDetailsBuilder.hasSink() && functionDetailsBuilder.getSink() != null
                && isNotBlank(functionDetailsBuilder.getSink().getClassName())) {
            try {
                String sinkClassName = functionDetailsBuilder.getSink().getClassName();
                String argClassName = getTypeArg(sinkClassName, Sink.class, classLoader).getName();
                functionDetailsBuilder.setSink(functionDetailsBuilder.getSinkBuilder().setTypeClassName(argClassName));

                // if source-class not present then set same arg as sink
                if (!functionDetailsBuilder.hasSource() || isBlank(functionDetailsBuilder.getSource().getClassName())) {
                    functionDetailsBuilder
                            .setSource(functionDetailsBuilder.getSourceBuilder().setTypeClassName(argClassName));
                }

            } catch (IllegalArgumentException ie) {
                throw ie;
            } catch (Exception e) {
                log.error("Failed to validate sink class", e);
                throw new IllegalArgumentException("Failed to validate sink class-name", e);
            }
        } else if (isBlank(functionDetailsBuilder.getSinkBuilder().getTypeClassName())) {
            // if function-sink-class is not present then set function-sink type-class according to function class
            functionDetailsBuilder
                    .setSink(functionDetailsBuilder.getSinkBuilder().setTypeClassName(typeArgs[1].getName()));
        }

    }

    private Class<?> getTypeArg(String className, Class<?> funClass, ClassLoader classLoader)
            throws ClassNotFoundException {
        Class<?> loadedClass = classLoader.loadClass(className);
        if (!funClass.isAssignableFrom(loadedClass)) {
            throw new IllegalArgumentException(
                    String.format("class %s is not type of %s", className, funClass.getName()));
        }
        return TypeResolver.resolveRawArgument(funClass, loadedClass);
    }

    private void validateTriggerRequestParams(String tenant, String namespace, String functionName, String topic,
            String input, InputStream uploadedInputStream) {
        if (tenant == null) {
            throw new IllegalArgumentException("Tenant is not provided");
        }
        if (namespace == null) {
            throw new IllegalArgumentException("Namespace is not provided");
        }
        if (functionName == null) {
            throw new IllegalArgumentException("Function Name is not provided");
        }
        if (uploadedInputStream == null && input == null) {
            throw new IllegalArgumentException("Trigger Data is not provided");
        }
    }

    private Response getUnavailableResponse() {
        return Response.status(Status.SERVICE_UNAVAILABLE).type(MediaType.APPLICATION_JSON)
                .entity(new ErrorData(
                        "Function worker service is not done initializing. " + "Please try again in a little while."))
                .build();
    }

    private Response getStateStoreUnvailableResponse() {
        return Response.status(Status.SERVICE_UNAVAILABLE).type(MediaType.APPLICATION_JSON)
                .entity(new ErrorData(
                        "State storage client is not done initializing. "
                            + "Please try again in a little while."))
                .build();
    }

    public static String createPackagePath(String tenant, String namespace, String functionName, String fileName) {
        return String.format("%s/%s/%s/%s", tenant, namespace, Codec.encode(functionName),
                Utils.getUniquePackageName(Codec.encode(fileName)));
    }

    public boolean isAuthorizedRole(String tenant, String clientRole) throws PulsarAdminException {
        if (worker().getWorkerConfig().isAuthorizationEnabled()) {
            // skip authorization if client role is super-user
            if (isSuperUser(clientRole)) {
                return true;
            }
            TenantInfo tenantInfo = worker().getBrokerAdmin().tenants().getTenantInfo(tenant);
            return clientRole != null && (tenantInfo.getAdminRoles() == null || tenantInfo.getAdminRoles().isEmpty()
                    || tenantInfo.getAdminRoles().contains(clientRole));
        }
        return true;
    }

    public boolean isSuperUser(String clientRole) {
        return clientRole != null && worker().getWorkerConfig().getSuperUserRoles().contains(clientRole);
    }

    public String calculateSubjectType(FunctionMetaData functionMetaData) {
        SourceSpec sourceSpec = functionMetaData.getFunctionDetails().getSource();
        SinkSpec sinkSpec = functionMetaData.getFunctionDetails().getSink();
        if (sourceSpec.getInputSpecsCount() == 0) {
            return SOURCE;
        }
        // Now its between sink and function

        if (!isEmpty(sinkSpec.getBuiltin())) {
            // if its built in, its a sink
            return SINK;
        }

        if (isEmpty(sinkSpec.getClassName()) || sinkSpec.getClassName().equals(PulsarSink.class.getName())) {
            return FUNCTION;
        }
        return SINK;
    }

}
