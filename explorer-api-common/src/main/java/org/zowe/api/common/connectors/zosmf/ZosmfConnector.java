/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright IBM Corporation 2018, 2018
 */
package org.zowe.api.common.connectors.zosmf;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import lombok.extern.slf4j.Slf4j;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.zowe.api.common.connectors.zosmf.exceptions.ZosmfConnectionException;
import org.zowe.api.common.security.CustomUser;

@Slf4j
@Service
public class ZosmfConnector {

    private final String baseZosmfUri;

    public String getFullUrl(String relativePath) {
        return baseZosmfUri + relativePath;
    }

    @Autowired
    public ZosmfConnector(ZosmfProperties properties) {
        baseZosmfUri = "https://" + properties.getIpAddress() + ":" + properties.getHttpsPort() + "/zosmf/";
    }

    public HttpResponse request(RequestBuilder requestBuilder) throws IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUser customUser = (CustomUser) authentication.getPrincipal();
        requestBuilder.setHeader("Cookie", customUser.getLtpa());
        requestBuilder.setHeader("X-CSRF-ZOSMF-HEADER", "");

        HttpClient client;
        try {
            client = createIgnoreSSLClient();
        } catch (GeneralSecurityException e) {
            log.error("request", e);
            throw new ZosmfConnectionException(e);
        }
        return client.execute(requestBuilder.build());

    }

    public Header getLtpaHeader(String username, String password)
            throws IOException, KeyManagementException, NoSuchAlgorithmException {
        HttpClient createIgnoreSSLClient = createIgnoreSSLClientWithPassword(username, password);

        HttpGet httpGet = new HttpGet(baseZosmfUri + "restjobs/jobs");
        httpGet.setHeader("X-CSRF-ZOSMF-HEADER", "");
        HttpResponse response = createIgnoreSSLClient.execute(httpGet);
        Header setCookieHeader = response.getFirstHeader("Set-Cookie");
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
            return setCookieHeader;
        } else {
            throw new IOException("login failed");
        }
    }

    public static HttpClient createIgnoreSSLClientWithPassword(String userName, String password)
            throws NoSuchAlgorithmException, KeyManagementException {
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(userName, password));

        SSLContext sslcontext = SSLContext.getInstance("TLS");
        sslcontext.init(null, new TrustManager[]{new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] arg0, String arg1) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] arg0, String arg1) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

        } }, new java.security.SecureRandom());
        return HttpClientBuilder.create().setSSLContext(sslcontext).setDefaultCredentialsProvider(credentialsProvider)
                .setSSLHostnameVerifier(new HostnameVerifier() {

                    @Override
                    public boolean verify(String s1, SSLSession s2) {
                        return true;
                    }

                }).build();
    }

    public static HttpClient createIgnoreSSLClient() throws KeyManagementException, NoSuchAlgorithmException {
        SSLContext sslcontext = SSLContext.getInstance("TLS");
        sslcontext.init(null, new TrustManager[]{new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] arg0, String arg1) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] arg0, String arg1) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }

        } } , new java.security.SecureRandom());
        return HttpClientBuilder.create().setSSLContext(sslcontext).setSSLHostnameVerifier(new HostnameVerifier() {

            @Override
            public boolean verify(String s1, SSLSession s2) {
                return true;
            }

        }).build();
    }
}