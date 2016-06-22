/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.data.publisher.application.authentication.impl;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.CarbonException;
import org.wso2.carbon.core.util.AnonymousSessionUtil;
import org.wso2.carbon.databridge.commons.Event;
import org.wso2.carbon.identity.application.authentication.framework.AbstractAuthenticationDataPublisher;
import org.wso2.carbon.identity.application.authentication.framework.model.AuthenticationData;
import org.wso2.carbon.identity.application.authentication.framework.model.SessionData;
import org.wso2.carbon.identity.application.authentication.framework.util.FrameworkConstants;
import org.wso2.carbon.identity.core.util.IdentityUtil;
import org.wso2.carbon.identity.data.publisher.application.authentication.AuthPublisherConstants;
import org.wso2.carbon.identity.data.publisher.application.authentication.internal.AuthenticationDataPublisherDataHolder;
import org.wso2.carbon.registry.core.service.RegistryService;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.UserRealm;
import org.wso2.carbon.user.core.UserStoreManager;
import org.wso2.carbon.user.core.service.RealmService;

public class DASAuthnDataPublisherImpl extends AbstractAuthenticationDataPublisher {

    public static final Log LOG = LogFactory.getLog(DASAuthnDataPublisherImpl.class);

    @Override
    public void doPublishAuthenticationStepSuccess(AuthenticationData authenticationData) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Publishing authentication step success results");
        }
        publishAuthenticationData(authenticationData);
    }

    @Override
    public void doPublishAuthenticationStepFailure(AuthenticationData authenticationData) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Publishing authentication step failure results");
        }
        publishAuthenticationData(authenticationData);
    }

    @Override
    public void doPublishAuthenticationSuccess(AuthenticationData authenticationData) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Publishing authentication success results");
        }
        publishAuthenticationData(authenticationData);
    }

    @Override
    public void doPublishAuthenticationFailure(AuthenticationData authenticationData) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Publishing authentication failure results");
        }
        publishAuthenticationData(authenticationData);
    }

    @Override
    public void doPublishSessionCreation(SessionData sessionData) {
        publishSessionData(sessionData, AuthPublisherConstants.SESSION_CREATION_STATUS);
    }

    @Override
    public void doPublishSessionTermination(SessionData sessionData) {
        publishSessionData(sessionData, AuthPublisherConstants.SESSION_TERMINATION_STATUS);

    }

    @Override
    public void doPublishSessionUpdate(SessionData sessionData) {
        publishSessionData(sessionData, AuthPublisherConstants.SESSION_UPDATE_STATUS);
    }

    private void publishAuthenticationData(AuthenticationData authenticationData) {

        String roleList = null;
        if (FrameworkConstants.LOCAL_IDP_NAME.equalsIgnoreCase(authenticationData.getIdentityProviderType())) {
            roleList = getCommaSeparatedUserRoles(authenticationData.getUserStoreDomain() + "/" + authenticationData
                    .getUsername(), authenticationData.getTenantDomain());
        }

        Object[] payloadData = new Object[20];
        payloadData[0] = authenticationData.getContextId();
        payloadData[1] = authenticationData.getEventId();
        payloadData[2] = authenticationData.isAuthnSuccess();
        payloadData[3] = replaceIfNotAvailable(AuthPublisherConstants.USERNAME, authenticationData.getUsername());
        payloadData[4] = replaceIfNotAvailable(AuthPublisherConstants.USER_STORE_DOMAIN, authenticationData
                .getUserStoreDomain());
        payloadData[5] = authenticationData.getTenantDomain();
        payloadData[6] = authenticationData.getRemoteIp();
        payloadData[7] = authenticationData.getInboundProtocol();
        payloadData[8] = replaceIfNotAvailable(AuthPublisherConstants.SERVICE_PROVIDER, authenticationData
                .getServiceProvider());
        payloadData[9] = authenticationData.isRememberMe();
        payloadData[10] = authenticationData.isForcedAuthn();
        payloadData[11] = authenticationData.isPassive();
        payloadData[12] = replaceIfNotAvailable(AuthPublisherConstants.ROLES, roleList);
        payloadData[13] = String.valueOf(authenticationData.getStepNo());
        payloadData[14] = replaceIfNotAvailable(AuthPublisherConstants.IDENTITY_PROVIDER, authenticationData.getIdentityProvider());
        payloadData[15] = authenticationData.isSuccess();
        payloadData[16] = authenticationData.getAuthenticator();
        payloadData[17] = authenticationData.isInitialLogin();
        payloadData[18] = authenticationData.getIdentityProviderType();
        payloadData[19] = System.currentTimeMillis();

        if (LOG.isDebugEnabled()) {
            for (int i = 0; i < 19; i++) {
                if (payloadData[i] != null) {
                    LOG.debug("Payload data for entry " + i + " " + payloadData[i].toString());
                } else {
                    LOG.debug("Payload data for entry " + i + " is null");
                }

            }
        }
        Event event = new Event(AuthPublisherConstants.AUTHN_DATA_STREAM_NAME, System.currentTimeMillis(), null, null,
                payloadData);
        AuthenticationDataPublisherDataHolder.getInstance().getPublisherService().publish(event);
    }

    @Override
    public String getName() {
        return AuthPublisherConstants.DAS_PUBLISHER_NAME;
    }

    private String getCommaSeparatedUserRoles(String userName, String tenantDomain) {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Retrieving roles for user " + userName + ", tenant domain " + tenantDomain);
        }
        if (tenantDomain == null || userName == null) {
            return StringUtils.EMPTY;
        }

        RegistryService registryService = AuthenticationDataPublisherDataHolder.getInstance().getRegistryService();
        RealmService realmService = AuthenticationDataPublisherDataHolder.getInstance().getRealmService();

        UserRealm realm = null;
        UserStoreManager userstore = null;

        try {
            realm = AnonymousSessionUtil.getRealmByTenantDomain(registryService,
                    realmService, tenantDomain);
            userstore = realm.getUserStoreManager();
            if (userstore.isExistingUser(userName)) {
                String[] newRoles = userstore.getRoleListOfUser(userName);
                StringBuilder sb = new StringBuilder();
                for (String role : newRoles) {
                    sb.append(",").append(role);
                }
                if (sb.length() > 0) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Returning roles, " + sb.substring(1));
                    }
                    return sb.substring(1); //remove the first comma
                }

            }
        } catch (CarbonException e) {
            LOG.error("Error when getting realm for " + userName + "@" + tenantDomain, e);
        } catch (UserStoreException e) {
            LOG.error("Error when getting user store for " + userName + "@" + tenantDomain, e);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("No roles found. Returning empty string");
        }
        return StringUtils.EMPTY;
    }

    /**
     * Add default values if the values coming in are null or empty
     *
     * @param name  Name of the property configured in identity.xml
     * @param value In coming value
     * @return
     */
    protected String replaceIfNotAvailable(String name, String value) {
        if (StringUtils.isNotEmpty(name) && StringUtils.isEmpty(value)) {
            String defaultValue = IdentityUtil.getProperty(AuthPublisherConstants.CONFIG_PREFIX + name);
            if (defaultValue != null) {
                return defaultValue;
            }
        }
        if (StringUtils.isEmpty(value)) {
            return AuthPublisherConstants.NOT_AVAILABLE;
        }
        return value;
    }

    protected void publishSessionData(SessionData sessionData, int actionId) {

        if (sessionData != null) {
            Object[] payloadData = new Object[11];
            payloadData[0] = replaceIfNotAvailable(AuthPublisherConstants.SESSION_ID, sessionData.getSessionId());
            payloadData[1] = sessionData.getCreatedTimestamp();
            payloadData[2] = sessionData.getUpdatedTimestamp();
            payloadData[3] = sessionData.getTerminationTimestamp();
            payloadData[4] = actionId;
            payloadData[5] = replaceIfNotAvailable(AuthPublisherConstants.USERNAME, sessionData.getUser());
            payloadData[6] = replaceIfNotAvailable(AuthPublisherConstants.USER_STORE_DOMAIN, sessionData
                    .getUserStoreDomain());
            payloadData[7] = sessionData.getRemoteIP();
            payloadData[8] = sessionData.getTenantDomain();
            payloadData[9] = sessionData.isRememberMe();
            payloadData[10] = System.currentTimeMillis();

            if (LOG.isDebugEnabled()) {
                for (int i = 0; i < 10; i++) {
                    if (payloadData[i] != null) {
                        LOG.debug("Payload data for entry " + i + " " + payloadData[i].toString());
                    } else {
                        LOG.debug("Payload data for entry " + i + " is null");
                    }

                }
            }
            Event event = new Event(AuthPublisherConstants.SESSION_DATA_STREAM_NAME, System.currentTimeMillis(), null, null,
                    payloadData);
            AuthenticationDataPublisherDataHolder.getInstance().getPublisherService().publish(event);
        }
    }
}
