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

import io.ballerina.runtime.api.async.Callback;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import org.ballerinalang.net.transport.contract.websocket.ServerHandshakeFuture;
import org.ballerinalang.net.transport.contract.websocket.WebSocketHandshaker;
import org.ballerinalang.net.websocket.WebSocketUtil;

import static org.ballerinalang.net.websocket.WebSocketConstants.CUSTOM_HEADERS;

/**
 * The onUpgrade resource callback.
 */
public class OnUpgradeResourceCallback implements Callback {
    private final WebSocketHandshaker webSocketHandshaker;
    private final WebSocketServerService wsService;
    private final WebSocketConnectionManager connectionManager;

    public OnUpgradeResourceCallback(WebSocketHandshaker webSocketHandshaker, WebSocketServerService wsService,
            WebSocketConnectionManager connectionManager) {
        this.webSocketHandshaker = webSocketHandshaker;
        this.wsService = wsService;
        this.connectionManager = connectionManager;
    }

    @Override
    public void notifySuccess(Object result) {
        if (result instanceof BError) {
            webSocketHandshaker.cancelHandshake(400, ((BError) result).getErrorMessage().toString());
            return;
        }
        if (!webSocketHandshaker.isCancelled() && !webSocketHandshaker.isHandshakeStarted()) {
            HttpHeaders headers = null;
            if (((BObject) result).getType().getFields().get(CUSTOM_HEADERS.toString()) != null) {
                BMap<BString, BString> headersMap = (BMap) ((BObject) result).get(CUSTOM_HEADERS);
                headers = populateAndGetHttpHeaders(headersMap);
            }
            ServerHandshakeFuture future = webSocketHandshaker
                    .handshake(wsService.getNegotiableSubProtocols(), wsService.getIdleTimeoutInSeconds() * 1000,
                            headers, wsService.getMaxFrameSize());
            future.setHandshakeListener(new UpgradeListener(wsService, connectionManager, result));
        }
    }

    @Override
    public void notifyFailure(BError error) {
        error.printStackTrace();
        WebSocketConnectionInfo connectionInfo =
                connectionManager.getConnectionInfo(webSocketHandshaker.getChannelId());
        if (connectionInfo != null) {
            try {
                WebSocketUtil.closeDuringUnexpectedCondition(connectionInfo.getWebSocketConnection());
            } catch (IllegalAccessException e) {
                // Ignore as it is not possible have an Illegal access
            }
        }
    }

    private static DefaultHttpHeaders populateAndGetHttpHeaders(BMap<BString, BString> headers) {
        DefaultHttpHeaders httpHeaders = new DefaultHttpHeaders();
        BString[] keys = headers.getKeys();
        for (BString key : keys) {
            httpHeaders.add(key.toString(), headers.get(key).getValue());
        }
        return httpHeaders;
    }
}
