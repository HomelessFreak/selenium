// Licensed to the Software Freedom Conservancy (SFC) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The SFC licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.openqa.grid.internal.utils.configuration;

import com.google.common.annotations.VisibleForTesting;

import org.openqa.grid.common.RegistrationRequest;
import org.openqa.grid.common.SeleniumProtocol;
import org.openqa.grid.common.exception.GridConfigurationException;
import org.openqa.grid.internal.utils.configuration.json.NodeJsonConfiguration;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.Platform;
import org.openqa.selenium.json.JsonInput;
import org.openqa.selenium.net.NetworkUtils;
import org.openqa.selenium.remote.CapabilityType;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class GridNodeConfiguration extends GridConfiguration {
  public static final String DEFAULT_NODE_CONFIG_FILE = "org/openqa/grid/common/defaults/DefaultNodeWebDriver.json";
  public static final String CONFIG_UUID_CAPABILITY = "server:CONFIG_UUID";

  private static NodeJsonConfiguration DEFAULT_CONFIG_FROM_JSON
      = NodeJsonConfiguration.loadFromResourceOrFile(DEFAULT_NODE_CONFIG_FILE);

  @VisibleForTesting
  static final String ROLE = "node";

  private static class HostPort {
    final String host;
    final int port;

    HostPort(String host, int port) {
      this.host = host;
      this.port = port;
    }
  }

  private HostPort hubHostPort;

  /*
   * config parameters which do not serialize or de-serialize
   */

  /**
   * Node specific json config file to use. Defaults to {@code null}.
   */
  public String nodeConfigFile;

  /*
   * config parameters which do not serialize to json
   */

  /**
   * The address to report to the hub. By default it's generated based on the host and port specified.
   * Setting a value overrides the default (http://<host>:<port>).
   */
  public String remoteHost;

  // used to read a Selenium 2.x nodeConfig.json file and throw a friendly exception
  @Deprecated
  private Object configuration;

  /*
   * config parameters which serialize and deserialize to/from json
   */

  /**
   * The host name or IP of the hub. Defaults to {@code null}.
   */
  public String hubHost;

  /**
   * The port of the hub. Defaults to {@code null}.
   */
  public Integer hubPort;

  /**
   * The id tu use for this node. Automatically generated when {@code null}. Defaults to {@code null}.
   */
  public String id;

  /**
   * The capabilties of this node. Defaults from the capabilities specified in the
   * {@link #DEFAULT_NODE_CONFIG_FILE} or an empty list if the {@link #DEFAULT_NODE_CONFIG_FILE}
   * can not be loaded.
   */
  public List<MutableCapabilities> capabilities;

  /**
   * The down polling limit for the node. Defaults to {@code null}.
   */
  public Integer downPollingLimit;

  /**
   * The hub url. Defaults to {@code http://localhost:4444}.
   */
  public String hub;

  /**
   * How often to pull the node. Defaults to 5000 ms
   */
  public Integer nodePolling;

  /**
   * When to time out a node status check. Defaults is after 5000 ms.
   */
  public Integer nodeStatusCheckTimeout;

  /**
   * The proxy class name to use. Defaults to org.openqa.grid.selenium.proxy.DefaultRemoteProxy.
   */
  public String proxy;

  /**
   * Whether to register this node with the hub. Defaults to {@code true}
   */
  public Boolean register;

  /**
   * How often to re-register this node with the hub. Defaults to every 5000 ms.
   */
  public Integer registerCycle;

  /**
   * How long to wait before marking this node down. Defaults is 60000 ms.
   */
  public Integer unregisterIfStillDownAfter;

  /**
   * Whether or not to drop capabilities that does not belong to the current platform family
   */
  public boolean enablePlatformVerification;

  /**
   * Creates a new configuration using the default values.
   */
  public GridNodeConfiguration() {
    this(DEFAULT_CONFIG_FROM_JSON);
  }

  public GridNodeConfiguration(NodeJsonConfiguration jsonConfig) {
    super(jsonConfig);
    role = ROLE;
    capabilities = jsonConfig.getCapabilities();
    maxSession = jsonConfig.getMaxSession();
    register = jsonConfig.getRegister();
    registerCycle = jsonConfig.getRegisterCycle();
    nodeStatusCheckTimeout = jsonConfig.getNodeStatusCheckTimeout();
    nodePolling = jsonConfig.getNodePolling();
    unregisterIfStillDownAfter = jsonConfig.getUnregisterIfStillDownAfter();
    downPollingLimit = jsonConfig.getDownPollingLimit();
    proxy = jsonConfig.getProxy();
    enablePlatformVerification = jsonConfig.isEnablePlatformVerification();
    if (jsonConfig.getHub() != null) {
      hub = jsonConfig.getHub();

    } else {
      if (jsonConfig.getHubHost() == null) {
        throw new RuntimeException("You must specify either a hubHost or hub parameter in a node JSON config.");
      }
      if (jsonConfig.getHubPort() == null) {
        throw new RuntimeException("You must specify either a hubPort or hub parameter in a node JSON config.");
      }
      hub = hubHostPort.toString();
    }
  }

  public String getHubHost() {
    return getHubHostPort().host;
  }

  public Integer getHubPort() {
    return getHubHostPort().port;
  }

  private HostPort getHubHostPort() {
    if (hubHostPort == null) { // parse options
      // -hub has precedence
      if (hub != null) {
        try {
          URL u = new URL(hub);
          hubHostPort = new HostPort(u.getHost(), u.getPort());
        } catch (MalformedURLException mURLe) {
          throw new RuntimeException("-hub must be a valid url: " + hub, mURLe);
        }
      } else if (hubHost != null || hubPort != null) {
        if (hubHost == null) {
          throw new RuntimeException("You must specify either a -hubHost or -hub parameter.");
        }
        if (hubPort == null) {
          throw new RuntimeException("You must specify either a -hubPort or -hub parameter.");
        }
        hubHostPort = new HostPort(hubHost, hubPort);
      } else {
        try {
          URL u = new URL(hub);
          hubHostPort = new HostPort(u.getHost(), u.getPort());
        } catch (MalformedURLException mURLe) {
          throw new RuntimeException("-hub must be a valid url: " + hub, mURLe);
        }
      }
    }
    return hubHostPort;
  }

  public String getRemoteHost() {
    if (remoteHost == null) {
      if (host == null) {
        host = "localhost";
      }
      if (port == null) {
        port = 5555;
      }
      remoteHost = "http://" + host + ":" + port;
    }
    return remoteHost;
  }

  public void merge(GridNodeConfiguration other) {
    if (other == null) {
      return;
    }
    super.merge(other);

    if (isMergeAble(List.class, other.capabilities, capabilities)) {
      capabilities = other.capabilities;
    }
    if (isMergeAble(Integer.class, other.downPollingLimit, downPollingLimit)) {
      downPollingLimit = other.downPollingLimit;
    }
    if (isMergeAble(String.class, other.hub, hub)) {
      hub = other.hub;
    }
    if (isMergeAble(String.class, other.hubHost, hubHost)) {
      hubHost = other.hubHost;
    }
    if (isMergeAble(Integer.class, other.hubPort, hubPort)) {
      hubPort = other.hubPort;
    }
    if (isMergeAble(String.class, other.id, id)) {
      id = other.id;
    }
    if (isMergeAble(Integer.class, other.nodePolling, nodePolling)) {
      nodePolling = other.nodePolling;
    }
    if (isMergeAble(Integer.class, other.nodeStatusCheckTimeout, nodeStatusCheckTimeout)) {
      nodeStatusCheckTimeout = other.nodeStatusCheckTimeout;
    }
    if (isMergeAble(String.class, other.proxy, proxy)) {
      proxy = other.proxy;
    }
    if (isMergeAble(Boolean.class, other.register, register)) {
      register = other.register;
    }
    if (isMergeAble(Integer.class, other.registerCycle, registerCycle)) {
      registerCycle = other.registerCycle;
    }
    if (isMergeAble(String.class, other.remoteHost, remoteHost)) {
      remoteHost = other.remoteHost;
    }
    if (isMergeAble(Integer.class, other.unregisterIfStillDownAfter, unregisterIfStillDownAfter)) {
      unregisterIfStillDownAfter = other.unregisterIfStillDownAfter;
    }

    // never merge configuration. it should always be null.
  }

  @Override
  protected void serializeFields(Map<String, Object> appendTo) {
    super.serializeFields(appendTo);

    appendTo.put("remoteHost", remoteHost);
    appendTo.put("hubHost", hubHost);
    appendTo.put("hubPort", hubPort);
    appendTo.put("id", id);
    appendTo.put("capabilities", capabilities);
    appendTo.put("downPollingLimit", downPollingLimit);
    appendTo.put("hub", hub);
    appendTo.put("nodePolling", nodePolling);
    appendTo.put("nodeStatusCheckTimeout", nodeStatusCheckTimeout);
    appendTo.put("proxy", proxy);
    appendTo.put("register", register);
    appendTo.put("registerCycle", registerCycle);
    appendTo.put("unregisterIfStillDownAfter", unregisterIfStillDownAfter);
    appendTo.put("enablePlatformVerification", enablePlatformVerification);
  }

  @Override
  public String toString(String format) {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString(format));
    sb.append(toString(format, "capabilities", capabilities));
    sb.append(toString(format, "downPollingLimit", downPollingLimit));
    sb.append(toString(format, "hub", hub));
    sb.append(toString(format, "id", id));
    sb.append(toString(format, "hubHost", hubHost));
    sb.append(toString(format, "hubPort", hubPort));
    sb.append(toString(format, "nodeConfigFile", nodeConfigFile));
    sb.append(toString(format, "nodePolling", nodePolling));
    sb.append(toString(format, "nodeStatusCheckTimeout", nodeStatusCheckTimeout));
    sb.append(toString(format, "proxy", proxy));
    sb.append(toString(format, "register", register));
    sb.append(toString(format, "registerCycle", registerCycle));
    sb.append(toString(format, "remoteHost", remoteHost));
    sb.append(toString(format, "unregisterIfStillDownAfter", unregisterIfStillDownAfter));
    return sb.toString();
  }

  /**
   * @param filePath node config json file to load configuration from
   */
  public static GridNodeConfiguration loadFromJSON(String filePath) {
    return loadFromJSON(StandaloneConfiguration.loadJsonFromResourceOrFile(filePath));
  }

  public static GridNodeConfiguration loadFromJSON(JsonInput jsonInput) {
    try {
      GridNodeConfiguration fromJson = new GridNodeConfiguration(NodeJsonConfiguration.loadFromJson(jsonInput));
      GridNodeConfiguration result = new GridNodeConfiguration(); // defaults
      result.merge(fromJson);
      // copy non-mergeable fields
      result.hub = String.format("http://%s:%s", fromJson.getHubHostPort(), fromJson.getHubPort());
      if (fromJson.hub != null) {
        result.hub = fromJson.hub;
      }
      if (fromJson.hubHost != null) {
        result.hubHost = fromJson.hubHost;
      }
      if (fromJson.hubPort != null) {
        result.hubPort = fromJson.hubPort;
      }
      if (fromJson.host != null) {
        result.host = fromJson.host;
      }
      if (fromJson.port != null) {
        result.port = fromJson.port;
      }
      return result;
    } catch (Throwable e) {
      throw new GridConfigurationException("Error with the JSON of the config : " + e.getMessage(), e);
    }
  }

  public void fixUpCapabilities() {
    if (capabilities == null) {
      return; // assumes the caller set it/wants it this way
    }

    Platform current = Platform.getCurrent();
    capabilities = capabilities.stream()
        .peek(cap -> cap.setCapability(
            CapabilityType.PLATFORM,
            Optional.ofNullable(cap.getCapability(CapabilityType.PLATFORM_NAME)).orElse(
                Optional.ofNullable(cap.getCapability(CapabilityType.PLATFORM)).orElse(current))))
        .peek(cap -> cap.setCapability(
            CapabilityType.PLATFORM_NAME,
            Optional.ofNullable(cap.getCapability(CapabilityType.PLATFORM_NAME)).orElse(
                Optional.ofNullable(cap.getCapability(CapabilityType.PLATFORM)).orElse(current))))
        .peek(cap -> cap.setCapability(RegistrationRequest.SELENIUM_PROTOCOL,
            Optional.ofNullable(cap.getCapability(RegistrationRequest.SELENIUM_PROTOCOL))
                .orElse(SeleniumProtocol.WebDriver.toString())))
        .peek(cap -> cap.setCapability(CONFIG_UUID_CAPABILITY, UUID.randomUUID().toString()))
        .collect(Collectors.toList());
  }

  public void dropCapabilitiesThatDoesNotMatchCurrentPlatform() {
    if (!enablePlatformVerification) {
      return;
    }

    if (capabilities == null) {
      return; // assumes the caller set it/wants it this way
    }

    Platform current = Platform.getCurrent();
    Platform currentFamily = Optional.ofNullable(current.family()).orElse(current);
    capabilities = capabilities.stream()
        .filter(cap -> cap.getPlatform() != null
                       && (cap.getPlatform() == Platform.ANY || cap.getPlatform().is(currentFamily)))
        .collect(Collectors.toList());
  }

  public void fixUpHost() {
    NetworkUtils util = new NetworkUtils();
    if (host == null || "ip".equalsIgnoreCase(host)) {
      host = util.getIp4NonLoopbackAddressOfThisMachine().getHostAddress();
    } else if ("host".equalsIgnoreCase(host)) {
      host = util.getIp4NonLoopbackAddressOfThisMachine().getHostName();
    }
  }
}
