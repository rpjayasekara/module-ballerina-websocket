/*
 *  Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.ballerinalang.net.websocket.server;

import org.ballerinalang.net.http.HttpConstants;
import org.ballerinalang.net.http.HttpResourceArguments;
import org.ballerinalang.net.http.HttpUtil;
import org.ballerinalang.net.transport.contract.websocket.WebSocketBinaryMessage;
import org.ballerinalang.net.transport.contract.websocket.WebSocketCloseMessage;
import org.ballerinalang.net.transport.contract.websocket.WebSocketConnection;
import org.ballerinalang.net.transport.contract.websocket.WebSocketConnectorListener;
import org.ballerinalang.net.transport.contract.websocket.WebSocketControlMessage;
import org.ballerinalang.net.transport.contract.websocket.WebSocketHandshaker;
import org.ballerinalang.net.transport.contract.websocket.WebSocketMessage;
import org.ballerinalang.net.transport.contract.websocket.WebSocketTextMessage;
import org.ballerinalang.net.transport.message.HttpCarbonMessage;
import org.ballerinalang.net.uri.URIUtil;
import org.ballerinalang.net.websocket.WebSocketResourceDispatcher;
import org.ballerinalang.net.websocket.WebSocketUtil;
import org.ballerinalang.net.websocket.observability.WebSocketObservabilityConstants;
import org.ballerinalang.net.websocket.observability.WebSocketObservabilityUtil;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.ballerinalang.net.http.HttpDispatcher.getValidatedURI;
import static org.ballerinalang.net.websocket.WebSocketConstants.BACK_SLASH;

/**
 * Ballerina Connector listener for WebSocket.
 *
 * @since 0.93
 */
public class WebSocketServerListener implements WebSocketConnectorListener {

    private final WebSocketServicesRegistry servicesRegistry;
    private final WebSocketConnectionManager connectionManager;

    public WebSocketServerListener(WebSocketServicesRegistry servicesRegistry) {
        this.servicesRegistry = servicesRegistry;
        this.connectionManager = new WebSocketConnectionManager();
    }

    @Override
    public void onHandshake(WebSocketHandshaker webSocketHandshaker) {
        HttpResourceArguments pathParams = new HttpResourceArguments();
        URI requestUri = createRequestUri(webSocketHandshaker);
        Map<String, Map<String, String>> matrixParams = new HashMap<>();
        String uriWithoutMatrixParams = URIUtil.extractMatrixParams(requestUri.getRawPath(), matrixParams);
        URI validatedUri = getValidatedURI(uriWithoutMatrixParams);
        String matchingBasePath = servicesRegistry
                .findTheMostSpecificBasePath(validatedUri.getRawPath(), servicesRegistry.getServicesByBasePath(),
                        servicesRegistry.getSortedServiceURIs());
        WebSocketServerService wsService = servicesRegistry.findMatching(matchingBasePath, pathParams,
                webSocketHandshaker);
        if (wsService == null) {
            String errMsg = "No service found to handle the service request";
            webSocketHandshaker.cancelHandshake(404, errMsg);
            WebSocketObservabilityUtil.observeError(WebSocketObservabilityConstants.ERROR_TYPE_CONNECTION,
                    errMsg, requestUri.getPath(),
                    WebSocketObservabilityConstants.CONTEXT_SERVER);
            return;
        }
        setCarbonMessageProperties(pathParams, requestUri, validatedUri, webSocketHandshaker.getHttpCarbonRequest(),
                matchingBasePath);
            WebSocketResourceDispatcher.dispatchUpgrade(webSocketHandshaker, wsService, connectionManager);
    }

    private URI createRequestUri(WebSocketHandshaker webSocketHandshaker) {
        String serviceUri = webSocketHandshaker.getTarget();
        serviceUri = HttpUtil.sanitizeBasePath(serviceUri);
        return URI.create(serviceUri);
    }

    private void setCarbonMessageProperties(HttpResourceArguments pathParams, URI requestUri, URI validateUri,
            HttpCarbonMessage msg, String matchingBasePath) {
        String subPath = URIUtil.getSubPath(validateUri.getRawPath(), matchingBasePath);
        msg.setProperty(HttpConstants.QUERY_STR, requestUri.getRawQuery());
        msg.setProperty(HttpConstants.RAW_QUERY_STR, requestUri.getRawQuery());
        msg.setProperty(HttpConstants.RESOURCE_ARGS, pathParams);
        if (subPath.startsWith(BACK_SLASH)) {
            msg.setProperty(HttpConstants.SUB_PATH, subPath.substring(1));
        } else {
            msg.setProperty(HttpConstants.SUB_PATH, subPath);
        }
    }

    @Override
    public void onMessage(WebSocketTextMessage webSocketTextMessage) {
        WebSocketResourceDispatcher.dispatchOnText(getConnectionInfo(webSocketTextMessage), webSocketTextMessage);
    }

    @Override
    public void onMessage(WebSocketBinaryMessage webSocketBinaryMessage) {
        WebSocketResourceDispatcher.dispatchOnBinary(getConnectionInfo(webSocketBinaryMessage), webSocketBinaryMessage);
    }

    @Override
    public void onMessage(WebSocketControlMessage webSocketControlMessage) {
        WebSocketResourceDispatcher.dispatchOnPingOnPong(
                getConnectionInfo(webSocketControlMessage), webSocketControlMessage, true);
    }

    @Override
    public void onMessage(WebSocketCloseMessage webSocketCloseMessage) {
        WebSocketResourceDispatcher.dispatchOnClose(
                getConnectionInfo(webSocketCloseMessage), webSocketCloseMessage, true);
    }

    @Override
    public void onClose(WebSocketConnection webSocketConnection) {
        WebSocketObservabilityUtil.observeClose(getConnectionInfo(webSocketConnection));
        try {
            WebSocketUtil.setListenerOpenField(
                    connectionManager.removeConnectionInfo(webSocketConnection.getChannelId()));
        } catch (IllegalAccessException e) {
            // Ignore as it is not possible have an Illegal access
        }
    }

    @Override
    public void onError(WebSocketConnection webSocketConnection, Throwable throwable) {
        WebSocketResourceDispatcher.dispatchOnError(getConnectionInfo(webSocketConnection), throwable, true);
    }

    @Override
    public void onIdleTimeout(WebSocketControlMessage controlMessage) {
        WebSocketResourceDispatcher.dispatchOnIdleTimeout(getConnectionInfo(controlMessage));
    }

    private String getConnectionId(WebSocketMessage webSocketMessage) {
        return webSocketMessage.getWebSocketConnection().getChannelId();
    }

    private WebSocketConnectionInfo getConnectionInfo(WebSocketMessage webSocketMessage) {
        return connectionManager.getConnectionInfo(getConnectionId(webSocketMessage));
    }

    private WebSocketConnectionInfo getConnectionInfo(WebSocketConnection webSocketConnection) {
        return connectionManager.getConnectionInfo(webSocketConnection.getChannelId());
    }

}

