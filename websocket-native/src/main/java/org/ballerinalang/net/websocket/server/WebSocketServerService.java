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

import io.ballerina.runtime.api.Runtime;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import org.ballerinalang.net.websocket.ModuleUtils;
import org.ballerinalang.net.websocket.WebSocketConstants;
import org.ballerinalang.net.websocket.WebSocketService;
import org.ballerinalang.net.websocket.WebSocketUtil;


/**
 * WebSocket service for service dispatching.
 */
public class WebSocketServerService extends WebSocketService {

    private String[] negotiableSubProtocols = null;
    private String basePath;
    private int maxFrameSize = WebSocketConstants.DEFAULT_MAX_FRAME_SIZE;
    private int idleTimeoutInSeconds = 0;

    public WebSocketServerService(BObject service, Runtime runtime, String basePath) {
        super(service, runtime);
        populateConfigs(basePath);
    }

    private void populateConfigs(String basePath) {
        BMap<BString, Object> configAnnotation = getServiceConfigAnnotation();
        if (configAnnotation != null) {
            negotiableSubProtocols = WebSocketUtil.findNegotiableSubProtocols(configAnnotation);
            idleTimeoutInSeconds = WebSocketUtil.findTimeoutInSeconds(configAnnotation,
                    WebSocketConstants.ANNOTATION_ATTR_IDLE_TIMEOUT, 0);
            maxFrameSize = WebSocketUtil.findMaxFrameSize(configAnnotation);
        }
        service.addNativeData(WebSocketConstants.ANNOTATION_ATTR_MAX_FRAME_SIZE.toString(), maxFrameSize);
        // This will be overridden if there is an upgrade path
        setBasePathToServiceObj(basePath);
    }

    @SuppressWarnings(WebSocketConstants.UNCHECKED) private BMap<BString, Object> getServiceConfigAnnotation() {
        return (BMap<BString, Object>) (service.getType()).getAnnotation(StringUtils.fromString(
                ModuleUtils.getPackageIdentifier() + ":" + WebSocketConstants.WEBSOCKET_ANNOTATION_CONFIGURATION));
    }

    public String[] getNegotiableSubProtocols() {
        if (negotiableSubProtocols == null) {
            return new String[0];
        }
        return negotiableSubProtocols.clone();
    }

    public int getIdleTimeoutInSeconds() {
        return idleTimeoutInSeconds;
    }

    public int getMaxFrameSize() {
        return maxFrameSize;
    }

    public void setBasePathToServiceObj(String basePath) {
        service.addNativeData(WebSocketConstants.NATIVE_DATA_BASE_PATH, basePath);
        this.basePath = basePath;
    }

    public String getBasePath() {
        return basePath;
    }
}
