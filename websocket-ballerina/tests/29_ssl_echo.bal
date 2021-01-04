// Copyright (c) 2020 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/runtime;
import ballerina/test;
import ballerina/http;

string expectedString = "";
byte[] expectedBinaryData = [];

service UpgradeService /sslEcho on new Listener(21029, {
       secureSocket: {
           keyStore: {
               path: "tests/certsAndKeys/ballerinaKeystore.p12",
               password: "ballerina"
           }
       }
   }) {
   remote isolated function onUpgrade(http:Caller caller, http:Request req) returns Service {
       return new WsService6();
   }
}

service class WsService6 {
  *Service;
  remote isolated function onText(Caller caller, string data, boolean finalFrame) {
       var returnVal = caller->writeString(data, finalFrame);
       if (returnVal is WebSocketError) {
           panic <error>returnVal;
       }
   }

   remote isolated function onBinary(Caller caller, byte[] data, boolean finalFrame) {
       var returnVal = caller->writeBytes(data, finalFrame);
       if (returnVal is WebSocketError) {
           panic <error>returnVal;
       }
   }
}

service class sslEchoCallbackService {
   *CallbackService;
   remote function onText(AsyncClient wsEp, string text) {
       expectedString = <@untainted>text;
   }

   remote function onBinary(AsyncClient wsEp, byte[] data) {
       expectedBinaryData = <@untainted>data;
   }

   remote isolated function onClose(AsyncClient wsEp, int statusCode, string reason) {
       var returnVal = wsEp->close(statusCode = statusCode, reason = reason, timeoutInSeconds = 0);
       if (returnVal is WebSocketError) {
           panic <error>returnVal;
       }
   }
}

// Tests sending and receiving of binary frames in WebSocket.
@test:Config {}
public function sslBinaryEcho() {
   AsyncClient wsClient = new ("wss://localhost:21029/sslEcho", new sslEchoCallbackService(), {
           secureSocket: {
               trustStore: {
                   path: "tests/certsAndKeys/ballerinaTruststore.p12",
                   password: "ballerina"
               }
           }
       });
   byte[] binaryData = [5, 24, 56];
   checkpanic wsClient->writeBytes(binaryData);
   runtime:sleep(500);
   test:assertEquals(expectedBinaryData, binaryData, msg = "Data mismatched");
   error? result = wsClient->close(statusCode = 1000, reason = "Close the connection", timeoutInSeconds = 0);
}

// Tests sending and receiving of text frames in WebSockets.
@test:Config {}
public function sslTextEcho() {
   AsyncClient wsClient = new ("wss://localhost:21029/sslEcho", new sslEchoCallbackService(), {
           secureSocket: {
               trustStore: {
                   path: "tests/certsAndKeys/ballerinaTruststore.p12",
                   password: "ballerina"
               }
           }
       });
   checkpanic wsClient->writeString("Hi madam");
   runtime:sleep(500);
   test:assertEquals(expectedString, "Hi madam", msg = "Data mismatched");
   error? result = wsClient->close(statusCode = 1000, reason = "Close the connection", timeoutInSeconds = 0);
}
