/**
 * Copyright © 2016-2022 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.service.cloud.rpc.processor;

import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.WidgetTypeId;
import org.thingsboard.server.common.data.widget.WidgetType;
import org.thingsboard.server.common.data.widget.WidgetTypeDetails;
import org.thingsboard.server.gen.edge.v1.WidgetTypeUpdateMsg;

import java.util.UUID;

@Component
@Slf4j
public class WidgetTypeCloudProcessor extends BaseCloudProcessor {

    public ListenableFuture<Void> processWidgetTypeMsgFromCloud(TenantId tenantId, WidgetTypeUpdateMsg widgetTypeUpdateMsg) {
        WidgetTypeId widgetTypeId = new WidgetTypeId(new UUID(widgetTypeUpdateMsg.getIdMSB(), widgetTypeUpdateMsg.getIdLSB()));
        switch (widgetTypeUpdateMsg.getMsgType()) {
            case ENTITY_CREATED_RPC_MESSAGE:
            case ENTITY_UPDATED_RPC_MESSAGE:
                widgetCreationLock.lock();
                try {
                    WidgetTypeDetails widgetTypeDetails = widgetTypeService.findWidgetTypeDetailsById(tenantId, widgetTypeId);
                    if (widgetTypeDetails == null) {
                        widgetTypeDetails = new WidgetTypeDetails();
                        if (widgetTypeUpdateMsg.getIsSystem()) {
                            widgetTypeDetails.setTenantId(TenantId.SYS_TENANT_ID);
                        } else {
                            widgetTypeDetails.setTenantId(tenantId);
                        }
                        widgetTypeDetails.setId(widgetTypeId);
                        widgetTypeDetails.setCreatedTime(Uuids.unixTimestamp(widgetTypeId.getId()));
                    }
                    if (widgetTypeUpdateMsg.hasBundleAlias()) {
                        widgetTypeDetails.setBundleAlias(widgetTypeUpdateMsg.getBundleAlias());
                    }
                    if (widgetTypeUpdateMsg.hasAlias()) {
                        widgetTypeDetails.setAlias(widgetTypeUpdateMsg.getAlias());
                    }
                    if (widgetTypeUpdateMsg.hasName()) {
                        widgetTypeDetails.setName(widgetTypeUpdateMsg.getName());
                    }
                    if (widgetTypeUpdateMsg.hasDescriptorJson()) {
                        widgetTypeDetails.setDescriptor(JacksonUtil.toJsonNode(widgetTypeUpdateMsg.getDescriptorJson()));
                    }
                    if (widgetTypeUpdateMsg.hasImage()) {
                        widgetTypeDetails.setImage(widgetTypeUpdateMsg.getImage());
                    }
                    if (widgetTypeUpdateMsg.hasDescription()) {
                        widgetTypeDetails.setDescription(widgetTypeUpdateMsg.getDescription());
                    }
                    widgetTypeService.saveWidgetType(widgetTypeDetails, false);
                } finally {
                    widgetCreationLock.unlock();
                }
                break;
            case ENTITY_DELETED_RPC_MESSAGE:
                WidgetType widgetType = widgetTypeService.findWidgetTypeById(tenantId, widgetTypeId);
                if (widgetType != null) {
                    widgetTypeService.deleteWidgetType(tenantId, widgetType.getId());
                }
                break;
            case UNRECOGNIZED:
                log.error("Unsupported msg type");
                return Futures.immediateFailedFuture(new RuntimeException("Unsupported msg type " + widgetTypeUpdateMsg.getMsgType()));
        }
        return Futures.immediateFuture(null);
    }
}
