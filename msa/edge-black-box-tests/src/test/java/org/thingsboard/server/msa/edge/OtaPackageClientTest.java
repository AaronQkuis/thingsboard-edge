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
package org.thingsboard.server.msa.edge;

import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.junit.Test;
import org.thingsboard.server.common.data.DeviceProfileInfo;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.OtaPackage;
import org.thingsboard.server.common.data.OtaPackageInfo;
import org.thingsboard.server.common.data.id.DeviceProfileId;
import org.thingsboard.server.common.data.id.IdBased;
import org.thingsboard.server.common.data.ota.ChecksumAlgorithm;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.msa.AbstractContainerTest;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.thingsboard.server.common.data.ota.OtaPackageType.FIRMWARE;

@Slf4j
public class OtaPackageClientTest extends AbstractContainerTest {

    @Test
    public void testOtaPackages() throws Exception {
        DeviceProfileInfo defaultDeviceProfileInfo = cloudRestClient.getDefaultDeviceProfileInfo();
        OtaPackageInfo firmware = new OtaPackageInfo();
        firmware.setDeviceProfileId(new DeviceProfileId(defaultDeviceProfileInfo.getId().getId()));
        firmware.setType(FIRMWARE);
        firmware.setTitle("My firmware #2");
        firmware.setVersion("v2.0");
        firmware.setTag("My firmware #2 v2.0");
        firmware.setHasData(false);
        OtaPackageInfo savedOtaPackageInfo = cloudRestClient.saveOtaPackageInfo(firmware, false);

        cloudRestClient.saveOtaPackageData(savedOtaPackageInfo.getId(), null, ChecksumAlgorithm.SHA256, "firmware.bin", new byte[]{1, 3, 5});

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() ->  {
                    PageData<OtaPackageInfo> otaPackages = edgeRestClient.getOtaPackages(new PageLink(100));
                    if (otaPackages.getData().size() < 1) {
                        return false;
                    }
                    OtaPackage otaPackageById = edgeRestClient.getOtaPackageById(otaPackages.getData().get(0).getId());
                    return otaPackageById.isHasData();
                });

        PageData<OtaPackageInfo> pageData = edgeRestClient.getOtaPackages(new PageLink(100));
        assertEntitiesByIdsAndType(pageData.getData().stream().map(IdBased::getId).collect(Collectors.toList()), EntityType.OTA_PACKAGE);

        cloudRestClient.deleteOtaPackage(savedOtaPackageInfo.getId());

        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS).
                until(() ->  edgeRestClient.getOtaPackages(new PageLink(100)).getTotalElements() == 0);
    }


}

