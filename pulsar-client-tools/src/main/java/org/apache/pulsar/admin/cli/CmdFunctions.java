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
package org.apache.pulsar.admin.cli;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.bookkeeper.common.concurrent.FutureUtils.result;
import static org.apache.commons.lang.StringUtils.isBlank;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.apache.pulsar.common.naming.TopicName.DEFAULT_NAMESPACE;
import static org.apache.pulsar.common.naming.TopicName.PUBLIC_TENANT;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.converters.StringConverter;
import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import com.google.protobuf.util.JsonFormat;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.apache.bookkeeper.api.StorageClient;
import org.apache.bookkeeper.api.kv.Table;
import org.apache.bookkeeper.api.kv.result.KeyValue;
import org.apache.bookkeeper.clients.StorageClientBuilder;
import org.apache.bookkeeper.clients.config.StorageClientSettings;
import org.apache.commons.lang.StringUtils;
import org.apache.pulsar.admin.cli.utils.CmdUtils;
import org.apache.pulsar.client.admin.PulsarAdmin;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.common.functions.FunctionConfig;
import org.apache.pulsar.common.functions.Resources;
import org.apache.pulsar.common.functions.WindowConfig;
import org.apache.pulsar.common.functions.Utils;

@Slf4j
@Parameters(commandDescription = "Interface for managing Pulsar Functions (lightweight, Lambda-style compute processes that work with Pulsar)")
public class CmdFunctions extends CmdBase {
    private final LocalRunner localRunner;
    private final CreateFunction creater;
    private final DeleteFunction deleter;
    private final UpdateFunction updater;
    private final GetFunction getter;
    private final GetFunctionStatus functionStatus;
    @Getter
    private final GetFunctionStats functionStats;
    private final RestartFunction restart;
    private final StopFunction stop;
    private final ListFunctions lister;
    private final StateGetter stateGetter;
    private final TriggerFunction triggerer;
    private final UploadFunction uploader;
    private final DownloadFunction downloader;

    /**
     * Base command
     */
    @Getter
    abstract class BaseCommand extends CliCommand {
        @Override
        void run() throws Exception {
            processArguments();
            runCmd();
        }

        void processArguments() throws Exception {}

        abstract void runCmd() throws Exception;
    }

    /**
     * Namespace level command
     */
    @Getter
    abstract class NamespaceCommand extends BaseCommand {
        @Parameter(names = "--tenant", description = "The function's tenant")
        protected String tenant;

        @Parameter(names = "--namespace", description = "The function's namespace")
        protected String namespace;

        @Override
        public void processArguments() {
            if (tenant == null) {
                tenant = PUBLIC_TENANT;
            }
            if (namespace == null) {
                namespace = DEFAULT_NAMESPACE;
            }
        }
    }

    /**
     * Function level command
     */
    @Getter
    abstract class FunctionCommand extends BaseCommand {
        @Parameter(names = "--fqfn", description = "The Fully Qualified Function Name (FQFN) for the function")
        protected String fqfn;

        @Parameter(names = "--tenant", description = "The function's tenant")
        protected String tenant;

        @Parameter(names = "--namespace", description = "The function's namespace")
        protected String namespace;

        @Parameter(names = "--name", description = "The function's name")
        protected String functionName;

        @Override
        void processArguments() throws Exception {
            super.processArguments();

            boolean usesSetters = (null != tenant || null != namespace || null != functionName);
            boolean usesFqfn = (null != fqfn);

            // Throw an exception if --fqfn is set alongside any combination of --tenant, --namespace, and --name
            if (usesFqfn && usesSetters) {
                throw new RuntimeException(
                        "You must specify either a Fully Qualified Function Name (FQFN) or tenant, namespace, and function name");
            } else if (usesFqfn) {
                // If the --fqfn flag is used, parse tenant, namespace, and name using that flag
                String[] fqfnParts = fqfn.split("/");
                if (fqfnParts.length != 3) {
                    throw new RuntimeException(
                            "Fully qualified function names (FQFNs) must be of the form tenant/namespace/name");
                }
                tenant = fqfnParts[0];
                namespace = fqfnParts[1];
                functionName = fqfnParts[2];
            } else {
                if (tenant == null) {
                    tenant = PUBLIC_TENANT;
                }
                if (namespace == null) {
                    namespace = DEFAULT_NAMESPACE;
                }
                if (null == functionName) {
                    throw new RuntimeException(
                            "You must specify a name for the function or a Fully Qualified Function Name (FQFN)");
                }
            }
        }
    }

    /**
     * Commands that require a function config
     */
    @Getter
    abstract class FunctionDetailsCommand extends BaseCommand {
        @Parameter(names = "--fqfn", description = "The Fully Qualified Function Name (FQFN) for the function")
        protected String fqfn;
        @Parameter(names = "--tenant", description = "The function's tenant")
        protected String tenant;
        @Parameter(names = "--namespace", description = "The function's namespace")
        protected String namespace;
        @Parameter(names = "--name", description = "The function's name")
        protected String functionName;
        // for backwards compatibility purposes
        @Parameter(names = "--className", description = "The function's class name", hidden = true)
        protected String DEPRECATED_className;
        @Parameter(names = "--classname", description = "The function's class name")
        protected String className;
        @Parameter(names = "--jar", description = "Path to the jar file for the function (if the function is written in Java). It also supports url-path [http/https/file (file protocol assumes that file already exists on worker host)] from which worker can download the package.", listConverter = StringConverter.class)
        protected String jarFile;
        @Parameter(
                names = "--py",
                description = "Path to the main Python file/Python Wheel file for the function (if the function is written in Python)",
                listConverter = StringConverter.class)
        protected String pyFile;
        @Parameter(names = {"-i",
                "--inputs"}, description = "The function's input topic or topics (multiple topics can be specified as a comma-separated list)")
        protected String inputs;
        // for backwards compatibility purposes
        @Parameter(names = "--topicsPattern", description = "TopicsPattern to consume from list of topics under a namespace that match the pattern. [--input] and [--topic-pattern] are mutually exclusive. Add SerDe class name for a pattern in --custom-serde-inputs (supported for java fun only)", hidden = true)
        protected String DEPRECATED_topicsPattern;
        @Parameter(names = "--topics-pattern", description = "The topic pattern to consume from list of topics under a namespace that match the pattern. [--input] and [--topic-pattern] are mutually exclusive. Add SerDe class name for a pattern in --custom-serde-inputs (supported for java fun only)")
        protected String topicsPattern;

        @Parameter(names = {"-o", "--output"}, description = "The function's output topic (If none is specified, no output is written)")
        protected String output;
        // for backwards compatibility purposes
        @Parameter(names = "--logTopic", description = "The topic to which the function's logs are produced", hidden = true)
        protected String DEPRECATED_logTopic;
        @Parameter(names = "--log-topic", description = "The topic to which the function's logs are produced")
        protected String logTopic;

        @Parameter(names = {"-st", "--schema-type"}, description = "The builtin schema type or custom schema class name to be used for messages output by the function")
        protected String schemaType = "";

        // for backwards compatibility purposes
        @Parameter(names = "--customSerdeInputs", description = "The map of input topics to SerDe class names (as a JSON string)", hidden = true)
        protected String DEPRECATED_customSerdeInputString;
        @Parameter(names = "--custom-serde-inputs", description = "The map of input topics to SerDe class names (as a JSON string)")
        protected String customSerdeInputString;
        @Parameter(names = "--custom-schema-inputs", description = "The map of input topics to Schema class names (as a JSON string)")
        protected String customSchemaInputString;
        // for backwards compatibility purposes
        @Parameter(names = "--outputSerdeClassName", description = "The SerDe class to be used for messages output by the function", hidden = true)
        protected String DEPRECATED_outputSerdeClassName;
        @Parameter(names = "--output-serde-classname", description = "The SerDe class to be used for messages output by the function")
        protected String outputSerdeClassName;
        // for backwards compatibility purposes
        @Parameter(names = "--functionConfigFile", description = "The path to a YAML config file specifying the function's configuration", hidden = true)
        protected String DEPRECATED_fnConfigFile;
        @Parameter(names = "--function-config-file", description = "The path to a YAML config file specifying the function's configuration")
        protected String fnConfigFile;
        // for backwards compatibility purposes
        @Parameter(names = "--processingGuarantees", description = "The processing guarantees (aka delivery semantics) applied to the function", hidden = true)
        protected FunctionConfig.ProcessingGuarantees DEPRECATED_processingGuarantees;
        @Parameter(names = "--processing-guarantees", description = "The processing guarantees (aka delivery semantics) applied to the function")
        protected FunctionConfig.ProcessingGuarantees processingGuarantees;
        // for backwards compatibility purposes
        @Parameter(names = "--userConfig", description = "User-defined config key/values", hidden = true)
        protected String DEPRECATED_userConfigString;
        @Parameter(names = "--user-config", description = "User-defined config key/values")
        protected String userConfigString;
        @Parameter(names = "--retainOrdering", description = "Function consumes and processes messages in order", hidden = true)
        protected Boolean DEPRECATED_retainOrdering;
        @Parameter(names = "--retain-ordering", description = "Function consumes and processes messages in order")
        protected boolean retainOrdering;
        @Parameter(names = "--subs-name", description = "Pulsar source subscription name if user wants a specific subscription-name for input-topic consumer")
        protected String subsName;
        @Parameter(names = "--parallelism", description = "The function's parallelism factor (i.e. the number of function instances to run)")
        protected Integer parallelism;
        @Parameter(names = "--cpu", description = "The cpu in cores that need to be allocated per function instance(applicable only to docker runtime)")
        protected Double cpu;
        @Parameter(names = "--ram", description = "The ram in bytes that need to be allocated per function instance(applicable only to process/docker runtime)")
        protected Long ram;
        @Parameter(names = "--disk", description = "The disk in bytes that need to be allocated per function instance(applicable only to docker runtime)")
        protected Long disk;
        // for backwards compatibility purposes
        @Parameter(names = "--windowLengthCount", description = "The number of messages per window", hidden = true)
        protected Integer DEPRECATED_windowLengthCount;
        @Parameter(names = "--window-length-count", description = "The number of messages per window")
        protected Integer windowLengthCount;
        // for backwards compatibility purposes
        @Parameter(names = "--windowLengthDurationMs", description = "The time duration of the window in milliseconds", hidden = true)
        protected Long DEPRECATED_windowLengthDurationMs;
        @Parameter(names = "--window-length-duration-ms", description = "The time duration of the window in milliseconds")
        protected Long windowLengthDurationMs;
        // for backwards compatibility purposes
        @Parameter(names = "--slidingIntervalCount", description = "The number of messages after which the window slides", hidden = true)
        protected Integer DEPRECATED_slidingIntervalCount;
        @Parameter(names = "--sliding-interval-count", description = "The number of messages after which the window slides")
        protected Integer slidingIntervalCount;
        // for backwards compatibility purposes
        @Parameter(names = "--slidingIntervalDurationMs", description = "The time duration after which the window slides", hidden = true)
        protected Long DEPRECATED_slidingIntervalDurationMs;
        @Parameter(names = "--sliding-interval-duration-ms", description = "The time duration after which the window slides")
        protected Long slidingIntervalDurationMs;
        // for backwards compatibility purposes
        @Parameter(names = "--autoAck", description = "Whether or not the framework will automatically acknowleges messages", hidden = true)
        protected Boolean DEPRECATED_autoAck = null;
        @Parameter(names = "--auto-ack", description = "Whether or not the framework will automatically acknowleges messages", arity = 1)
        protected boolean autoAck = true;
        // for backwards compatibility purposes
        @Parameter(names = "--timeoutMs", description = "The message timeout in milliseconds", hidden = true)
        protected Long DEPRECATED_timeoutMs;
        @Parameter(names = "--timeout-ms", description = "The message timeout in milliseconds")
        protected Long timeoutMs;
        @Parameter(names = "--max-message-retries", description = "How many times should we try to process a message before giving up")
        protected Integer maxMessageRetries = -1;
        @Parameter(names = "--dead-letter-topic", description = "The topic where all messages which could not be processed successfully are sent")
        protected String deadLetterTopic;
        protected FunctionConfig functionConfig;
        protected String userCodeFile;

        private void mergeArgs() {
            if (!StringUtils.isBlank(DEPRECATED_className)) className = DEPRECATED_className;
            if (!StringUtils.isBlank(DEPRECATED_topicsPattern)) topicsPattern = DEPRECATED_topicsPattern;
            if (!StringUtils.isBlank(DEPRECATED_logTopic)) logTopic = DEPRECATED_logTopic;
            if (!StringUtils.isBlank(DEPRECATED_outputSerdeClassName)) outputSerdeClassName = DEPRECATED_outputSerdeClassName;
            if (!StringUtils.isBlank(DEPRECATED_customSerdeInputString)) customSerdeInputString = DEPRECATED_customSerdeInputString;

            if (!StringUtils.isBlank(DEPRECATED_fnConfigFile)) fnConfigFile = DEPRECATED_fnConfigFile;
            if (DEPRECATED_processingGuarantees != null) processingGuarantees = DEPRECATED_processingGuarantees;
            if (!StringUtils.isBlank(DEPRECATED_userConfigString)) userConfigString = DEPRECATED_userConfigString;
            if (DEPRECATED_retainOrdering != null) retainOrdering = DEPRECATED_retainOrdering;
            if (DEPRECATED_windowLengthCount != null) windowLengthCount = DEPRECATED_windowLengthCount;
            if (DEPRECATED_windowLengthDurationMs != null) windowLengthDurationMs = DEPRECATED_windowLengthDurationMs;
            if (DEPRECATED_slidingIntervalCount != null) slidingIntervalCount = DEPRECATED_slidingIntervalCount;
            if (DEPRECATED_slidingIntervalDurationMs != null) slidingIntervalDurationMs = DEPRECATED_slidingIntervalDurationMs;
            if (DEPRECATED_autoAck != null) autoAck = DEPRECATED_autoAck;
            if (DEPRECATED_timeoutMs != null) timeoutMs = DEPRECATED_timeoutMs;
        }

        @Override
        void processArguments() throws Exception {
            super.processArguments();
            // merge deprecated args with new args
            mergeArgs();

            // Initialize config builder either from a supplied YAML config file or from scratch
            if (null != fnConfigFile) {
                functionConfig = CmdUtils.loadConfig(fnConfigFile, FunctionConfig.class);
            } else {
                functionConfig = new FunctionConfig();
            }

            if (null != fqfn) {
                parseFullyQualifiedFunctionName(fqfn, functionConfig);
            } else {
                if (null != tenant) {
                    functionConfig.setTenant(tenant);
                }
                if (null != namespace) {
                    functionConfig.setNamespace(namespace);
                }
                if (null != functionName) {
                    functionConfig.setName(functionName);
                }
            }

            if (null != inputs) {
                List<String> inputTopics = Arrays.asList(inputs.split(","));
                functionConfig.setInputs(inputTopics);
            }
            if (null != customSerdeInputString) {
                Type type = new TypeToken<Map<String, String>>() {}.getType();
                Map<String, String> customSerdeInputMap = new Gson().fromJson(customSerdeInputString, type);
                functionConfig.setCustomSerdeInputs(customSerdeInputMap);
            }
            if (null != customSchemaInputString) {
                Type type = new TypeToken<Map<String, String>>() {}.getType();
                Map<String, String> customschemaInputMap = new Gson().fromJson(customSchemaInputString, type);
                functionConfig.setCustomSchemaInputs(customschemaInputMap);
            }
            if (null != topicsPattern) {
                functionConfig.setTopicsPattern(topicsPattern);
            }
            if (null != output) {
                functionConfig.setOutput(output);
            }
            if (null != logTopic) {
                functionConfig.setLogTopic(logTopic);
            }
            if (null != className) {
                functionConfig.setClassName(className);
            }
            if (null != outputSerdeClassName) {
                functionConfig.setOutputSerdeClassName(outputSerdeClassName);
            }

            if (null != schemaType) {
                functionConfig.setOutputSchemaType(schemaType);
            }
            if (null != processingGuarantees) {
                functionConfig.setProcessingGuarantees(processingGuarantees);
            }

            functionConfig.setRetainOrdering(retainOrdering);

            if (isNotBlank(subsName)) {
                functionConfig.setSubName(subsName);
            }

            if (null != userConfigString) {
                Type type = new TypeToken<Map<String, String>>() {}.getType();
                Map<String, Object> userConfigMap = new Gson().fromJson(userConfigString, type);
                functionConfig.setUserConfig(userConfigMap);
            }
            if (functionConfig.getUserConfig() == null) {
                functionConfig.setUserConfig(new HashMap<>());
            }

            if (parallelism != null) {
                functionConfig.setParallelism(parallelism);
            }

            Resources resources = functionConfig.getResources();
            if (resources == null) {
                resources = new Resources();
            }
            if (cpu != null) {
                resources.setCpu(cpu);
            }

            if (ram != null) {
                resources.setRam(ram);
            }

            if (disk != null) {
                resources.setDisk(disk);
            }
            functionConfig.setResources(resources);

            if (timeoutMs != null) {
                functionConfig.setTimeoutMs(timeoutMs);
            }

            // window configs
            WindowConfig windowConfig = functionConfig.getWindowConfig();
            if (null != windowLengthCount) {
                if (windowConfig == null) {
                    windowConfig = new WindowConfig();
                }
                windowConfig.setWindowLengthCount(windowLengthCount);
            }
            if (null != windowLengthDurationMs) {
                if (windowConfig == null) {
                    windowConfig = new WindowConfig();
                }
                windowConfig.setWindowLengthDurationMs(windowLengthDurationMs);
            }
            if (null != slidingIntervalCount) {
                if (windowConfig == null) {
                    windowConfig = new WindowConfig();
                }
                windowConfig.setSlidingIntervalCount(slidingIntervalCount);
            }
            if (null != slidingIntervalDurationMs) {
                if (windowConfig == null) {
                    windowConfig = new WindowConfig();
                }
                windowConfig.setSlidingIntervalDurationMs(slidingIntervalDurationMs);
            }

            functionConfig.setWindowConfig(windowConfig);

            functionConfig.setAutoAck(autoAck);

            if (null != maxMessageRetries) {
                functionConfig.setMaxMessageRetries(maxMessageRetries);
            }
            if (null != deadLetterTopic) {
                functionConfig.setDeadLetterTopic(deadLetterTopic);
            }

            if (null != jarFile) {
                functionConfig.setJar(jarFile);
            }

            if (null != pyFile) {
                functionConfig.setPy(pyFile);
            }

            if (functionConfig.getJar() != null) {
                userCodeFile = functionConfig.getJar();
            } else if (functionConfig.getPy() != null) {
                userCodeFile = functionConfig.getPy();
            }

            // check if configs are valid
            validateFunctionConfigs(functionConfig);
        }

        protected void validateFunctionConfigs(FunctionConfig functionConfig) {
            if (StringUtils.isEmpty(functionConfig.getClassName())) {
                throw new IllegalArgumentException("No Function Classname specified");
            }
            if (StringUtils.isEmpty(functionConfig.getName())) {
                org.apache.pulsar.common.functions.Utils.inferMissingFunctionName(functionConfig);
            }
            if (StringUtils.isEmpty(functionConfig.getTenant())) {
                org.apache.pulsar.common.functions.Utils.inferMissingTenant(functionConfig);
            }
            if (StringUtils.isEmpty(functionConfig.getNamespace())) {
                org.apache.pulsar.common.functions.Utils.inferMissingNamespace(functionConfig);
            }

            if (isNotBlank(functionConfig.getJar()) && isNotBlank(functionConfig.getPy())) {
                throw new ParameterException("Either a Java jar or a Python file needs to"
                        + " be specified for the function. Cannot specify both.");
            }

            if (isBlank(functionConfig.getJar()) && isBlank(functionConfig.getPy())) {
                throw new ParameterException("Either a Java jar or a Python file needs to"
                        + " be specified for the function. Please specify one.");
            }

            if (!isBlank(functionConfig.getJar()) && !Utils.isFunctionPackageUrlSupported(functionConfig.getJar()) &&
                    !new File(functionConfig.getJar()).exists()) {
                throw new ParameterException("The specified jar file does not exist");
            }
            if (!isBlank(functionConfig.getPy()) && !Utils.isFunctionPackageUrlSupported(functionConfig.getPy()) &&
                    !new File(functionConfig.getPy()).exists()) {
                throw new ParameterException("The specified python file does not exist");
            }
        }
    }

    @Parameters(commandDescription = "Run the Pulsar Function locally (rather than deploying it to the Pulsar cluster)")
    class LocalRunner extends FunctionDetailsCommand {

        // TODO: this should become bookkeeper url and it should be fetched from pulsar client.
        // for backwards compatibility purposes
        @Parameter(names = "--stateStorageServiceUrl", description = "The URL for the state storage service (by default Apache BookKeeper)", hidden = true)
        protected String DEPRECATED_stateStorageServiceUrl;
        @Parameter(names = "--state-storage-service-url", description = "The URL for the state storage service (by default Apache BookKeeper)")
        protected String stateStorageServiceUrl;
        // for backwards compatibility purposes
        @Parameter(names = "--brokerServiceUrl", description = "The URL for the Pulsar broker", hidden = true)
        protected String DEPRECATED_brokerServiceUrl;
        @Parameter(names = "--broker-service-url", description = "The URL for the Pulsar broker")
        protected String brokerServiceUrl;
        // for backwards compatibility purposes
        @Parameter(names = "--clientAuthPlugin", description = "Client authentication plugin using which function-process can connect to broker", hidden = true)
        protected String DEPRECATED_clientAuthPlugin;
        @Parameter(names = "--client-auth-plugin", description = "Client authentication plugin using which function-process can connect to broker")
        protected String clientAuthPlugin;
        // for backwards compatibility purposes
        @Parameter(names = "--clientAuthParams", description = "Client authentication param", hidden = true)
        protected String DEPRECATED_clientAuthParams;
        @Parameter(names = "--client-auth-params", description = "Client authentication param")
        protected String clientAuthParams;
        // for backwards compatibility purposes
        @Parameter(names = "--use_tls", description = "Use tls connection\n", hidden = true)
        protected Boolean DEPRECATED_useTls = null;
        @Parameter(names = "--use-tls", description = "Use tls connection\n")
        protected boolean useTls;
        // for backwards compatibility purposes
        @Parameter(names = "--tls_allow_insecure", description = "Allow insecure tls connection\n", hidden = true)
        protected Boolean DEPRECATED_tlsAllowInsecureConnection = null;
        @Parameter(names = "--tls-allow-insecure", description = "Allow insecure tls connection\n")
        protected boolean tlsAllowInsecureConnection;
        // for backwards compatibility purposes
        @Parameter(names = "--hostname_verification_enabled", description = "Enable hostname verification", hidden = true)
        protected Boolean DEPRECATED_tlsHostNameVerificationEnabled = null;
        @Parameter(names = "--hostname-verification-enabled", description = "Enable hostname verification")
        protected boolean tlsHostNameVerificationEnabled;
        // for backwards compatibility purposes
        @Parameter(names = "--tls_trust_cert_path", description = "tls trust cert file path", hidden = true)
        protected String DEPRECATED_tlsTrustCertFilePath;
        @Parameter(names = "--tls-trust-cert-path", description = "tls trust cert file path")
        protected String tlsTrustCertFilePath;
        // for backwards compatibility purposes
        @Parameter(names = "--instanceIdOffset", description = "Start the instanceIds from this offset", hidden = true)
        protected Integer DEPRECATED_instanceIdOffset = null;
        @Parameter(names = "--instance-id-offset", description = "Start the instanceIds from this offset")
        protected Integer instanceIdOffset = 0;

        private void mergeArgs() {
            if (!StringUtils.isBlank(DEPRECATED_stateStorageServiceUrl)) stateStorageServiceUrl = DEPRECATED_stateStorageServiceUrl;
            if (!StringUtils.isBlank(DEPRECATED_brokerServiceUrl)) brokerServiceUrl = DEPRECATED_brokerServiceUrl;
            if (!StringUtils.isBlank(DEPRECATED_clientAuthPlugin)) clientAuthPlugin = DEPRECATED_clientAuthPlugin;
            if (!StringUtils.isBlank(DEPRECATED_clientAuthParams)) clientAuthParams = DEPRECATED_clientAuthParams;
            if (DEPRECATED_useTls != null) useTls = DEPRECATED_useTls;
            if (DEPRECATED_tlsAllowInsecureConnection != null) tlsAllowInsecureConnection = DEPRECATED_tlsAllowInsecureConnection;
            if (DEPRECATED_tlsHostNameVerificationEnabled != null) tlsHostNameVerificationEnabled = DEPRECATED_tlsHostNameVerificationEnabled;
            if (!StringUtils.isBlank(DEPRECATED_tlsTrustCertFilePath)) tlsTrustCertFilePath = DEPRECATED_tlsTrustCertFilePath;
            if (DEPRECATED_instanceIdOffset != null) instanceIdOffset = DEPRECATED_instanceIdOffset;
        }

        @Override
        void runCmd() throws Exception {
            // merge deprecated args with new args
            mergeArgs();
            List<String> localRunArgs = new LinkedList<>();
            localRunArgs.add(System.getenv("PULSAR_HOME") + "/bin/function-localrunner");
            localRunArgs.add("--functionConfig");
            localRunArgs.add(new Gson().toJson(functionConfig));
            for (Field field : this.getClass().getDeclaredFields()) {
                if (field.getName().startsWith("DEPRECATED")) continue;
                if(field.getName().contains("$")) continue;
                Object value = field.get(this);
                if (value != null) {
                    localRunArgs.add("--" + field.getName());
                    localRunArgs.add(value.toString());
                }
            }
            ProcessBuilder processBuilder = new ProcessBuilder(localRunArgs).inheritIO();
            Process process = processBuilder.start();
            process.waitFor();
        }
    }

    @Parameters(commandDescription = "Create a Pulsar Function in cluster mode (i.e. deploy it on a Pulsar cluster)")
    class CreateFunction extends FunctionDetailsCommand {
        @Override
        void runCmd() throws Exception {
            if (Utils.isFunctionPackageUrlSupported(functionConfig.getJar())) {
                admin.functions().createFunctionWithUrl(functionConfig, functionConfig.getJar());
            } else {
                admin.functions().createFunction(functionConfig, userCodeFile);
            }

            print("Created successfully");
        }
    }

    @Parameters(commandDescription = "Fetch information about a Pulsar Function")
    class GetFunction extends FunctionCommand {
        @Override
        void runCmd() throws Exception {
            FunctionConfig functionConfig = admin.functions().getFunction(tenant, namespace, functionName);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            System.out.println(gson.toJson(functionConfig));
        }
    }

    @Parameters(commandDescription = "Check the current status of a Pulsar Function")
    class GetFunctionStatus extends FunctionCommand {

        @Parameter(names = "--instance-id", description = "The function instanceId (Get-status of all instances if instance-id is not provided")
        protected String instanceId;

        @Override
        void runCmd() throws Exception {
            String json = JsonFormat.printer().print(
                    isBlank(instanceId) ? admin.functions().getFunctionStatus(tenant, namespace, functionName)
                            : admin.functions().getFunctionStatus(tenant, namespace, functionName,
                                    Integer.parseInt(instanceId)));
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            System.out.println(gson.toJson(new JsonParser().parse(json)));
        }
    }

    @Parameters(commandDescription = "Get the current stats of a Pulsar Function")
    class GetFunctionStats extends FunctionCommand {

        @Parameter(names = "--instance-id", description = "The function instanceId (Get-status of all instances if instance-id is not provided")
        protected String instanceId;

        @Override
        void runCmd() throws Exception {

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            if (isBlank(instanceId)) {
                System.out.println(gson.toJson(admin.functions().getFunctionStats(tenant, namespace, functionName)));
            } else {
                System.out.println(gson.toJson(admin.functions().getFunctionStats(tenant, namespace, functionName, Integer.parseInt(instanceId))));
            }
        }
    }

    @Parameters(commandDescription = "Restart function instance")
    class RestartFunction extends FunctionCommand {

        @Parameter(names = "--instance-id", description = "The function instanceId (restart all instances if instance-id is not provided")
        protected String instanceId;

        @Override
        void runCmd() throws Exception {
            if (isNotBlank(instanceId)) {
                try {
                    admin.functions().restartFunction(tenant, namespace, functionName, Integer.parseInt(instanceId));
                } catch (NumberFormatException e) {
                    System.err.println("instance-id must be a number");
                }
            } else {
                admin.functions().restartFunction(tenant, namespace, functionName);
            }
            System.out.println("Restarted successfully");
        }
    }

    @Parameters(commandDescription = "Temporary stops function instance. (If worker restarts then it reassigns and starts functiona again")
    class StopFunction extends FunctionCommand {
        
        @Parameter(names = "--instance-id", description = "The function instanceId (stop all instances if instance-id is not provided")
        protected String instanceId;
        
        @Override
        void runCmd() throws Exception {
            if (isNotBlank(instanceId)) {
                try {
                    admin.functions().stopFunction(tenant, namespace, functionName, Integer.parseInt(instanceId));
                } catch (NumberFormatException e) {
                    System.err.println("instance-id must be a number");
                }
            } else {
                admin.functions().stopFunction(tenant, namespace, functionName);
            }
            System.out.println("Restarted successfully");
        }
    }

    @Parameters(commandDescription = "Delete a Pulsar Function that's running on a Pulsar cluster")
    class DeleteFunction extends FunctionCommand {
        @Override
        void runCmd() throws Exception {
            admin.functions().deleteFunction(tenant, namespace, functionName);
            print("Deleted successfully");
        }
    }

    @Parameters(commandDescription = "Update a Pulsar Function that's been deployed to a Pulsar cluster")
    class UpdateFunction extends FunctionDetailsCommand {
        @Override
        void runCmd() throws Exception {
            if (Utils.isFunctionPackageUrlSupported(functionConfig.getJar())) {
                admin.functions().updateFunctionWithUrl(functionConfig, functionConfig.getJar());
            } else {
                admin.functions().updateFunction(functionConfig, userCodeFile);
            }
            print("Updated successfully");
        }
    }

    @Parameters(commandDescription = "List all of the Pulsar Functions running under a specific tenant and namespace")
    class ListFunctions extends NamespaceCommand {
        @Override
        void runCmd() throws Exception {
            print(admin.functions().getFunctions(tenant, namespace));
        }
    }

    @Parameters(commandDescription = "Fetch the current state associated with a Pulsar Function running in cluster mode")
    class StateGetter extends FunctionCommand {

        @Parameter(names = { "-k", "--key" }, description = "key")
        private String key = null;

        // TODO: this url should be fetched along with bookkeeper location from pulsar admin
        @Parameter(names = { "-u", "--storage-service-url" }, description = "The URL for the storage service used by the function")
        private String stateStorageServiceUrl = null;

        @Parameter(names = { "-w", "--watch" }, description = "Watch for changes in the value associated with a key for a Pulsar Function")
        private boolean watch = false;

        @Override
        void runCmd() throws Exception {
            checkNotNull(stateStorageServiceUrl, "The state storage service URL is missing");

            String tableNs = String.format(
                "%s_%s",
                tenant,
                namespace).replace('-', '_');

            String tableName = getFunctionName();

            try (StorageClient client = StorageClientBuilder.newBuilder()
                 .withSettings(StorageClientSettings.newBuilder()
                     .serviceUri(stateStorageServiceUrl)
                     .clientName("functions-admin")
                     .build())
                 .withNamespace(tableNs)
                 .build()) {
                try (Table<ByteBuf, ByteBuf> table = result(client.openTable(tableName))) {
                    long lastVersion = -1L;
                    do {
                        try (KeyValue<ByteBuf, ByteBuf> kv = result(table.getKv(Unpooled.wrappedBuffer(key.getBytes(UTF_8))))) {
                            if (null == kv) {
                                System.out.println("key '" + key + "' doesn't exist.");
                            } else {
                                if (kv.version() > lastVersion) {
                                    if (kv.isNumber()) {
                                        System.out.println("value = " + kv.numberValue());
                                    } else {
                                        System.out.println("value = " + new String(ByteBufUtil.getBytes(kv.value()), UTF_8));
                                    }
                                    lastVersion = kv.version();
                                }
                            }
                        }
                        if (watch) {
                            Thread.sleep(1000);
                        }
                    } while (watch);
                }
            }

        }
    }

    @Parameters(commandDescription = "Triggers the specified Pulsar Function with a supplied value")
    class TriggerFunction extends FunctionCommand {
        // for backward compatibility purposes
        @Parameter(names = "--triggerValue", description = "The value with which you want to trigger the function", hidden = true)
        protected String DEPRECATED_triggerValue;
        @Parameter(names = "--trigger-value", description = "The value with which you want to trigger the function")
        protected String triggerValue;
        // for backward compatibility purposes
        @Parameter(names = "--triggerFile", description = "The path to the file that contains the data with which you'd like to trigger the function", hidden = true)
        protected String DEPRECATED_triggerFile;
        @Parameter(names = "--trigger-file", description = "The path to the file that contains the data with which you'd like to trigger the function")
        protected String triggerFile;
        @Parameter(names = "--topic", description = "The specific topic name that the function consumes from that you want to inject the data to")
        protected String topic;

        public void mergeArgs() {
            if (!StringUtils.isBlank(DEPRECATED_triggerValue)) triggerValue = DEPRECATED_triggerValue;
            if (!StringUtils.isBlank(DEPRECATED_triggerFile)) triggerFile = DEPRECATED_triggerFile;
        }

        @Override
        void runCmd() throws Exception {
            // merge deprecated args with new args
            mergeArgs();
            if (triggerFile == null && triggerValue == null) {
                throw new ParameterException("Either a trigger value or a trigger filepath needs to be specified");
            }
            String retval = admin.functions().triggerFunction(tenant, namespace, functionName, topic, triggerValue, triggerFile);
            System.out.println(retval);
        }
    }

    @Parameters(commandDescription = "Upload File Data to Pulsar", hidden = true)
    class UploadFunction extends BaseCommand {
        // for backward compatibility purposes
        @Parameter(
                names = "--sourceFile",
                description = "The file whose contents need to be uploaded",
                listConverter = StringConverter.class, hidden = true)
        protected String DEPRECATED_sourceFile;
        @Parameter(
                names = "--source-file",
                description = "The file whose contents need to be uploaded",
                listConverter = StringConverter.class)
        protected String sourceFile;
        @Parameter(
                names = "--path",
                description = "Path where the contents need to be stored",
                listConverter = StringConverter.class, required = true)
        protected String path;

        private void mergeArgs() {
            if (!StringUtils.isBlank(DEPRECATED_sourceFile)) sourceFile = DEPRECATED_sourceFile;
        }

        @Override
        void runCmd() throws Exception {
            // merge deprecated args with new args
            mergeArgs();
            if (StringUtils.isBlank(sourceFile)) {
                throw new ParameterException("--source-file needs to be specified");
            }
            admin.functions().uploadFunction(sourceFile, path);
            print("Uploaded successfully");
        }
    }

    @Parameters(commandDescription = "Download File Data from Pulsar", hidden = true)
    class DownloadFunction extends BaseCommand {
        // for backward compatibility purposes
        @Parameter(
                names = "--destinationFile",
                description = "The file where downloaded contents need to be stored",
                listConverter = StringConverter.class, hidden = true)
        protected String DEPRECATED_destinationFile;
        @Parameter(
                names = "--destination-file",
                description = "The file where downloaded contents need to be stored",
                listConverter = StringConverter.class)
        protected String destinationFile;
        @Parameter(
                names = "--path",
                description = "Path where the contents are to be stored",
                listConverter = StringConverter.class, required = true)
        protected String path;

        private void mergeArgs() {
            if (!StringUtils.isBlank(DEPRECATED_destinationFile)) destinationFile = DEPRECATED_destinationFile;
        }

        @Override
        void runCmd() throws Exception {
            // merge deprecated args with new args
            mergeArgs();
            if (StringUtils.isBlank(destinationFile)) {
                throw new ParameterException("--destination-file needs to be specified");
            }
            admin.functions().downloadFunction(destinationFile, path);
            print("Downloaded successfully");
        }
    }

    public CmdFunctions(PulsarAdmin admin) throws PulsarClientException {
        super("functions", admin);
        localRunner = new LocalRunner();
        creater = new CreateFunction();
        deleter = new DeleteFunction();
        updater = new UpdateFunction();
        getter = new GetFunction();
        functionStatus = new GetFunctionStatus();
        functionStats = new GetFunctionStats();
        lister = new ListFunctions();
        stateGetter = new StateGetter();
        triggerer = new TriggerFunction();
        uploader = new UploadFunction();
        downloader = new DownloadFunction();
        restart = new RestartFunction();
        stop = new StopFunction();
        jcommander.addCommand("localrun", getLocalRunner());
        jcommander.addCommand("create", getCreater());
        jcommander.addCommand("delete", getDeleter());
        jcommander.addCommand("update", getUpdater());
        jcommander.addCommand("get", getGetter());
        jcommander.addCommand("restart", getRestarter());
        jcommander.addCommand("stop", getStopper());
        jcommander.addCommand("getstatus", getStatuser());
        jcommander.addCommand("stats", getFunctionStats());
        jcommander.addCommand("list", getLister());
        jcommander.addCommand("querystate", getStateGetter());
        jcommander.addCommand("trigger", getTriggerer());
        jcommander.addCommand("upload", getUploader());
        jcommander.addCommand("download", getDownloader());
    }

    @VisibleForTesting
    LocalRunner getLocalRunner() {
        return localRunner;
    }

    @VisibleForTesting
    CreateFunction getCreater() {
        return creater;
    }

    @VisibleForTesting
    DeleteFunction getDeleter() {
        return deleter;
    }

    @VisibleForTesting
    UpdateFunction getUpdater() {
        return updater;
    }

    @VisibleForTesting
    GetFunction getGetter() {
        return getter;
    }

    @VisibleForTesting
    GetFunctionStatus getStatuser() { return functionStatus; }

    @VisibleForTesting
    ListFunctions getLister() {
        return lister;
    }

    @VisibleForTesting
    StateGetter getStateGetter() {
        return stateGetter;
    }

    @VisibleForTesting
    TriggerFunction getTriggerer() {
        return triggerer;
    }

    @VisibleForTesting
    UploadFunction getUploader() {
        return uploader;
    }

    @VisibleForTesting
    DownloadFunction getDownloader() {
        return downloader;
    }

    @VisibleForTesting
    RestartFunction getRestarter() {
        return restart;
    }

    @VisibleForTesting
    StopFunction getStopper() {
        return stop;
    }

    private void parseFullyQualifiedFunctionName(String fqfn, FunctionConfig functionConfig) {
        String[] args = fqfn.split("/");
        if (args.length != 3) {
            throw new ParameterException("Fully qualified function names (FQFNs) must be of the form tenant/namespace/name");
        } else {
            functionConfig.setTenant(args[0]);
            functionConfig.setNamespace(args[1]);
            functionConfig.setName(args[2]);
        }
    }

}
