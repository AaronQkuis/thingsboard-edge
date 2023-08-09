/**
 * Copyright © 2016-2023 The Thingsboard Authors
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
package org.thingsboard.server.edge;

import com.google.protobuf.AbstractMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.thingsboard.server.common.data.Customer;
import org.thingsboard.server.common.data.Device;
import org.thingsboard.server.common.data.EntityView;
import org.thingsboard.server.common.data.edge.Edge;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.gen.edge.v1.EntityViewUpdateMsg;
import org.thingsboard.server.gen.edge.v1.EntityViewsRequestMsg;
import org.thingsboard.server.gen.edge.v1.UpdateMsgType;
import org.thingsboard.server.gen.edge.v1.UplinkMsg;

import java.util.UUID;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DaoSqlTest
public class EntityViewEdgeTest extends AbstractEdgeTest {

    @Test
    @Ignore
    public void testEntityViews() throws Exception {
        // create entity view and assign to edge
        edgeImitator.expectMessageAmount(1);
        Device device = findDeviceByName("Edge Device 1");
        EntityView entityView = new EntityView();
        entityView.setName("Edge EntityView 1");
        entityView.setType("test");
        entityView.setEntityId(device.getId());
        EntityView savedEntityView = doPost("/api/entityView", entityView, EntityView.class);
        doPost("/api/edge/" + edge.getUuidId()
                + "/entityView/" + savedEntityView.getUuidId(), EntityView.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        verifyEntityViewUpdateMsg(savedEntityView, device);

        // update entity view
        edgeImitator.expectMessageAmount(1);
        savedEntityView.setName("Edge EntityView 1 Updated");
        savedEntityView = doPost("/api/entityView", savedEntityView, EntityView.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityViewUpdateMsg);
        EntityViewUpdateMsg entityViewUpdateMsg = (EntityViewUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE, entityViewUpdateMsg.getMsgType());
        Assert.assertEquals(savedEntityView.getName(), entityViewUpdateMsg.getName());

        // request entity view(s) for device
        UplinkMsg.Builder uplinkMsgBuilder = UplinkMsg.newBuilder();
        EntityViewsRequestMsg.Builder entityViewsRequestBuilder = EntityViewsRequestMsg.newBuilder();
        entityViewsRequestBuilder.setEntityIdMSB(device.getUuidId().getMostSignificantBits());
        entityViewsRequestBuilder.setEntityIdLSB(device.getUuidId().getLeastSignificantBits());
        entityViewsRequestBuilder.setEntityType(device.getId().getEntityType().name());
        testAutoGeneratedCodeByProtobuf(entityViewsRequestBuilder);
        uplinkMsgBuilder.addEntityViewsRequestMsg(entityViewsRequestBuilder.build());

        testAutoGeneratedCodeByProtobuf(uplinkMsgBuilder);

        edgeImitator.expectResponsesAmount(1);
        edgeImitator.expectMessageAmount(1);
        edgeImitator.sendUplinkMsg(uplinkMsgBuilder.build());
        Assert.assertTrue(edgeImitator.waitForResponses());
        Assert.assertTrue(edgeImitator.waitForMessages());
        verifyEntityViewUpdateMsg(savedEntityView, device);

        // unassign entity view from edge
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/edge/" + edge.getUuidId()
                + "/entityView/" + savedEntityView.getUuidId(), EntityView.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityViewUpdateMsg);
        entityViewUpdateMsg = (EntityViewUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, entityViewUpdateMsg.getMsgType());
        Assert.assertEquals(entityViewUpdateMsg.getIdMSB(), savedEntityView.getUuidId().getMostSignificantBits());
        Assert.assertEquals(entityViewUpdateMsg.getIdLSB(), savedEntityView.getUuidId().getLeastSignificantBits());

        // delete entity view - message expected, it was sent to all edges
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/entityView/" + savedEntityView.getUuidId())
                .andExpect(status().isOk());
        Assert.assertTrue(edgeImitator.waitForMessages(1));

        // create entity view #2 and assign to edge
        edgeImitator.expectMessageAmount(1);
        entityView = new EntityView();
        entityView.setName("Edge EntityView 2");
        entityView.setType("test");
        entityView.setEntityId(device.getId());
        savedEntityView = doPost("/api/entityView", entityView, EntityView.class);
        doPost("/api/edge/" + edge.getUuidId()
                + "/entityView/" + savedEntityView.getUuidId(), EntityView.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        verifyEntityViewUpdateMsg(savedEntityView, device);

        // assign entity view #2 to customer
        Customer customer = new Customer();
        customer.setTitle("Edge Customer");
        Customer savedCustomer = doPost("/api/customer", customer, Customer.class);
        edgeImitator.expectMessageAmount(2);
        doPost("/api/customer/" + savedCustomer.getUuidId()
                + "/edge/" + edge.getUuidId(), Edge.class);
        Assert.assertTrue(edgeImitator.waitForMessages());

        edgeImitator.expectMessageAmount(1);
        doPost("/api/customer/" + savedCustomer.getUuidId()
                + "/entityView/" + savedEntityView.getUuidId(), EntityView.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityViewUpdateMsg);
        entityViewUpdateMsg = (EntityViewUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE, entityViewUpdateMsg.getMsgType());
        Assert.assertEquals(savedCustomer.getUuidId().getMostSignificantBits(), entityViewUpdateMsg.getCustomerIdMSB());
        Assert.assertEquals(savedCustomer.getUuidId().getLeastSignificantBits(), entityViewUpdateMsg.getCustomerIdLSB());

        // unassign entity view #2 from customer
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/customer/entityView/" + savedEntityView.getUuidId(), EntityView.class);
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityViewUpdateMsg);
        entityViewUpdateMsg = (EntityViewUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_UPDATED_RPC_MESSAGE, entityViewUpdateMsg.getMsgType());
        Assert.assertEquals(
                new CustomerId(EntityId.NULL_UUID),
                new CustomerId(new UUID(entityViewUpdateMsg.getCustomerIdMSB(), entityViewUpdateMsg.getCustomerIdLSB())));

        // delete entity view #2 - messages expected
        edgeImitator.expectMessageAmount(1);
        doDelete("/api/entityView/" + savedEntityView.getUuidId())
                .andExpect(status().isOk());
        Assert.assertTrue(edgeImitator.waitForMessages());
        latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityViewUpdateMsg);
        entityViewUpdateMsg = (EntityViewUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_DELETED_RPC_MESSAGE, entityViewUpdateMsg.getMsgType());
        Assert.assertEquals(savedEntityView.getUuidId().getMostSignificantBits(), entityViewUpdateMsg.getIdMSB());
        Assert.assertEquals(savedEntityView.getUuidId().getLeastSignificantBits(), entityViewUpdateMsg.getIdLSB());

    }

    private void verifyEntityViewUpdateMsg(EntityView entityView, Device device) throws InvalidProtocolBufferException {
        AbstractMessage latestMessage = edgeImitator.getLatestMessage();
        Assert.assertTrue(latestMessage instanceof EntityViewUpdateMsg);
        EntityViewUpdateMsg entityViewUpdateMsg = (EntityViewUpdateMsg) latestMessage;
        Assert.assertEquals(UpdateMsgType.ENTITY_CREATED_RPC_MESSAGE, entityViewUpdateMsg.getMsgType());
        Assert.assertEquals(entityView.getType(), entityViewUpdateMsg.getType());
        Assert.assertEquals(entityView.getName(), entityViewUpdateMsg.getName());
        Assert.assertEquals(entityView.getUuidId().getMostSignificantBits(), entityViewUpdateMsg.getIdMSB());
        Assert.assertEquals(entityView.getUuidId().getLeastSignificantBits(), entityViewUpdateMsg.getIdLSB());
        Assert.assertEquals(device.getUuidId().getMostSignificantBits(), entityViewUpdateMsg.getEntityIdMSB());
        Assert.assertEquals(device.getUuidId().getLeastSignificantBits(), entityViewUpdateMsg.getEntityIdLSB());
        Assert.assertEquals(device.getId().getEntityType().name(), entityViewUpdateMsg.getEntityType().name());
        testAutoGeneratedCodeByProtobuf(entityViewUpdateMsg);
    }



}
