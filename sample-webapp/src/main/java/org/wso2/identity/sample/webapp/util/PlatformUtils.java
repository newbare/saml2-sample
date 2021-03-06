/*
 * Copyright 2005-2013 WSO2, Inc. http://www.wso2.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.wso2.identity.sample.webapp.util;

/**
 * Setups the TrustStore System properties
 */
public class PlatformUtils {

    public static void setKeyStoreProperties() {
        System.setProperty("javax.net.ssl.trustStore", "/Users/nuwanbando/wso2/clients/wgu/demo/az/apache-tomcat-7.0.40/webapps/sample-webapp/WEB-INF/classes/wso2carbon.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "wso2carbon");
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");
    }

    public static void setTrustStoreParams() {
        System.setProperty("javax.net.ssl.trustStore", "/Users/nuwanbando/wso2/clients/wgu/demo/az/apache-tomcat-7.0.40/webapps/sample-webapp/WEB-INF/classes/wso2carbon.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "wso2carbon");
    }

    public static void setKeyStoreParams() {
        System.setProperty("Security.KeyStore.Location", "/Users/nuwanbando/wso2/clients/wgu/demo/az/apache-tomcat-7.0.40/webapps/sample-webapp/WEB-INF/classes/wso2carbon.jks");
        System.setProperty("Security.KeyStore.Password", "wso2carbon");
    }
}
